package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.QuestionSuggestion;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.QuizQuestionPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.constants.LoggerNames;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the "suggest quiz questions" AI flow (BE-7c.1).
 *
 * <h3>Happy path</h3>
 * <ol>
 *   <li>{@link AiQuotaService#verifyCanCall()} — master switch + quota check.
 *       Throws {@code AiDisabledException} (403) or
 *       {@code AiQuotaExceededException} (429) before we waste an LLM call.</li>
 *   <li>Build the {@link LlmRequest} via {@link QuizQuestionPromptBuilder}.</li>
 *   <li>Call the {@link LlmClient}. Persist an {@link AiGeneration}
 *       audit row in {@code FAILED} state BEFORE the call (so we have
 *       a row even if the JVM dies mid-call).</li>
 *   <li>Update the audit row to {@code COMPLETED} (with the parsed JSON)
 *       or {@code FAILED} (with the error code/message). Always inside
 *       the same transaction as the counter bump.</li>
 *   <li>{@link AiQuotaService#incrementCounters} — atomic UPSERT of the
 *       daily counters.</li>
 * </ol>
 *
 * <h3>Failure modes</h3>
 * <ul>
 *   <li>{@link LlmException#TIMEOUT} / {@link LlmException#NETWORK} /
 *       {@link LlmException#UPSTREAM}: the {@link LlmClient} already
 *       retried; we surface the error to the client (502) and persist
 *       a {@code FAILED} audit row with the code.</li>
 *   <li>LLM returns invalid JSON or wrong shape → {@link AiParseException}
 *       (502) and a {@code FAILED} audit row with {@code AI_PARSE_ERROR}.</li>
 *   <li>Quota disabled or exhausted → never reach the LLM. No audit row
 *       (the request was rejected, not "attempted").</li>
 * </ul>
 *
 * <h3>Async path (BE-7c.2)</h3>
 * The full lifecycle lives in {@link #runGeneration(UUID, SuggestQuizQuestionsRequest)},
 * which is called by:
 * <ul>
 *   <li>{@link #suggestQuizQuestions(SuggestQuizQuestionsRequest)} — synchronous,
 *       returns the response to the caller immediately.</li>
 *   <li>{@link AsyncLmsAiOrchestrator#startGeneration(SuggestQuizQuestionsRequest)} —
 *       submits to the {@code aiJobExecutor} thread pool and returns a
 *       {@code publicUuid} right away. The async path does its own
 *       quota check (to fail fast on the caller thread) and reuses
 *       {@code runGeneration()} for the heavy lifting.</li>
 * </ul>
 * Both share the same {@link AiGeneration} audit row, so the lifecycle
 * is observable from one place (the {@code ai_generations} table).
 */
@Service
public class LmsAiService {

    private static final Logger log = LoggerFactory.getLogger(LoggerNames.AI);

    private final LlmClient llmClient;
    private final QuizQuestionPromptBuilder promptBuilder;
    private final AiQuotaService quotaService;
    private final AiGenerationRepository generationRepo;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;

    public LmsAiService(LlmClient llmClient,
                        QuizQuestionPromptBuilder promptBuilder,
                        AiQuotaService quotaService,
                        AiGenerationRepository generationRepo,
                        CurrentUserProvider currentUser,
                        ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.quotaService = quotaService;
        this.generationRepo = generationRepo;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    /**
     * Suggests quiz questions synchronously. Throws
     * {@code AiDisabledException} / {@code AiQuotaExceededException} /
     * {@code AiParseException} / {@code LlmException} as documented on
     * the class.
     */
    @Transactional
    public SuggestQuizQuestionsResponse suggestQuizQuestions(SuggestQuizQuestionsRequest request) {
        // 1. Quota gate. If this throws, no audit row is created (the
        //    request never reached the LLM).
        TenantAiSettings settings = quotaService.verifyCanCall();
        UUID tenantId = TenantContext.currentRequired();
        UUID userId = currentUser.currentUserId().orElse(null);
        String model = settings.getDefaultModel() != null
                ? settings.getDefaultModel()
                : promptBuilder.build(null, 1, null, null).model(); // cheap: just reads default
        // Build the prompt (this also normalises topic / count).
        LlmRequest llmRequest = promptBuilder.build(
                request.topic(), request.count(), request.questionType(), model);

        // 2. Persist a PENDING audit row BEFORE the call. If the JVM
        //    dies mid-call, we still have a record of the attempt.
        AiGeneration gen = newGeneration(tenantId, userId, llmRequest);
        gen.setStatus(AiGeneration.Status.PENDING);
        generationRepo.saveAndFlush(gen);

        return runGeneration(gen, llmRequest, request);
    }

    /**
     * Core generation pipeline, shared by sync and async paths.
     * <p>
     * The caller hands in a <em>managed</em> {@link AiGeneration} row
     * (already in {@code PENDING} state and attached to the current
     * persistence context) plus the original {@link LlmRequest} and
     * user-facing {@link SuggestQuizQuestionsRequest}. We do not
     * re-load the row by publicUuid — the sync caller has it locally,
     * and the async caller (in BE-7c.2) does its own load right before
     * this method runs, with a defensive {@code CANCELLED} check.
     * <p>
     * Marks the row {@code PROCESSING}, calls the LLM, parses the
     * response, updates the row to {@code COMPLETED} / {@code FAILED},
     * and bumps the quota counters — all in the same transaction the
     * caller opened. Returns the final response on success; throws
     * the documented exceptions on failure (after persisting FAILED).
     */
    @Transactional
    public SuggestQuizQuestionsResponse runGeneration(AiGeneration gen,
                                                      LlmRequest llmRequest,
                                                      SuggestQuizQuestionsRequest request) {
        // Mark PROCESSING now. Even if the JVM dies between LLM call and
        // result, a sweep job can find PROCESSING rows older than N
        // minutes and mark them FAILED. (That job is out of scope for
        // BE-7c.2; tracked as DEBT-BE-7C-4.)
        gen.setStatus(AiGeneration.Status.PROCESSING);
        generationRepo.saveAndFlush(gen);

        // 3. Call the LLM.
        long start = System.nanoTime();
        LlmResponse llmResponse;
        try {
            llmResponse = llmClient.complete(llmRequest);
        } catch (LlmException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("AI generation failed (code={}, latency={}ms): {}",
                    e.getCode(), latency, e.getMessage());
            markFailed(gen, e.getCode(), e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        } catch (RuntimeException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.error("AI generation threw unexpected error (latency={}ms)", latency, e);
            markFailed(gen, "AI_UNEXPECTED", e.getMessage());
            quotaService.incrementCounters(false, 0L, 0L);
            throw e;
        }

        // 4. Parse the LLM JSON.
        List<QuestionSuggestion> suggestions;
        try {
            suggestions = parseAndValidate(llmResponse.text(),
                    request.count(), request.questionType());
        } catch (AiParseException e) {
            long latency = (System.nanoTime() - start) / 1_000_000L;
            log.warn("AI generation returned unparseable JSON (latency={}ms): {}",
                    latency, e.getMessage());
            markFailedWithResponse(gen, llmResponse, "AI_PARSE_ERROR", e.getMessage());
            quotaService.incrementCounters(false,
                    longOrZero(llmResponse.tokensIn()),
                    longOrZero(llmResponse.tokensOut()));
            throw e;
        }

        // 5. Update audit row to COMPLETED.
        markCompleted(gen, llmResponse, suggestions);
        quotaService.incrementCounters(true,
                longOrZero(llmResponse.tokensIn()),
                longOrZero(llmResponse.tokensOut()));

        return new SuggestQuizQuestionsResponse(
                suggestions,
                llmResponse.model(),
                llmClient.providerId(),
                promptBuilder.promptVersion(),
                List.of(gen.getPublicUuid().toString())
        );
    }

    // ---------------------------------------------------------------------
    // Internals.
    // ---------------------------------------------------------------------

    private AiGeneration newGeneration(UUID tenantId, UUID userId, LlmRequest llmRequest) {
        AiGeneration g = new AiGeneration();
        g.setPublicUuid(UUID.randomUUID());
        g.setTenantId(tenantId);
        g.setRequestUserId(userId);
        g.setFeature(AiGeneration.Feature.QUIZ_QUESTION_SUGGEST);
        // We persist the user prompt (the system prompt is long + reconstructable
        // from the prompt version + topic). For full audit, the audit
        // dashboard can rebuild the system prompt from promptVersion.
        g.setPromptText(llmRequest.userPrompt());
        return g;
    }

    private void markFailed(AiGeneration gen, String code, String message) {
        gen.setStatus(AiGeneration.Status.FAILED);
        gen.setErrorCode(code);
        gen.setErrorMessage(truncate(message, 4000));
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, 0L));
        // responseText/responseParsed stay null.
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
                               List<QuestionSuggestion> suggestions) {
        gen.setStatus(AiGeneration.Status.COMPLETED);
        gen.setResponseText(truncate(llmResponse.text(), 60_000));
        // Persist the parsed JSON as a Map for the audit dashboard.
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("questions", suggestions.stream().map(LmsAiService::suggestionToMap).toList());
        gen.setResponseParsed(parsed);
        gen.setModelUsed(llmResponse.model());
        gen.setPromptTokens(llmResponse.tokensIn());
        gen.setResponseTokens(llmResponse.tokensOut());
        gen.setLatencyMs((int) Math.min(Integer.MAX_VALUE, llmResponse.latencyMs()));
        generationRepo.save(gen);
    }

    private static Map<String, Object> suggestionToMap(QuestionSuggestion s) {
        Map<String, Object> m = new HashMap<>();
        m.put("prompt", s.prompt());
        m.put("type", s.questionType());
        m.put("points", s.points());
        m.put("rationale", s.rationale());
        m.put("options", s.options().stream().map(o -> Map.<String, Object>of(
                "label", o.label(),
                "isCorrect", o.isCorrect(),
                "explanation", o.explanation() == null ? "" : o.explanation()
        )).toList());
        return m;
    }

    /**
     * Parses the LLM's text reply into a list of validated
     * {@link QuestionSuggestion}. Throws {@link AiParseException} on
     * any structural problem.
     */
    private List<QuestionSuggestion> parseAndValidate(String text, int maxCount, String filterType) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new AiParseException(
                    "LLM response is not valid JSON: " + firstLine(e.getMessage()), e);
        }
        JsonNode questionsNode = root.get("questions");
        if (questionsNode == null || !questionsNode.isArray()) {
            throw new AiParseException("LLM response missing the 'questions' array");
        }
        List<QuestionSuggestion> out = new ArrayList<>();
        for (int i = 0; i < questionsNode.size() && out.size() < maxCount; i++) {
            JsonNode q = questionsNode.get(i);
            QuestionSuggestion s = parseOne(q, i);
            if (filterType != null && !filterType.equals(s.questionType())) {
                continue;
            }
            out.add(s);
        }
        if (out.isEmpty()) {
            throw new AiParseException(
                    "LLM returned no valid questions (max=" + maxCount + ", filter=" + filterType + ")");
        }
        return out;
    }

    private QuestionSuggestion parseOne(JsonNode q, int idx) {
        String prompt = requiredText(q, "prompt", idx);
        String type = requiredText(q, "type", idx);
        if (!QuizQuestionPromptBuilder.allowedQuestionTypes().contains(type)) {
            throw new AiParseException(
                    "Question[" + idx + "] has invalid type '" + type + "'; allowed="
                            + QuizQuestionPromptBuilder.allowedQuestionTypes());
        }
        int points = q.has("points") ? q.get("points").asInt(5) : 5;
        if (points < 1 || points > 10) {
            points = Math.max(1, Math.min(10, points));
        }
        String rationale = q.has("rationale") && q.get("rationale").isTextual()
                ? q.get("rationale").asText() : null;

        List<QuestionSuggestion.OptionSuggestion> options = new ArrayList<>();
        JsonNode optionsNode = q.get("options");
        if (optionsNode != null && optionsNode.isArray()) {
            for (int j = 0; j < optionsNode.size(); j++) {
                JsonNode o = optionsNode.get(j);
                String label = requiredText(o, "label", idx, j);
                boolean isCorrect = o.has("isCorrect") && o.get("isCorrect").asBoolean(false);
                String explanation = o.has("explanation") && o.get("explanation").isTextual()
                        ? o.get("explanation").asText() : null;
                options.add(new QuestionSuggestion.OptionSuggestion(label, isCorrect, explanation));
            }
        }
        // Per-type option validation.
        if ("MC".equals(type)) {
            if (options.size() < 2 || options.size() > 4) {
                throw new AiParseException(
                        "MC question[" + idx + "] must have 2..4 options, got " + options.size());
            }
            long correct = options.stream().filter(QuestionSuggestion.OptionSuggestion::isCorrect).count();
            if (correct != 1) {
                throw new AiParseException(
                        "MC question[" + idx + "] must have exactly 1 isCorrect=true option, got " + correct);
            }
        } else if ("TF".equals(type)) {
            if (options.size() != 2) {
                throw new AiParseException(
                        "TF question[" + idx + "] must have exactly 2 options, got " + options.size());
            }
            long correct = options.stream().filter(QuestionSuggestion.OptionSuggestion::isCorrect).count();
            if (correct != 1) {
                throw new AiParseException(
                        "TF question[" + idx + "] must have exactly 1 isCorrect=true option, got " + correct);
            }
        } else if ("SHORT_ANSWER".equals(type)) {
            if (!options.isEmpty()) {
                // We don't error; we just drop the options silently (the FE will see an empty list).
                options = List.of();
            }
        }
        return new QuestionSuggestion(
                UUID.randomUUID().toString(),
                prompt,
                type,
                points,
                options,
                rationale
        );
    }

    private static String requiredText(JsonNode obj, String field, int idx) {
        return requiredText(obj, field, idx, -1);
    }

    private static String requiredText(JsonNode obj, String field, int idx, int subIdx) {
        JsonNode v = obj.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            String where = subIdx < 0
                    ? "Question[" + idx + "]." + field
                    : "Question[" + idx + "].options[" + subIdx + "]." + field;
            throw new AiParseException(where + " is required and must be a non-blank string");
        }
        return v.asText();
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

    /** Marker record kept here so we can evolve the parse shape in one place. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireQuestionsRoot(List<WireQuestion> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireQuestion(String prompt, String type, Integer points,
                                List<WireOption> options, String rationale) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WireOption(String label, Boolean isCorrect, String explanation) {}
}
