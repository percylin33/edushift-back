package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.GenerateRubricRequest;
import com.edushift.modules.ai.dto.GenerateRubricResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.RubricGeneratorPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the BE-8.2 "generate rubric" AI flow.
 *
 * <p>Mirrors {@link SessionGeneratorService} (BE-8.1). Duplication of
 * the {@code PENDING → PROCESSING → LLM → parse → COMPLETED/FAILED}
 * pipeline is tracked as {@code DEBT-8-REFAC-1} (extract a
 * {@code BaseAiService} helper in Sprint 9).</p>
 *
 * <h3>Fork (ADR-8.3)</h3>
 * <p>If {@code request.seedRubricId} is set, the service loads the seed
 * rubric (multi-tenant safe via the {@code @TenantId} filter — 404
 * if not in caller's tenant) and feeds its {@code name} + a
 * compact summary of its criteria into the prompt. The output
 * {@code GenerateRubricResponse} is returned as-is; the FE is
 * responsible for the actual {@code POST /v1/academic/rubrics} call
 * to persist a fork.</p>
 */
@Service
public class RubricGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RubricGeneratorService.class);

    private final LlmClient llmClient;
    private final RubricGeneratorPromptBuilder promptBuilder;
    private final AiQuotaService quotaService;
    private final AiGenerationRepository generationRepo;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;
    private final RubricRepository rubricRepository;

    public RubricGeneratorService(LlmClient llmClient,
                                  RubricGeneratorPromptBuilder promptBuilder,
                                  AiQuotaService quotaService,
                                  AiGenerationRepository generationRepo,
                                  CurrentUserProvider currentUser,
                                  ObjectMapper objectMapper,
                                  RubricRepository rubricRepository) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.quotaService = quotaService;
        this.generationRepo = generationRepo;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.rubricRepository = rubricRepository;
    }

    /**
     * Generates a rubric draft synchronously. Throws
     * {@code AiDisabledException} / {@code AiQuotaExceededException} /
     * {@code ResourceNotFoundException} (404 for unknown seed) /
     * {@code AiParseException} / {@code LlmException}.
     */
    @Transactional
    public RubricGeneratorResult generateRubric(GenerateRubricRequest request) {
        // 1. Quota gate.
        TenantAiSettings settings = quotaService.verifyCanCall();
        UUID tenantId = TenantContext.currentRequired();
        UUID userId = currentUser.currentUserId().orElse(null);

        // 2. Resolve seed rubric (ADR-8.3) if provided.
        String seedName = null;
        String seedSummary = null;
        if (request.seedRubricId() != null) {
            Rubric seed = rubricRepository.findByPublicUuid(request.seedRubricId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Rubric", request.seedRubricId().toString()));
            seedName = seed.getName();
            seedSummary = summariseSeedCriteria(seed);
            log.debug("Rubric-generation forking from seed={} ({} criteria, levelCount={})",
                    seed.getPublicUuid(), seed.getCriteria().size(), request.effectiveLevelCount());
        }

        // 3. Build prompt.
        LlmRequest llmRequest = promptBuilder.build(request, seedName, seedSummary);

        // 4. Persist PENDING audit row.
        AiGeneration gen = newGeneration(tenantId, userId, llmRequest, request.seedRubricId());
        gen.setStatus(AiGeneration.Status.PENDING);
        generationRepo.saveAndFlush(gen);

        return runGeneration(gen, llmRequest, request);
    }

    @Transactional
    public RubricGeneratorResult runGeneration(AiGeneration gen,
                                               LlmRequest llmRequest,
                                               GenerateRubricRequest request) {
        gen.setStatus(AiGeneration.Status.PROCESSING);
        generationRepo.saveAndFlush(gen);

        long start = System.nanoTime();
        LlmResponse llmResponse;
        try {
            llmResponse = llmClient.complete(llmRequest);
        } catch (LlmException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Rubric-generation failed (code={}, latency={}ms): {}",
                    e.getCode(), latency, e.getMessage());
            markFailed(gen, e.getCode(), e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        } catch (RuntimeException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.error("Rubric-generation threw unexpected error (latency={}ms)", latency, e);
            markFailed(gen, "AI_UNEXPECTED", e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        }

        GenerateRubricResponse parsed;
        try {
            parsed = parseAndValidate(llmResponse.text());
        } catch (AiParseException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Rubric-generation returned unparseable JSON (latency={}ms): {}",
                    latency, e.getMessage());
            markFailedWithResponse(gen, llmResponse, "AI_PARSE_ERROR", e.getMessage());
            quotaService.incrementCounters(false,
                    longOrZero(llmResponse.tokensIn()),
                    longOrZero(llmResponse.tokensOut()));
            throw e;
        }

        markCompleted(gen, llmResponse, parsed);
        quotaService.incrementCounters(true,
                longOrZero(llmResponse.tokensIn()),
                longOrZero(llmResponse.tokensOut()));

        return new RubricGeneratorResult(
                parsed,
                llmResponse.model(),
                llmClient.providerId(),
                RubricGeneratorPromptBuilder.PROMPT_VERSION,
                gen.getPublicUuid());
    }

    // ---------------------------------------------------------------------
    // Internals.
    // ---------------------------------------------------------------------

    /**
     * Builds a compact one-line-per-criterion summary of the seed
     * rubric for the LLM prompt. We avoid dumping the full criteria
     * array (which includes per-level descriptors and would blow up
     * the prompt).
     */
    private String summariseSeedCriteria(Rubric seed) {
        List<Map<String, Object>> criteria = seed.getCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return "(sin criterios registrados)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < criteria.size(); i++) {
            Map<String, Object> c = criteria.get(i);
            Object name = c.get("name");
            Object weight = c.get("weight");
            sb.append("  ").append(i + 1).append(". ")
              .append(name == null ? "(sin nombre)" : name);
            if (weight != null) {
                sb.append(" (peso ").append(weight).append(")");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private AiGeneration newGeneration(UUID tenantId, UUID userId, LlmRequest llmRequest, UUID seedRubricId) {
        AiGeneration g = new AiGeneration();
        g.setPublicUuid(UUID.randomUUID());
        g.setTenantId(tenantId);
        g.setRequestUserId(userId);
        g.setFeature(AiGeneration.Feature.RUBRIC_SUGGEST);
        g.setPromptText(llmRequest.userPrompt());
        log.debug("Rubric-generation start: tenant={} user={} seed={} promptVersion={}",
                tenantId, userId, seedRubricId, RubricGeneratorPromptBuilder.PROMPT_VERSION);
        return g;
    }

    private void markFailed(AiGeneration gen, String code, String message) {
        gen.setStatus(AiGeneration.Status.FAILED);
        gen.setErrorCode(code);
        gen.setErrorMessage(truncate(message, 4000));
        gen.setLatencyMs(0);
        generationRepo.save(gen);
    }

    private void markFailedWithResponse(AiGeneration gen, LlmResponse llmResponse,
                                        String code, String message) {
        gen.setStatus(AiGeneration.Status.FAILED);
        gen.setErrorCode(code);
        gen.setErrorMessage(truncate(message, 4000));
        gen.setResponseText(truncate(llmResponse.text(), 60_000));
        gen.setModelUsed(llmResponse.model());
        gen.setPromptTokens(llmResponse.tokensIn());
        gen.setResponseTokens(llmResponse.tokensOut());
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, llmResponse.latencyMs()));
        generationRepo.save(gen);
    }

    private void markCompleted(AiGeneration gen, LlmResponse llmResponse,
                               GenerateRubricResponse parsed) {
        gen.setStatus(AiGeneration.Status.COMPLETED);
        gen.setResponseText(truncate(llmResponse.text(), 60_000));
        Map<String, Object> parsedMap = objectMapper.convertValue(parsed, new TypeReference<>() {});
        gen.setResponseParsed(parsedMap);
        gen.setModelUsed(llmResponse.model());
        gen.setPromptTokens(llmResponse.tokensIn());
        gen.setResponseTokens(llmResponse.tokensOut());
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, llmResponse.latencyMs()));
        generationRepo.save(gen);
    }

    private GenerateRubricResponse parseAndValidate(String text) {
        GenerateRubricResponse parsed;
        try {
            parsed = objectMapper.readValue(text, GenerateRubricResponse.class);
        } catch (JsonProcessingException e) {
            throw new AiParseException(
                    "LLM response is not valid JSON: " + firstLine(e.getMessage()), e);
        }
        parsed.validate(text);
        return parsed;
    }

    private static long longOrZero(Integer i) {
        return i == null ? 0L : i.longValue();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /**
     * Result envelope. Includes the parsed response plus enough audit
     * metadata for the FE to show "Generated by {model} · UUID {generationUuid}".
     */
    public record RubricGeneratorResult(
            GenerateRubricResponse rubric,
            String model,
            String provider,
            String promptVersion,
            UUID generationUuid
    ) {
    }
}
