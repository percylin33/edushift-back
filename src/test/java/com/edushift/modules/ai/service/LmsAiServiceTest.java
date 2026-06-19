package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.dto.QuestionSuggestion;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.QuizQuestionPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link LmsAiService} (BE-7c.1).
 *
 * <p>We mock every collaborator (LlmClient, AiQuotaService,
 * AiGenerationRepository, CurrentUserProvider) and the
 * {@link QuizQuestionPromptBuilder} to make the LlmRequest
 * predictable. The {@link TenantContext} is set manually.</p>
 */
class LmsAiServiceTest {

    private LlmClient llmClient;
    private QuizQuestionPromptBuilder promptBuilder;
    private AiQuotaService quotaService;
    private AiGenerationRepository generationRepo;
    private CurrentUserProvider currentUser;
    private ObjectMapper objectMapper;
    private LmsAiService service;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        llmClient      = mock(LlmClient.class);
        promptBuilder  = mock(QuizQuestionPromptBuilder.class);
        quotaService   = mock(AiQuotaService.class);
        generationRepo = mock(AiGenerationRepository.class);
        currentUser    = mock(CurrentUserProvider.class);
        objectMapper   = new ObjectMapper();

        service = new LmsAiService(llmClient, promptBuilder, quotaService,
                generationRepo, currentUser, objectMapper);

