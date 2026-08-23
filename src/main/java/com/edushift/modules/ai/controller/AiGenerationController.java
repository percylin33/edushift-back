package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.dto.GenerateRubricRequest;
import com.edushift.modules.ai.dto.GenerateSessionRequest;
import com.edushift.modules.ai.service.RubricGeneratorService;
import com.edushift.modules.ai.service.RubricGeneratorService.RubricGeneratorResult;
import com.edushift.modules.ai.service.SessionGeneratorService;
import com.edushift.modules.ai.service.SessionGeneratorService.SessionGeneratorResult;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.LmsAuthorities;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Session/rubric AI generators (BE-8.1 / BE-8.2).
 *
 * <p>Mounted at {@code /ai/*} so the wire path is
 * {@code /api/v1/ai/generate-session} (matches the FE
 * {@code API.AI.GENERATE_* } constants). Do <em>not</em> prefix
 * {@code /v1} here — {@link com.edushift.config.WebConfiguration}
 * adds it globally.</p>
 *
 * <p>Legacy alias {@code /lms/ai/v1/ai/*} is kept for old Postman
 * collections; prefer {@code /ai/*} for new clients.</p>
 */
@RestController
@RequestMapping({"/ai", "/lms/ai/v1/ai"})
@Tag(name = "AI generation", description = "Session and rubric AI generators (Sprint 8)")
public class AiGenerationController {

    private final SessionGeneratorService sessionGeneratorService;
    private final RubricGeneratorService rubricGeneratorService;
    private final ObjectMapper objectMapper;
    private final Executor aiJobExecutor;

    public AiGenerationController(
            SessionGeneratorService sessionGeneratorService,
            RubricGeneratorService rubricGeneratorService,
            ObjectMapper objectMapper,
            @Qualifier("aiJobExecutor") Executor aiJobExecutor) {
        this.sessionGeneratorService = sessionGeneratorService;
        this.rubricGeneratorService = rubricGeneratorService;
        this.objectMapper = objectMapper;
        this.aiJobExecutor = aiJobExecutor;
    }

    @PostMapping("/generate-session")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Generate a learning session outline with AI")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session outline generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AI disabled or missing LMS_AI_GENERATE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "LLM or parse failure")
    })
    public ResponseEntity<ApiResponse<SessionGeneratorResult>> generateSession(
            @Valid @RequestBody GenerateSessionRequest request) {
        SessionGeneratorResult result = sessionGeneratorService.generateSession(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(value = "/generate-session/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Generate a learning session outline with AI (SSE streaming)")
    public SseEmitter streamGenerateSession(@Valid @RequestBody GenerateSessionRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        java.util.concurrent.atomic.AtomicReference<Thread> workerThread =
                new java.util.concurrent.atomic.AtomicReference<>();
        // Run on aiJobExecutor so TenantContext + MDC propagate from the
        // request thread (ContextPropagatingTaskDecorator). A raw Thread
        // would lose the tenant binding and the @Transactional lookup in
        // streamSession() would fail with TENANT_REQUIRED.
        aiJobExecutor.execute(() -> {
            workerThread.set(Thread.currentThread());
            try {
                SessionGeneratorResult result = sessionGeneratorService.streamSession(
                        request,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("token").data(chunk));
                                return true;
                            } catch (java.io.IOException e) {
                                return false;
                            }
                        });
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(result)));
                emitter.complete();
            } catch (com.edushift.modules.ai.exception.AiModuleException ex) {
                sendErrorAndComplete(emitter, ex.getMessage());
            } catch (Exception ex) {
                sendErrorAndComplete(emitter,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        });
        emitter.onCompletion(() -> interruptWorker(workerThread.get()));
        emitter.onTimeout(() -> { interruptWorker(workerThread.get()); emitter.complete(); });
        emitter.onError(ex -> interruptWorker(workerThread.get()));
        return emitter;
    }

    @PostMapping("/generate-rubric")
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Generate a rubric draft with AI")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Rubric draft generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AI disabled or missing LMS_AI_GENERATE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "LLM or parse failure")
    })
    public ResponseEntity<ApiResponse<RubricGeneratorResult>> generateRubric(
            @Valid @RequestBody GenerateRubricRequest request) {
        RubricGeneratorResult result = rubricGeneratorService.generateRubric(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(value = "/generate-rubric/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('" + LmsAuthorities.LMS_AI_GENERATE + "')")
    @Operation(summary = "Generate a rubric draft with AI (SSE streaming)")
    public SseEmitter streamGenerateRubric(@Valid @RequestBody GenerateRubricRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        java.util.concurrent.atomic.AtomicReference<Thread> workerThread =
                new java.util.concurrent.atomic.AtomicReference<>();
        aiJobExecutor.execute(() -> {
            workerThread.set(Thread.currentThread());
            try {
                RubricGeneratorResult result = rubricGeneratorService.streamRubric(
                        request,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().name("token").data(chunk));
                                return true;
                            } catch (java.io.IOException e) {
                                return false;
                            }
                        });
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(result)));
                emitter.complete();
            } catch (com.edushift.modules.ai.exception.AiModuleException ex) {
                sendErrorAndComplete(emitter, ex.getMessage());
            } catch (Exception ex) {
                sendErrorAndComplete(emitter,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        });
        emitter.onCompletion(() -> interruptWorker(workerThread.get()));
        emitter.onTimeout(() -> { interruptWorker(workerThread.get()); emitter.complete(); });
        emitter.onError(ex -> interruptWorker(workerThread.get()));
        return emitter;
    }

    private static void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message == null ? "unknown" : message));
        } catch (java.io.IOException ignored) {
            // client gone
        }
        emitter.complete();
    }

    private static void interruptWorker(Thread worker) {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
        }
    }
}
