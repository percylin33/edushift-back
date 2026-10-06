package com.edushift.modules.ai.service;

import com.edushift.modules.ai.dto.GenerateApplicationSheetRequest;
import com.edushift.modules.ai.dto.GenerateApplicationSheetResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.ApplicationSheetPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.modules.sessions.learning.sheet.ApplicationSheetParser;
import com.edushift.modules.sessions.learning.sheet.ApplicationSheetParser.ParsedApplicationSheet;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * One LLM call, one printable sheet, one grade.
 */
@Service
public class ApplicationSheetGeneratorService {

	private static final Logger log = LoggerFactory.getLogger(ApplicationSheetGeneratorService.class);

	private final LlmClient llmClient;
	private final ApplicationSheetPromptBuilder promptBuilder;
	private final AiQuotaService quotaService;
	private final AiGenerationRepository generationRepo;
	private final CurrentUserProvider currentUser;
	private final ObjectMapper objectMapper;

	public ApplicationSheetGeneratorService(LlmClient llmClient,
			ApplicationSheetPromptBuilder promptBuilder,
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

	public GenerateApplicationSheetResponse generate(GenerateApplicationSheetRequest request) {
		quotaService.verifyCanCall();
		UUID tenantId = TenantContext.currentRequired();
		UUID userId = currentUser.currentUserId().orElse(null);
		LlmRequest llmRequest = promptBuilder.build(request);

		AiGeneration gen = new AiGeneration();
		gen.setPublicUuid(UUID.randomUUID());
		gen.setTenantId(tenantId);
		gen.setRequestUserId(userId);
		gen.setFeature(AiGeneration.Feature.APPLICATION_SHEET_SUGGEST);
		gen.setPromptText(truncate(llmRequest.userPrompt(), 60_000));
		gen.setStatus(AiGeneration.Status.PROCESSING);
		generationRepo.saveAndFlush(gen);

		long start = System.nanoTime();
		LlmResponse llmResponse;
		try {
			// #region agent log
			agentLog("B", "ApplicationSheetGeneratorService.generate", "before-llm",
					"{\"txActive\":" + TransactionSynchronizationManager.isActualTransactionActive()
							+ ",\"grade\":\"" + jsonEscape(request.grade()) + "\"}");
			// #endregion
			llmResponse = llmClient.complete(llmRequest);
		}
		catch (LlmException ex) {
			markFailed(gen, ex.getCode(), ex.getMessage(), start);
			quotaService.incrementCounters(false, 0L, 0L);
			throw ex;
		}
		catch (RuntimeException ex) {
			markFailed(gen, "AI_UNEXPECTED", ex.getMessage(), start);
			quotaService.incrementCounters(false, 0L, 0L);
			throw ex;
		}

		ParsedApplicationSheet parsed;
		try {
			parsed = ApplicationSheetParser.parse(
					objectMapper, llmResponse.text(), request.kind(), request.grade().trim());
		}
		catch (AiParseException ex) {
			gen.setStatus(AiGeneration.Status.FAILED);
			gen.setErrorCode("AI_PARSE_ERROR");
			gen.setErrorMessage(truncate(ex.getMessage(), 4000));
			gen.setResponseText(truncate(llmResponse.text(), 60_000));
			gen.setModelUsed(llmResponse.model());
			gen.setLatencyMs(latency(start));
			generationRepo.save(gen);
			quotaService.incrementCounters(false,
					longOrZero(llmResponse.tokensIn()),
					longOrZero(llmResponse.tokensOut()));
			throw ex;
		}

		gen.setStatus(AiGeneration.Status.COMPLETED);
		gen.setResponseText(truncate(llmResponse.text(), 60_000));
		gen.setResponseParsed(objectMapper.convertValue(parsed,
				new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
				}));
		gen.setModelUsed(llmResponse.model());
		gen.setPromptTokens(llmResponse.tokensIn());
		gen.setResponseTokens(llmResponse.tokensOut());
		gen.setLatencyMs(latency(start));
		generationRepo.save(gen);
		quotaService.incrementCounters(true,
				longOrZero(llmResponse.tokensIn()),
				longOrZero(llmResponse.tokensOut()));

		log.info("[ai/application-sheet] grade={} kind={} generation={}",
				request.grade(), request.kind(), gen.getPublicUuid());

		return new GenerateApplicationSheetResponse(
				request.kind(),
				request.grade().trim(),
				parsed.title(),
				parsed.content(),
				llmResponse.model(),
				llmClient.providerId(),
				ApplicationSheetPromptBuilder.PROMPT_VERSION,
				gen.getPublicUuid());
	}

	// #region agent log
	private static void agentLog(String hypothesisId, String location, String message, String dataJson) {
		try {
			String line = "{\"sessionId\":\"325667\",\"runId\":\"post-fix\",\"hypothesisId\":\""
					+ hypothesisId + "\",\"location\":\"" + location + "\",\"message\":\""
					+ message + "\",\"data\":" + dataJson + ",\"timestamp\":"
					+ System.currentTimeMillis() + "}\n";
			java.nio.file.Files.writeString(
					java.nio.file.Path.of("C:\\Users\\USUARIO\\Desktop\\personales\\proyectos\\new - proyecto multi school\\edushift\\debug-325667.log"),
					line,
					java.nio.file.StandardOpenOption.CREATE,
					java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception ignored) {
			// debug sink must not affect generation
		}
	}

	private static String jsonEscape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
	// #endregion

	private void markFailed(AiGeneration gen, String code, String message, long start) {
		gen.setStatus(AiGeneration.Status.FAILED);
		gen.setErrorCode(code);
		gen.setErrorMessage(truncate(message, 4000));
		gen.setLatencyMs(latency(start));
		generationRepo.save(gen);
	}

	private static int latency(long startNanos) {
		long ms = (System.nanoTime() - startNanos) / 1_000_000L;
		return (int) Math.min(Integer.MAX_VALUE, ms);
	}

	private static long longOrZero(Integer value) {
		return value == null ? 0L : value.longValue();
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
