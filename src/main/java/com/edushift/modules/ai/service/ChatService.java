package com.edushift.modules.ai.service;

import com.edushift.modules.ai.entity.AiChatMessage;
import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.ChatSessionNotActiveException;
import com.edushift.modules.ai.exception.ChatSessionNotFoundException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.StreamObserver;
import com.edushift.modules.ai.llm.LlmClient.StreamResult;
import com.edushift.modules.ai.repository.AiChatMessageRepository;
import com.edushift.modules.ai.repository.AiChatSessionRepository;
import com.edushift.modules.ai.safety.AuditHash;
import com.edushift.modules.ai.safety.PiiSafetyFilter;
import com.edushift.modules.ai.service.TenantAiContextService.TenantContextSnapshot;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI chat service (Sprint 8 / BE-8.3).
 *
 * <p>Manages the lifecycle of chat sessions and the streaming of
 * assistant replies. Memory is in-session only (ADR-8.1, firmado
 * 2026-06-18); cross-session memory is out of scope for MVP v1.
 *
 * <h3>Decisiones</h3>
 * <ul>
 *   <li><b>ADR-8.1</b> — in-session memory only; cap 10 mensajes visibles
 *       (riesgo §5) + 1 system prompt. Si la conversación crece, se trunca
 *       al final del LLM call: el backend siempre envía los últimos N.</li>
 *   <li><b>ADR-8.2</b> — template estricto no aplica al chat; es
 *       conversacional.</li>
 *   <li><b>ADR-8.4</b> — system prompt con contexto del tenant (nombre,
 *       cursos top 20, competencias MINEDU, año activo). Cap 4KB.</li>
 *   <li><b>CHAT-QUOTA-01</b> — cada mensaje consume 1 quota unit
 *       (igual que el resto de features AI). El {@code AiQuotaService}
 *       enforza antes de persistir el user message.</li>
 *   <li><b>CHAT-TTL-01</b> — TTL 7 días desde {@code last_message_at}.
 *       Refrescado implícito en cada nuevo mensaje.</li>
 *   <li><b>CHAT-MT-01</b> — multi-tenant via Hibernate {@code @TenantId}.
 *       El servicio NUNCA lee {@code tenantId} del request; solo del
 *       {@code TenantContext} (seteado por el JWT filter).</li>
 * </ul>
 *
 * <h3>RBAC</h3>
 * Aplica {@code LMS_AI_GENERATE} (mismo que BE-7b.4). El controller
 * enforza con {@code @PreAuthorize}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /** Cap de mensajes visibles enviados al LLM (riesgo §5). */
    static final int MEMORY_CAP = 10;

    /** TTL de sesiones (ChatSessionSweeper). */
    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final AiChatSessionRepository sessionRepo;
    private final AiChatMessageRepository messageRepo;
    private final TenantAiContextService tenantContextService;
    private final AiQuotaService quotaService;
    private final RateLimitService rateLimitService;
    private final PiiSafetyFilter piiFilter;
    private final LlmClient llmClient;

    @Value("${app.ai.chat.model:MiniMax/MiniMax-M2}")
    private String chatModel;

    @Value("${app.ai.chat.temperature:0.7}")
    private double chatTemperature;

    // ------------------------------------------------------------------
    // Session management
    // ------------------------------------------------------------------

    /**
     * List the current user's active chat sessions, paginated.
     * Used by the FE sidebar (FE-8.3).
     */
    @Transactional(readOnly = true)
    public List<AiChatSession> listSessions(UUID userId, int limit) {
        return sessionRepo.findActiveByUser(userId,
                org.springframework.data.domain.PageRequest.of(0, limit)).getContent();
    }

    /**
     * Get a session by public UUID. Cross-tenant access returns empty
     * (anti-enumeration via Hibernate {@code @TenantId}).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AiChatSession> getSession(UUID publicUuid) {
        return sessionRepo.findByPublicUuid(publicUuid);
    }

    /**
     * List the visible (non-system) messages of a session in chronological
     * order. Used by the FE to render the thread on page load / refresh.
     */
    @Transactional(readOnly = true)
    public List<AiChatMessage> findVisibleMessages(UUID sessionId) {
        return messageRepo.findVisibleBySession(sessionId);
    }

    /**
     * Create a new chat session for the user. Title defaults to
     * {@code "Nueva conversacion"}; will be auto-renamed when the first
     * user message is sent.
     */
    @Transactional
    public AiChatSession createSession(UUID userId) {
        UUID tenantId = TenantContext.currentRequired();
        AiChatSession session = new AiChatSession();
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setTitle("Nueva conversacion");
        session.setStatus(AiChatSession.Status.ACTIVE);
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        return sessionRepo.save(session);
    }

    /**
     * Soft-delete a session (user-initiated). Cascades to its messages
     * via the FK ON DELETE CASCADE + the soft-delete filter.
     */
    @Transactional
    public void deleteSession(UUID sessionPublicUuid, UUID callerUserId) {
        AiChatSession s = sessionRepo.findByPublicUuid(sessionPublicUuid)
                .orElseThrow(ChatSessionNotFoundException::new);
        if (!s.getUserId().equals(callerUserId)) {
            // Anti-enumeration: another user's session returns 404, not 403.
            throw new ChatSessionNotFoundException();
        }
        s.setStatus(AiChatSession.Status.DELETED);
        s.markDeleted(); // TenantAwareEntity helper (sets deleted=true + deleted_at)
        sessionRepo.save(s);
    }

    // ------------------------------------------------------------------
    // Send message (streaming)
    // ------------------------------------------------------------------

    /**
     * Append a user message to a session and stream the assistant reply.
     * Returns the assistant message's {@code publicUuid} (so the FE can
     * reconcile the stream with the persisted row).
     *
     * <p>Enforces quota before the LLM call. If the tenant is over its
     * monthly cap, throws {@link AiQuotaExceededException} (no message
     * persisted). If AI is disabled for the tenant, throws
     * {@link AiDisabledException}.
     */
    @Transactional
    public StreamHandle sendMessage(UUID sessionPublicUuid, UUID callerUserId,
                                    String userText, StreamObserver observer) {
        AiChatSession session = sessionRepo.findByPublicUuid(sessionPublicUuid)
                .orElseThrow(ChatSessionNotFoundException::new);
        if (!session.getUserId().equals(callerUserId)) {
            throw new ChatSessionNotFoundException();
        }
        if (session.getStatus() != AiChatSession.Status.ACTIVE) {
            throw new ChatSessionNotActiveException();
        }

        // 1) Quota check (SEC-8.1: per-tenant cap)
        UUID tenantId = TenantContext.currentRequired();
        quotaService.verifyCanCall();
        // 2) Rate limit (SEC-8.1: per-user 20/h, 100/day)
        rateLimitService.checkAndIncrement(callerUserId);

        // 2) Persist user message (SEC-8.1: PII filter + audit hash)
        String userTextMasked = piiFilter.mask(userText);
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setTenantId(tenantId);
        userMsg.setChatSessionId(session.getId());
        userMsg.setRole(AiChatMessage.Role.USER);
        userMsg.setContent(userTextMasked);
        userMsg.setStatus(AiChatMessage.Status.COMPLETED);
        userMsg.setInputHash(AuditHash.sha256Hex(userTextMasked));
        messageRepo.save(userMsg);

        // 3) Auto-rename session if it's still the default
        if ("Nueva conversacion".equals(session.getTitle())) {
            String t = userText.strip();
            if (t.length() > 80) t = t.substring(0, 80) + "…";
            session.setTitle(t);
        }

        // 4) Build the LLM request (system + history + new user)
        TenantContextSnapshot ctx = tenantContextService.snapshot(tenantId);
        String systemPrompt = buildSystemPrompt(ctx);
        List<LlmClient.HistoryItem> history = loadHistory(session.getId());

        // 5) Create the assistant message in STREAMING state
        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setTenantId(tenantId);
        assistantMsg.setChatSessionId(session.getId());
        assistantMsg.setRole(AiChatMessage.Role.ASSISTANT);
        assistantMsg.setContent("");
        assistantMsg.setStatus(AiChatMessage.Status.STREAMING);
        assistantMsg = messageRepo.save(assistantMsg);
        UUID assistantPublicUuid = assistantMsg.getPublicUuid();

        // 6) Run the LLM stream and accumulate content
        StringBuilder buffer = new StringBuilder();
        LlmRequest req = new LlmRequest(
                chatModel, systemPrompt, userTextMasked, chatTemperature, 1024,
                List.of(), null, history);
        LlmClient.WrappingObserver wrap = new LlmClient.WrappingObserver(observer) {
            @Override
            public boolean onToken(String chunk) {
                buffer.append(chunk);
                return super.onToken(chunk);
            }
        };
        StreamResult result = llmClient.stream(req, wrap);

        // 7) Persist final assistant message (SEC-8.1: PII filter + audit hash)
        String finalContent = piiFilter.mask(buffer.toString());
        assistantMsg.setContent(finalContent);
        assistantMsg.setStatus(result.cancelled() ? AiChatMessage.Status.CANCELLED
                : AiChatMessage.Status.COMPLETED);
        assistantMsg.setModelUsed(result.model());
        assistantMsg.setPromptTokens(result.tokensIn());
        assistantMsg.setResponseTokens(result.tokensOut());
        assistantMsg.setLatencyMs((int) result.latencyMs());
        assistantMsg.setOutputHash(AuditHash.sha256Hex(finalContent));
        messageRepo.save(assistantMsg);

        // 8) Update session aggregates
        session.recordMessage(
                result.tokensIn() == null ? 0 : result.tokensIn(),
                result.tokensOut() == null ? 0 : result.tokensOut());
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        sessionRepo.save(session);

        return new StreamHandle(assistantPublicUuid, result.cancelled());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Build the system prompt with the tenant context (ADR-8.4).
     * Cap 4KB. If the assembled string would exceed the cap, it is
     * truncated and a marker appended.
     */
    String buildSystemPrompt(TenantContextSnapshot ctx) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("Eres el asistente IA de EduShift para el colegio '")
          .append(ctx.tenantName()).append("'. ");
        sb.append("Tu rol: ayudar a docentes y administradores con tareas academicas. ");
        sb.append("Responde en espanol, claro y conciso. ");
        sb.append("Si no sabes algo, dilo honestamente.\n\n");
        sb.append("CONTEXTO INSTITUCIONAL (cap 4KB):\n");
        sb.append("- Ano academico activo: ").append(ctx.activeYearName()).append("\n");
        if (ctx.courses() != null && !ctx.courses().isEmpty()) {
            sb.append("- Cursos activos: ")
              .append(String.join(", ", ctx.courses())).append("\n");
        }
        if (ctx.competencies() != null && !ctx.competencies().isEmpty()) {
            sb.append("- Competencias MINEDU cargadas: ")
              .append(String.join("; ", ctx.competencies())).append("\n");
        }
        String s = sb.toString();
        if (s.length() > 4096) {
            return s.substring(0, 4090) + "\n[...]";
        }
        return s;
    }

    /**
     * Load the last {@link #MEMORY_CAP} visible messages and convert to
     * the {@code HistoryItem} shape the LLM expects.
     */
    private List<LlmClient.HistoryItem> loadHistory(UUID sessionId) {
        List<AiChatMessage> last = messageRepo.findLastVisibleBySession(sessionId,
                org.springframework.data.domain.PageRequest.of(0, MEMORY_CAP));
        // findLastVisible returns DESC; reverse for chronological order.
        List<AiChatMessage> ordered = new ArrayList<>(last);
        java.util.Collections.reverse(ordered);
        List<LlmClient.HistoryItem> out = new ArrayList<>(ordered.size());
        for (AiChatMessage m : ordered) {
            // Skip the message we just inserted (will be passed as userPrompt).
            if (m.getRole() == AiChatMessage.Role.USER && m.getContent().equals("")) {
                continue;
            }
            out.add(new LlmClient.HistoryItem(
                    m.getRole() == AiChatMessage.Role.USER ? "user" : "assistant",
                    m.getContent()));
        }
        return out;
    }

    /**
     * Return value of {@link #sendMessage}: the assistant message publicUuid
     * (so the FE can reconcile) + whether the stream was cancelled.
     */
    public record StreamHandle(UUID assistantMessagePublicUuid, boolean cancelled) {}
}
