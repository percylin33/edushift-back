package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.prompt.QuizQuestionPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AsyncLmsAiOrchestrator} (BE-7c.2).
 *
 * <p>Focus: caller-thread behaviour of {@link AsyncLmsAiOrchestrator#startGeneration(SuggestQuizQuestionsRequest)}.
 * The worker-thread method ({@code runOnExecutor}) is harder to test
 * without a Spring context (the {@code @Async} advice needs the
 * proxy), so we only test its skip paths (CANCELLED, missing row)
 * by calling it directly. The full happy-path is covered by the
 * end-to-end smoke script {@code smoke-ai-async.ps1}.</p>
 */
class AsyncLmsAiOrchestratorTest {

    private LmsAiService lmsAiService;
    private QuizQuestionPromptBuilder promptBuilder;
    private AiQuotaService quotaService;
    private AiGenerationRepository generationRepo;
    private CurrentUserProvider currentUser;
    private AsyncLmsAiOrchestrator orchestrator;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lmsAiService = mock(LmsAiService.class);
        promptBuilder = mock(QuizQuestionPromptBuilder.class);
        quotaService = mock(AiQuotaService.class);
        generationRepo = mock(AiGenerationRepository.class);
        currentUser = mock(CurrentUserProvider.class);

        // The orchestrator takes itself as a "self" reference so the
        // @Async + @Transactional advice fire on the right target in
        // production. In this unit test the advice won't fire (no
        // Spring context), but the calls still execute synchronously
        // against the mocked repo — which is what we want for testing
        // the persistence path and the cancellation guard. The full
        // async happy path is covered by smoke-ai-async.ps1.
        orchestrator = new AsyncLmsAiOrchestrator(
                lmsAiService, promptBuilder, quotaService,
                generationRepo, currentUser, /* self */ null);
        // Inject self via reflection (final fields). This mirrors what
        // Spring's @Lazy self-injection does at runtime.
        try {
            java.lang.reflect.Field f = AsyncLmsAiOrchestrator.class.getDeclaredField("self");
            f.setAccessible(true);
            f.set(orchestrator, orchestrator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TenantContext.set(TENANT);
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("startGeneration: persists PENDING row, dispatches worker, returns publicUuid")
    void startGenerationPersistsAndDispatches() {
        TenantAiSettings settings = new TenantAiSettings();
        settings.setDefaultModel("gpt-4o-mini");
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new com.edushift.modules.ai.llm.LlmClient.LlmRequest(
                        "gpt-4o-mini", "system", "user", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // The worker would normally be dispatched via the proxy, but
        // in this test it just runs synchronously. We mock the
        // generationRepo.findByPublicUuid to return Optional.empty()
        // so the worker bails immediately (otherwise it would call
        // lmsAiService.runGeneration with a non-mocked row).
        when(generationRepo.findByPublicUuid(any())).thenReturn(Optional.empty());

        SuggestQuizQuestionsRequest req = new SuggestQuizQuestionsRequest("Topic X", 2, null);
        UUID result = orchestrator.startGeneration(req);

        assertThat(result).isNotNull();

        // The persisted row carries the right shape.
        org.mockito.ArgumentCaptor<AiGeneration> captor =
                org.mockito.ArgumentCaptor.forClass(AiGeneration.class);
        verify(generationRepo).saveAndFlush(captor.capture());
        AiGeneration persisted = captor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(AiGeneration.Status.PENDING);
        assertThat(persisted.getTenantId()).isEqualTo(TENANT);
        assertThat(persisted.getRequestUserId()).isEqualTo(USER);
        assertThat(persisted.getFeature()).isEqualTo(AiGeneration.Feature.QUIZ_QUESTION_SUGGEST);
    }

    @Test
    @DisplayName("startGeneration: throws AiDisabledException before any row is created")
    void disabledThrowsBeforeRow() {
        when(quotaService.verifyCanCall())
                .thenThrow(new AiDisabledException());

        assertThatThrownBy(() -> orchestrator.startGeneration(
                new SuggestQuizQuestionsRequest("Topic", 1, null)))
                .isInstanceOf(AiDisabledException.class);

        verify(generationRepo, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("runOnExecutor: skips when row is CANCELLED (cooperative cancel)")
    void runOnExecutorSkipsCancelled() {
        UUID uuid = UUID.randomUUID();
        AiGeneration cancelled = new AiGeneration();
        cancelled.setPublicUuid(uuid);
        cancelled.setStatus(AiGeneration.Status.CANCELLED);
        when(generationRepo.findByPublicUuid(uuid)).thenReturn(Optional.of(cancelled));

        orchestrator.runOnExecutor(uuid, new SuggestQuizQuestionsRequest("T", 1, null));

        verify(lmsAiService, never()).runGeneration(any(), any(), any());
    }

    @Test
    @DisplayName("runOnExecutor: skips when row is missing (defensive)")
    void runOnExecutorSkipsMissing() {
        UUID uuid = UUID.randomUUID();
        when(generationRepo.findByPublicUuid(uuid)).thenReturn(Optional.empty());

        orchestrator.runOnExecutor(uuid, new SuggestQuizQuestionsRequest("T", 1, null));

        verify(lmsAiService, never()).runGeneration(any(), any(), any());
    }
}
