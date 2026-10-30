package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.entity.AiAgentType;
import com.edushift.modules.ai.entity.AiCitationRef;
import com.edushift.modules.ai.entity.AiChatMessage;
import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.exception.AiModuleException;
import com.edushift.modules.ai.exception.ChatSessionNotFoundException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.rag.AiSourceDocument;
import com.edushift.modules.ai.rag.AiSourceSearchService;
import com.edushift.modules.ai.service.AiAgentAccessService;
import com.edushift.modules.ai.service.AiAgentRequestIdempotencyService;
import com.edushift.modules.ai.service.ChatService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/ai/agents")
@Tag(name = "AI assistants", description = "Tenant and role scoped tutor, teacher and family chats")
public class AiAgentChatController {

    private static final Logger log = LoggerFactory.getLogger(AiAgentChatController.class);

    private final ChatService chats;
    private final AiAgentAccessService access;
    private final CurrentUserProvider users;
    private final AiSourceSearchService sources;
    private final AiAgentRequestIdempotencyService requests;
    private final Executor executor;

    public AiAgentChatController(ChatService chats, AiAgentAccessService access,
                                 CurrentUserProvider users, AiSourceSearchService sources,
                                 AiAgentRequestIdempotencyService requests,
                                 @Qualifier("aiJobExecutor") Executor executor) {
        this.chats = chats;
        this.access = access;
        this.users = users;
        this.sources = sources;
        this.requests = requests;
        this.executor = executor;
    }