        TenantContext.set(TENANT_ID);
        when(currentUser.currentUserId()).thenReturn(Optional.of(USER_ID));
        when(promptBuilder.promptVersion()).thenReturn("quiz-question-suggest/v1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------
    // Happy path.
    // -------------------------------------------------------------------

    @Test
    @DisplayName("happy path: LLM returns valid JSON, service parses + persists + returns response")
    void happyPath() {
        TenantAiSettings settings = settings(true, 100, null, "openai/gpt-4o-mini");
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("openai/gpt-4o-mini", "sys", "user", 0.2, 2048, null, null));
        when(llmClient.providerId()).thenReturn("mock");
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"questions\":[{\"prompt\":\"¿Capital de Francia?\",\"type\":\"MC\",\"points\":5,"
                        + "\"options\":[{\"label\":\"París\",\"isCorrect\":true,\"explanation\":null},"
                        + "{\"label\":\"Londres\",\"isCorrect\":false,\"explanation\":null}],"
                        + "\"rationale\":\"geografía\"}]}",
                "openai/gpt-4o-mini", 42, 28, 350L
        ));
        // saveAndFlush returns the same entity (id was set on PrePersist; mock returns arg).
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SuggestQuizQuestionsResponse resp = service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("Capitales de Europa", 1, "MC"));

        assertThat(resp).isNotNull();
        assertThat(resp.model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(resp.provider()).isEqualTo("mock");
        assertThat(resp.promptVersion()).isEqualTo("quiz-question-suggest/v1");
        assertThat(resp.questions()).hasSize(1);
        QuestionSuggestion q = resp.questions().get(0);
        assertThat(q.prompt()).isEqualTo("¿Capital de Francia?");
        assertThat(q.questionType()).isEqualTo("MC");
        assertThat(q.options()).hasSize(2);
        assertThat(q.options().get(0).isCorrect()).isTrue();
        assertThat(resp.generationUuids()).hasSize(1);

        verify(quotaService, times(1)).incrementCounters(true, 42L, 28L);
    }

    // -------------------------------------------------------------------
    // Quota disabled / exhausted.
    // -------------------------------------------------------------------

    @Test
    @DisplayName("quota disabled → AiDisabledException, no LLM call, no audit row")
    void quotaDisabledSkipsLlm() {
        when(quotaService.verifyCanCall()).thenThrow(new AiDisabledException());

        assertThatThrownBy(() -> service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 1, null)))
                .isInstanceOf(AiDisabledException.class);

        verify(llmClient, never()).complete(any());
        verify(generationRepo, never()).saveAndFlush(any());
        verify(quotaService, never()).incrementCounters(anyBoolean(), anyLong(), anyLong());
    }

    // -------------------------------------------------------------------
    // LLM failure.
    // -------------------------------------------------------------------

    @Test
    @DisplayName("LLM throws timeout → LlmException surfaces, audit row marked FAILED, counter not bumped as success")
    void llmTimeoutMarksFailed() {
        TenantAiSettings settings = settings(true, 100, null, null);
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("m", "s", "u", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llmClient.complete(any()))
                .thenThrow(new LlmException(LlmException.TIMEOUT, "timed out"));

        assertThatThrownBy(() -> service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 1, null)))
                .isInstanceOf(LlmException.class)
                .satisfies(t -> assertThat(((LlmException) t).getCode()).isEqualTo(LlmException.TIMEOUT));

        // The audit row was saved three times since BE-7c.2 added the
        // PROCESSING mark: once as PENDING (sync path), once as
        // PROCESSING (runGeneration), once as FAILED.
        ArgumentCaptor<AiGeneration> captor = ArgumentCaptor.forClass(AiGeneration.class);
        verify(generationRepo, atLeast(2)).saveAndFlush(captor.capture());
        verify(generationRepo, times(1)).save(captor.capture());
        AiGeneration finalRow = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalRow.getStatus()).isEqualTo(AiGeneration.Status.FAILED);
        assertThat(finalRow.getErrorCode()).isEqualTo(LlmException.TIMEOUT);
        // counter is bumped as failed (no token counts).
        verify(quotaService, times(1)).incrementCounters(false, 0L, 0L);
    }

    // -------------------------------------------------------------------
    // Parse error.
    // -------------------------------------------------------------------

    @Test
    @DisplayName("LLM returns invalid JSON → AiParseException, FAILED audit row, FAILED counter")
    void llmInvalidJsonThrowsParse() {
        TenantAiSettings settings = settings(true, 100, null, null);
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("m", "s", "u", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "this is not json", "m", 10, 5, 100L));

        assertThatThrownBy(() -> service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 1, null)))
                .isInstanceOf(AiParseException.class);

        ArgumentCaptor<AiGeneration> captor = ArgumentCaptor.forClass(AiGeneration.class);
        verify(generationRepo, times(1)).save(captor.capture());
        AiGeneration finalRow = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalRow.getStatus()).isEqualTo(AiGeneration.Status.FAILED);
        assertThat(finalRow.getErrorCode()).isEqualTo("AI_PARSE_ERROR");
        assertThat(finalRow.getResponseText()).isEqualTo("this is not json");
        // Counter bumped as failed, but with the LLM's reported tokens
        // (so the dashboard shows "tried but parse failed").
        verify(quotaService, times(1)).incrementCounters(false, 10L, 5L);
    }

    @Test
    @DisplayName("LLM returns JSON but MC question has 0 correct options → AiParseException")
    void llmMcWithoutCorrectThrowsParse() {
        TenantAiSettings settings = settings(true, 100, null, null);
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("m", "s", "u", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"questions\":[{\"prompt\":\"Q?\",\"type\":\"MC\",\"points\":5,"
                        + "\"options\":[{\"label\":\"A\",\"isCorrect\":false,\"explanation\":null},"
                        + "{\"label\":\"B\",\"isCorrect\":false,\"explanation\":null}]}]}",
                "m", 5, 3, 50L));

        assertThatThrownBy(() -> service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 1, "MC")))
                .isInstanceOf(AiParseException.class)
                .hasMessageContaining("exactly 1 isCorrect=true");
    }

    // -------------------------------------------------------------------
    // Question type filter.
    // -------------------------------------------------------------------

    @Test
    @DisplayName("questionType filter drops mismatched questions silently (up to count)")
    void questionTypeFilterDropsMismatches() {
        TenantAiSettings settings = settings(true, 100, null, null);
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("m", "s", "u", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // LLM returns 1 MC + 1 TF, but we asked for "TF" → 1 result.
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"questions\":["
                        + "{\"prompt\":\"MC\",\"type\":\"MC\",\"points\":5,"
                        + "\"options\":[{\"label\":\"A\",\"isCorrect\":true,\"explanation\":null},"
                        + "{\"label\":\"B\",\"isCorrect\":false,\"explanation\":null}]},"
                        + "{\"prompt\":\"TF\",\"type\":\"TF\",\"points\":3,"
                        + "\"options\":[{\"label\":\"V\",\"isCorrect\":true,\"explanation\":null},"
                        + "{\"label\":\"F\",\"isCorrect\":false,\"explanation\":null}]}]}",
                "m", 5, 5, 100L));

        SuggestQuizQuestionsResponse resp = service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 5, "TF"));

        assertThat(resp.questions()).hasSize(1);
        assertThat(resp.questions().get(0).questionType()).isEqualTo("TF");
    }

    @Test
    @DisplayName("questionType filter that drops all questions → AiParseException")
    void questionTypeFilterDropsAll() {
        TenantAiSettings settings = settings(true, 100, null, null);
        when(quotaService.verifyCanCall()).thenReturn(settings);
        when(promptBuilder.build(any(), anyInt(), any(), any()))
                .thenReturn(new LlmRequest("m", "s", "u", 0.2, 2048, null, null));
        when(generationRepo.saveAndFlush(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(generationRepo.save(any(AiGeneration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                "{\"questions\":[{\"prompt\":\"Q\",\"type\":\"MC\",\"points\":5,"
                        + "\"options\":[{\"label\":\"A\",\"isCorrect\":true,\"explanation\":null},"
                        + "{\"label\":\"B\",\"isCorrect\":false,\"explanation\":null}]}]}",
                "m", 5, 5, 100L));

        assertThatThrownBy(() -> service.suggestQuizQuestions(
                new SuggestQuizQuestionsRequest("X", 5, "TF")))
                .isInstanceOf(AiParseException.class)
                .hasMessageContaining("no valid questions");
    }

    // -------------------------------------------------------------------
    // Test helpers.
    // -------------------------------------------------------------------

    private static TenantAiSettings settings(boolean enabled, Integer dailyQuota,
                                              Long monthlyTokenQuota, String defaultModel) {
        TenantAiSettings s = new TenantAiSettings();
        s.setAiEnabled(enabled);
        s.setDailyRequestQuota(dailyQuota);
        s.setMonthlyTokenQuota(monthlyTokenQuota);
        s.setDefaultModel(defaultModel);
        return s;
    }
}
