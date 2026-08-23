package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.entity.AiChatMessage;
import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.service.ChatService;
import com.edushift.modules.ai.service.ChatService.StreamHandle;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST + SSE controller for the AI chat feature (Sprint 8 / BE-8.3).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <caption>Chat endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Authority</th><th>Returns</th></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/ai/chat/sessions</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@link AiChatSession} (201)</td></tr>
 *   <tr><td>GET </td>
 *       <td>/v1/ai/chat/sessions</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@code List<AiChatSession>} (200)</td></tr>
 *   <tr><td>GET </td>
 *       <td>/v1/ai/chat/sessions/{publicUuid}/messages</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@code List<AiChatMessage>} (200)</td></tr>
 *   <tr><td>DELETE</td>
 *       <td>/v1/ai/chat/sessions/{publicUuid}</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>204</td></tr>
 *   <tr><td>POST</td>
 *       <td>/v1/ai/chat/sessions/{publicUuid}/messages</td>
 *       <td>LMS_AI_GENERATE</td>
 *       <td>{@link SseEmitter} (text/event-stream)</td></tr>
 * </table>
 *
 * <h3>SSE streaming</h3>
 * The send-message endpoint returns an {@link SseEmitter}. The
 * controller pushes one {@code "token"} event per LLM chunk and a
 * final {@code "done"} event with the assistant message's
 * {@code publicUuid}. The client closes the connection to cancel.
 *
 * <h3>Multi-tenant</h3>
 * Chat is tenant-scoped via the Hibernate {@code @TenantId}
 * discriminator. Cross-tenant access returns 404 (anti-enumeration).
 */
@RestController
@RequestMapping({"/ai/chat", "/lms/ai/v1/ai/chat"})
@Tag(name = "AI chat", description = "Conversational AI assistant (Sprint 8)")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;
    private final Executor aiJobExecutor;

    public ChatController(
            ChatService chatService,
            CurrentUserProvider currentUserProvider,
            @Qualifier("aiJobExecutor") Executor aiJobExecutor) {
        this.chatService = chatService;
        this.currentUserProvider = currentUserProvider;
        this.aiJobExecutor = aiJobExecutor;
    }

    // ------------------------------------------------------------------
    // Session CRUD
    // ------------------------------------------------------------------

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Create a new chat session")
    public org.springframework.http.ResponseEntity<ApiResponse<AiChatSession>> createSession() {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("authenticated user required"));
        AiChatSession s = chatService.createSession(userId);
        return org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(s));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "List the caller's active chat sessions")
    public ApiResponse<List<AiChatSession>> listSessions(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("authenticated user required"));
        return ApiResponse.ok(chatService.listSessions(userId, Math.min(Math.max(limit, 1), 100)));
    }

    @GetMapping("/sessions/{publicUuid}/messages")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "List all messages of a session in chronological order")
    public ApiResponse<List<AiChatMessage>> listMessages(@PathVariable UUID publicUuid) {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("authenticated user required"));
        AiChatSession s = chatService.getSession(publicUuid)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(com.edushift.modules.ai.exception.ChatSessionNotFoundException::new);
        return ApiResponse.ok(chatService.findVisibleMessages(s.getId()));
    }

    @DeleteMapping("/sessions/{publicUuid}")
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Soft-delete a chat session (cascades to its messages)")
    public org.springframework.http.ResponseEntity<Void> deleteSession(@PathVariable UUID publicUuid) {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("authenticated user required"));
        chatService.deleteSession(publicUuid, userId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Streaming send
    // ------------------------------------------------------------------

    /**
     * Send a user message and stream the assistant's reply via SSE.
     *
     * <p>The body is a single {@code text} field. The response is a
     * {@code text/event-stream} with:
     * <ul>
     *   <li>{@code event: token} — {@code data: <chunk>} per token.</li>
     *   <li>{@code event: done}   — {@code data: {"publicUuid": "..."}}</li>
     *   <li>{@code event: error}  — {@code data: <message>} on LLM failure.</li>
     * </ul>
     */
    @PostMapping(value = "/sessions/{publicUuid}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('LMS_AI_GENERATE')")
    @Operation(summary = "Send a user message; stream the assistant reply via SSE")
    public SseEmitter sendMessage(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody SendMessageRequest body) {
        UUID userId = currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("authenticated user required"));

        // SSE timeout: 60s. The Mock LLM completes in ~150ms, real providers
        // stream a typical chat reply in 2-5s.
        SseEmitter emitter = new SseEmitter(60_000L);

        // Build the observer that pushes tokens to the emitter.
        LlmClient.StreamObserver observer = new LlmClient.StreamObserver() {
            @Override
            public boolean onToken(String chunk) {
                try {
                    emitter.send(SseEmitter.event().name("token").data(chunk));
                    return true;
                } catch (IOException e) {
                    // Client disconnected (or buffer broken) -> request cancel.
                    return false;
                }
            }
            @Override
            public void onComplete() {
                // No-op; the final "done" event is sent after stream() returns
                // with the assistant message's publicUuid.
            }
            @Override
            public void onError(Throwable error) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                } catch (IOException ignored) { /* client gone */ }
            }
        };

        // Run on aiJobExecutor so TenantContext + MDC propagate from the request
        // thread (ContextPropagatingTaskDecorator). Raw Thread would lose tenant.
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        aiJobExecutor.execute(() -> {
            workerThread.set(Thread.currentThread());
            try {
                StreamHandle handle = chatService.sendMessage(publicUuid, userId, body.text(), observer);
                emitter.send(SseEmitter.event().name("done")
                        .data("{\"publicUuid\":\"" + handle.assistantMessagePublicUuid() + "\","
                                + "\"cancelled\":" + handle.cancelled() + "}"));
                emitter.complete();
            } catch (com.edushift.modules.ai.exception.AiModuleException ex) {
                // Map domain exceptions to a single "error" SSE event and close.
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
                } catch (IOException ignored) { /* client gone */ }
                emitter.complete();
            } catch (LlmException ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
                } catch (IOException ignored) { /* client gone */ }
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                } catch (IOException ignored) { /* client gone */ }
                emitter.complete();
            }
        });

        // Hook the emitter's completion to interrupt the worker if the
        // client disconnects mid-stream.
        emitter.onCompletion(() -> interruptWorker(workerThread.get()));
        emitter.onTimeout(() -> { interruptWorker(workerThread.get()); emitter.complete(); });
        emitter.onError(ex -> interruptWorker(workerThread.get()));

        return emitter;
    }

    private static void interruptWorker(Thread worker) {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
        }
    }

    /**
     * Request body for the streaming send-message endpoint.
     */
    public record SendMessageRequest(
            @NotBlank @Size(min = 1, max = 4000) String text
    ) {}
}