    @PostMapping("/{type}/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a typed AI assistant session")
    public ResponseEntity<ApiResponse<SessionResponse>> create(
            @PathVariable AiAgentType type,
            @Valid @RequestBody CreateSessionRequest request,
            Authentication authentication) {
        requireRole(type, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                SessionResponse.from(chats.createAgentSession(userId(), type,
                        request.subjectPublicUuid()))));
    }

    @GetMapping("/{type}/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List own sessions for one assistant")
    public ApiResponse<List<SessionResponse>> list(
            @PathVariable AiAgentType type, @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        requireRole(type, authentication);
        return ApiResponse.ok(chats.listAgentSessions(userId(), type,
                Math.max(1, Math.min(limit, 100))).stream()
                .map(SessionResponse::from).toList());
    }

    @GetMapping("/{type}/sessions/{publicUuid}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List own messages for an authorized assistant session")
    public ApiResponse<List<MessageResponse>> messages(
            @PathVariable AiAgentType type, @PathVariable UUID publicUuid,
            Authentication authentication) {
        requireRole(type, authentication);
        AiChatSession session = requireSession(type, publicUuid, userId());
        List<AiChatMessage> messages = chats.findVisibleMessages(session.getId());
        Set<UUID> citedIds = messages.stream()
                .flatMap(m -> m.getCitations().stream())
                .map(AiCitationRef::sourcePublicUuid).collect(java.util.stream.Collectors.toSet());
        AiSourceDocument.Audience audience = switch (type) {
            case TUTOR -> AiSourceDocument.Audience.STUDENT;
            case TEACHER -> AiSourceDocument.Audience.TEACHER;
            case FAMILY -> AiSourceDocument.Audience.FAMILY;
        };
        Set<UUID> visibleIds = sources.visibleSourceIds(citedIds, audience);
        return ApiResponse.ok(messages.stream()
                .map(m -> MessageResponse.from(m, visibleIds)).toList());
    }

    @PostMapping(value = "/{type}/sessions/{publicUuid}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send a message and stream answer tokens and RAG citations")
    public SseEmitter send(
            @PathVariable AiAgentType type,
            @PathVariable UUID publicUuid,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        requireRole(type, authentication);
        UUID userId = userId();
        AiChatSession session = requireSession(type, publicUuid, userId);
        String requestKey = idempotencyKey == null
                ? UUID.randomUUID().toString() : idempotencyKey;
        var claim = requests.claim(session.getId(), userId, requestKey, request.text());
        if (claim.state() == AiAgentRequestIdempotencyService.State.PROCESSING) {
            throw new ConflictException("AI_REQUEST_IN_PROGRESS",
                    "Esta pregunta todavía se está procesando.");
        }
        if (claim.state() == AiAgentRequestIdempotencyService.State.FAILED) {
            throw new ConflictException("AI_REQUEST_ALREADY_FAILED",
                    "Esta solicitud falló. Envía una nueva pregunta para reintentar.");
        }
        if (claim.state() == AiAgentRequestIdempotencyService.State.COMPLETED) {
            return replay(session, type, claim.assistantPublicUuid());
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        LlmClient.StreamObserver observer = new LlmClient.StreamObserver() {
            @Override
            public boolean onToken(String token) {
                try {
                    emitter.send(SseEmitter.event().name("token").data(token));
                    return true;
                } catch (IOException ex) {
                    return false;
                }
            }
            @Override public void onComplete() {}
            @Override public void onError(Throwable error) {
                log.warn("AI agent provider stream failed: agentType={}, session={}, errorType={}",
                        type, publicUuid, error == null ? "unknown" : error.getClass().getSimpleName());
                sendEvent(emitter, "error", Map.of("code", "LLM_FAILURE",
                        "message", "No se pudo completar la respuesta."));
            }
        };
        executor.execute(() -> {
            try {
                var result = chats.sendAgentMessage(publicUuid, userId, type,
                        request.text(), observer);
                requests.complete(session.getId(), userId, requestKey,
                        result.assistantMessagePublicUuid());
                for (var citation : result.citations()) {
                    sendEvent(emitter, "citation", citation);
                }
                sendEvent(emitter, "done", Map.of(
                        "publicUuid", result.assistantMessagePublicUuid().toString(),
                        "cancelled", result.cancelled()));
                emitter.complete();
            } catch (AiModuleException ex) {
                failRequest(session.getId(), userId, requestKey);
                log.warn("AI agent request rejected: agentType={}, session={}, code={}",
                        type, publicUuid, ex.getCode());
                sendEvent(emitter, "error", Map.of("code", ex.getCode(),
                        "message", ex.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                failRequest(session.getId(), userId, requestKey);
                String errorCode = ex instanceof LlmException llmException
                        ? llmException.getCode() : ex.getClass().getSimpleName();
                log.error("AI agent response failed: agentType={}, session={}, errorCode={}",
                        type, publicUuid, errorCode, ex);
                sendEvent(emitter, "error", Map.of("code", "LLM_FAILURE",
                        "message", "No se pudo completar la respuesta."));
                emitter.complete();
            }
        });
        return emitter;
    }

    private SseEmitter replay(AiChatSession session, AiAgentType type, UUID assistantId) {
        AiChatMessage message = chats.findVisibleMessages(session.getId()).stream()
                .filter(m -> assistantId.equals(m.getPublicUuid()))
                .findFirst().orElseThrow(() -> new ConflictException(
                        "AI_REQUEST_RESULT_MISSING", "No se encontró la respuesta anterior."));
        AiSourceDocument.Audience audience = switch (type) {
            case TUTOR -> AiSourceDocument.Audience.STUDENT;
            case TEACHER -> AiSourceDocument.Audience.TEACHER;
            case FAMILY -> AiSourceDocument.Audience.FAMILY;
        };
        Set<UUID> visible = sources.visibleSourceIds(message.getCitations().stream()
                .map(AiCitationRef::sourcePublicUuid)
                .collect(java.util.stream.Collectors.toSet()), audience);
        SseEmitter emitter = new SseEmitter(60_000L);
        sendEvent(emitter, "token", message.getContent());
        message.getCitations().stream()
                .filter(c -> visible.contains(c.sourcePublicUuid()))
                .forEach(c -> sendEvent(emitter, "citation", c));
        sendEvent(emitter, "done", Map.of("publicUuid", assistantId.toString(),
                "cancelled", message.getStatus() == AiChatMessage.Status.CANCELLED));
        emitter.complete();
        return emitter;
    }

    private void failRequest(UUID sessionId, UUID userId, String requestKey) {
        try {
            requests.fail(sessionId, userId, requestKey);
        } catch (RuntimeException ignored) {
            // The stale-key job reconciles an unavailable database later.
        }
    }

    private AiChatSession requireSession(AiAgentType type, UUID id, UUID userId) {
        AiChatSession session = chats.getSession(id)
                .filter(s -> s.getUserId().equals(userId) && s.getAgentType() == type)
                .orElseThrow(ChatSessionNotFoundException::new);
        access.requireSubject(type, userId, session.getSubjectPublicUuid());
        return session;
    }

    private UUID userId() {
        return users.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("Authentication required"));
    }

    private static void requireRole(AiAgentType type, Authentication authentication) {
        String authority = switch (type) {
            case TUTOR -> LmsAuthorities.AI_TUTOR_USE;
            case TEACHER -> LmsAuthorities.AI_TEACHER_USE;
            case FAMILY -> LmsAuthorities.AI_FAMILY_USE;
        };
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> authority.equals(a.getAuthority()))) {
            throw new AccessDeniedException("AI assistant role not permitted");
        }
    }

    private static void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ignored) {
            // The client may already have disconnected.
        }
    }

    public record CreateSessionRequest(UUID subjectPublicUuid) {}
    public record SendMessageRequest(@NotBlank @Size(max = 4000) String text) {}
    public record SessionResponse(UUID publicUuid, AiAgentType agentType,
                                  UUID subjectPublicUuid, String title,
                                  int messageCount, Instant updatedAt) {
        static SessionResponse from(AiChatSession session) {
            return new SessionResponse(session.getPublicUuid(), session.getAgentType(),
                    session.getSubjectPublicUuid(), session.getTitle(),
                    session.getMessageCount(), session.getUpdatedAt());
        }
    }
    public record MessageResponse(UUID publicUuid, AiChatMessage.Role role,
                                  String content, AiChatMessage.Status status,
                                  Instant createdAt, List<AiCitationRef> citations) {
        static MessageResponse from(AiChatMessage message, Set<UUID> visibleIds) {
            return new MessageResponse(message.getPublicUuid(), message.getRole(),
                    message.getContent(), message.getStatus(), message.getCreatedAt(),
                    message.getCitations().stream()
                            .filter(c -> visibleIds.contains(c.sourcePublicUuid()))
                            .toList());
        }
    }
}
