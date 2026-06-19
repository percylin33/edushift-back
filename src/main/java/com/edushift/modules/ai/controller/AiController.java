package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.dto.AsyncGenerationAcceptedResponse;
import com.edushift.modules.ai.dto.GenerationStatusResponse;
import com.edushift.modules.ai.dto.QuestionSuggestion;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.exception.AiGenerationNotFoundException;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.modules.ai.service.AsyncLmsAiOrchestrator;
import com.edushift.modules.ai.service.LmsAiService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the AI module (BE-7c.1 + BE-7c.2).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>AI endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/lms/ai/quiz-questions</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@link SuggestQuizQuestionsResponse} (sync, 200) or
 *           {@link AsyncGenerationAcceptedResponse} (async, 202)</td></tr>
 *   <tr><td>GET</td>
 *       <td>/lms/ai/generations/{publicUuid}</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@link GenerationStatusResponse} (200)</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/lms/ai/generations/{publicUuid}</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>204 No Content</td></tr>
 * </table>
 *
 * <h3>Sync vs async (BE-7c.2)</h3>
 * The {@code POST /quiz-questions} endpoint supports a {@code ?async=true}
 * query parameter. When set, the request returns 202 with a
 * {@link AsyncGenerationAcceptedResponse} immediately, and the actual
 * LLM call happens on the {@code aiJobExecutor} thread pool. Clients
 * poll the {@code GET /generations/{uuid}} endpoint to retrieve the
 * result. When {@code ?async=true} is <em>not</em> set, the request
 * blocks the caller thread until the LLM responds (BE-7c.1 behavior,
 * kept for backward compat with FE-7c.1).
 *
 * <h3>Error mapping</h3>
 * Module exceptions ({@code AiDisabledException},
 * {@code AiQuotaExceededException}, {@code AiParseException},
 * {@code LlmException}, {@code AiGenerationNotFoundException}) are
 * translated to {@code ApiError} responses by
 * {@code AiExceptionHandler} (in the same package).
 */
@RestController
@RequestMapping("/lms/ai")
@RequiredArgsConstructor
@Tag(name = "AI assistant", description = "AI-assisted features for the LMS (Sprint 7c)")
public class AiController {

    private final LmsAiService lmsAiService;
    private final AsyncLmsAiOrchestrator asyncOrchestrator;
    private final AiGenerationRepository generationRepo;
    private final ObjectMapper objectMapper;

    @PostMapping("/quiz-questions")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Suggest quiz questions on a topic",
               description = """
                       Calls the configured LLM (OpenRouter, MiniMax, or Mock)
                       to suggest N questions (MC/TF/ShortAnswer) for the given
                       topic. Subject to the tenant's AI quota and master
                       switch. Synchronous by default (latency 1-3s); pass
                       `?async=true` to receive 202 + a generation UUID, then
                       poll GET /lms/ai/generations/{uuid} for the result.
                       """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Suggestions generated (sync mode)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Generation accepted; poll the returned URL (async mode)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error (topic too short, count out of range)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AI_DISABLED — tenant has AI off, or user lacks LMS_AI_GENERATE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI_QUOTA_EXCEEDED — daily or monthly quota exhausted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI_PARSE_ERROR / LLM_TIMEOUT / LLM_UPSTREAM — provider error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI_BUSY — aiJobExecutor queue is full, retry shortly")
    })
    public ResponseEntity<?> suggestQuizQuestions(
            @Valid @RequestBody SuggestQuizQuestionsRequest request,
            @Parameter(description = "If true, return 202 immediately and run the LLM call on a worker thread. Default false.",
                    example = "false")
            @RequestParam(name = "async", defaultValue = "false") boolean async
    ) {
        if (async) {
            UUID uuid = asyncOrchestrator.startGeneration(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.ok(AsyncGenerationAcceptedResponse.forUuid(uuid)));
        }
        SuggestQuizQuestionsResponse body = lmsAiService.suggestQuizQuestions(request);
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/generations/{publicUuid}")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Poll the status of an async generation",
               description = """
                       Returns the current lifecycle status of the generation
                       (PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED).
                       On COMPLETED, the response body also includes the
                       generated questions. On FAILED, it includes the error
                       code and message. Cross-tenant lookups return 404
                       (the Hibernate @TenantId filter guarantees this).
                       """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current status (see GenerationStatusResponse)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User lacks LMS_AI_GENERATE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Generation not found in the caller's tenant")
    })
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<GenerationStatusResponse>> getGeneration(
            @PathVariable("publicUuid") UUID publicUuid
    ) {
        AiGeneration gen = generationRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new AiGenerationNotFoundException(publicUuid));

        GenerationStatusResponse body = switch (gen.getStatus()) {
            case COMPLETED -> {
                List<QuestionSuggestion> suggestions = parsePersistedOrNull(gen);
                if (suggestions == null) {
                    yield GenerationStatusResponse.fromRow(gen);
                }
                yield GenerationStatusResponse.completed(
                        gen, suggestions, gen.getModelUsed(),
                        /* provider */ null, /* promptVersion */ null);
            }
            case PENDING, PROCESSING, CANCELLED, FAILED ->
                    GenerationStatusResponse.fromRow(gen);
        };
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @DeleteMapping("/generations/{publicUuid}")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Cancel a PENDING or PROCESSING generation",
               description = """
                       Marks the generation CANCELLED if it is PENDING or
                       PROCESSING. Cooperative: if the worker has already
                       started, the cancellation only prevents a SUCCESSFUL
                       audit row from being persisted; the LLM call still
                       runs to completion. For PENDING rows (queued but
                       not yet picked up), the worker thread checks the
                       status and bails before the LLM call. Idempotent
                       on COMPLETED / FAILED / CANCELLED (returns 204
                       without changes).
                       """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Cancelled (or was already terminal)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User lacks LMS_AI_GENERATE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Generation not found in the caller's tenant")
    })
    @Transactional
    public ResponseEntity<Void> cancelGeneration(@PathVariable("publicUuid") UUID publicUuid) {
        AiGeneration gen = generationRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new AiGenerationNotFoundException(publicUuid));
        if (gen.getStatus() == AiGeneration.Status.PENDING
                || gen.getStatus() == AiGeneration.Status.PROCESSING) {
            gen.setStatus(AiGeneration.Status.CANCELLED);
            generationRepo.save(gen);
        }
        // Idempotent: COMPLETED / FAILED / CANCELLED all return 204.
        return ResponseEntity.noContent().build();
    }

    /**
     * Parses the persisted {@code response_parsed} JSONB into a
     * {@link List<QuestionSuggestion>}. Returns {@code null} on any
     * parse failure (instead of throwing) so the polling client still
     * gets a 200 with the row's metadata — better than 500 every poll
     * for a corrupted historical row.
     */
    private List<QuestionSuggestion> parsePersistedOrNull(AiGeneration gen) {
        if (gen.getResponseParsed() == null) {
            return null;
        }
        Object questionsObj = gen.getResponseParsed().get("questions");
        if (!(questionsObj instanceof List<?> rawList)) {
            return null;
        }
        try {
            return rawList.stream()
                    .map(item -> objectMapper.convertValue(item, QuestionSuggestion.class))
                    .toList();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
