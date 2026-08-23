package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.config.MiniMaxProperties;
import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.ChatSessionNotFoundException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.repository.AiChatMessageRepository;
import com.edushift.modules.ai.repository.AiChatSessionRepository;
import com.edushift.modules.ai.safety.PiiSafetyFilter;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ChatService} (Sprint 8 / BE-8.3).
 *
 * <p>Covers the parts that don't require a DB or the full Spring
 * context: the system prompt builder and the in-memory
 * {@link LlmClient.StreamObserver} contract. Integration tests
 * (cross-tenant, persistence, SSE end-to-end) live in
 * {@code ChatServiceIT} (see {@code docs/qa/sprint-08-results.md}).
 */
class ChatServiceTest {

    private AiChatSessionRepository sessionRepo;
    private AiChatMessageRepository messageRepo;
    private TenantAiContextService tenantCtx;
    private AiQuotaService quotaService;
    private RateLimitService rateLimitService;
    private PiiSafetyFilter piiFilter;
    private LlmClient llmClient;
    private MiniMaxProperties miniMaxProperties;
    private ChatService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepo = mock(AiChatSessionRepository.class);
        messageRepo = mock(AiChatMessageRepository.class);
        tenantCtx = mock(TenantAiContextService.class);
        quotaService = mock(AiQuotaService.class);
        rateLimitService = mock(RateLimitService.class);
        piiFilter = new PiiSafetyFilter();
        llmClient = mock(LlmClient.class);
        miniMaxProperties = new MiniMaxProperties();
        miniMaxProperties.setDefaultModel("MiniMax-M3");
        service = new ChatService(sessionRepo, messageRepo, tenantCtx, quotaService,
                rateLimitService, piiFilter, llmClient, miniMaxProperties);
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("buildSystemPrompt: incluye nombre del tenant y contexto (ADR-8.4)")
    void buildSystemPrompt_includesTenantContext() {
        when(tenantCtx.snapshot(tenantId)).thenReturn(
                new TenantAiContextService.TenantContextSnapshot(
                        "Colegio Demo", "2026", java.util.List.of("COMU", "MATE"),
                        java.util.List.of("Lee diversos tipos de textos")));

        String prompt = service.buildSystemPrompt(tenantCtx.snapshot(tenantId));

        assertThat(prompt)
                .contains("Colegio Demo")
                .contains("2026")
                .contains("COMU")
                .contains("Lee diversos tipos de textos");
        assertThat(prompt.length()).isLessThanOrEqualTo(4096);
    }

    @Test
    @DisplayName("createSession: persiste con tenantId del contexto y TTL 7d")
    void createSession_persistsWithTenant() {
        // given
        UUID userId = UUID.randomUUID();
        when(sessionRepo.save(any(AiChatSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        AiChatSession s = service.createSession(userId);

        // then
        assertThat(s.getTenantId()).isEqualTo(tenantId);
        assertThat(s.getUserId()).isEqualTo(userId);
        assertThat(s.getStatus()).isEqualTo(AiChatSession.Status.ACTIVE);
        assertThat(s.getExpiresAt()).isNotNull();
        // TTL 7d ± 5s
        long ttlSec = java.time.Duration.between(java.time.Instant.now(), s.getExpiresAt()).getSeconds();
        assertThat(ttlSec).isBetween(7L * 86400 - 5, 7L * 86400 + 5);
    }

    @Test
    @DisplayName("getSession: lookup por publicUuid")
    void getSession_returnsFromRepo() {
        UUID pub = UUID.randomUUID();
        AiChatSession s = new AiChatSession();
        s.setPublicUuid(pub);
        when(sessionRepo.findByPublicUuid(pub)).thenReturn(Optional.of(s));

        Optional<AiChatSession> out = service.getSession(pub);
        assertThat(out).isPresent().get().isSameAs(s);
    }

    @Test
    @DisplayName("deleteSession: soft-deletes with deletedAt populated (chk_chat_sessions_deleted_at)")
    void deleteSession_setsDeletedAt() {
        UUID pub = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiChatSession s = new AiChatSession();
        s.setPublicUuid(pub);
        s.setUserId(userId);
        when(sessionRepo.findByPublicUuid(pub)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(AiChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteSession(pub, userId);

        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(sessionRepo).save(captor.capture());
        AiChatSession saved = captor.getValue();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AiChatSession.Status.DELETED);
    }

    @Test
    @DisplayName("resolveChatModel: usa tenant default_model o fallback MiniMax-M3")
    void resolveChatModel_usesTenantOrGlobalDefault() {
        TenantAiSettings settings = new TenantAiSettings();
        settings.setDefaultModel("MiniMax-M3");
        assertThat(service.resolveChatModel(settings)).isEqualTo("MiniMax-M3");

        settings.setDefaultModel("anthropic/claude-3.5-sonnet");
        assertThat(service.resolveChatModel(settings)).isEqualTo("MiniMax-M3");
    }

    @Test
    void deleteSession_wrongUserThrowsNotFound() {
        UUID pub = UUID.randomUUID();
        AiChatSession s = new AiChatSession();
        s.setPublicUuid(pub);
        s.setUserId(UUID.randomUUID());
        when(sessionRepo.findByPublicUuid(pub)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.deleteSession(pub, UUID.randomUUID()))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }
}
