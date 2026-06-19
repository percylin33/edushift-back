package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.dto.GenerateRubricRequest;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.RubricGeneratorPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link RubricGeneratorService} (Sprint 8 / BE-8.2).
 *
 * <p>Validates the 5 critical paths:</p>
 * <ol>
 *   <li>AI disabled → 403, no LLM call.</li>
 *   <li>Quota exhausted → 429, no LLM call.</li>
 *   <li>Seed rubric not in tenant → 404.</li>
 *   <li>Happy path: LLM returns valid JSON → COMPLETED + bump success.</li>
 *   <li>Weights do not sum to 100 → AiParseException + FAILED row.</li>
 * </ol>
 */
class RubricGeneratorServiceTest {

    private LlmClient llmClient;
    private RubricGeneratorPromptBuilder promptBuilder;
    private AiQuotaService quotaService;
    private AiGenerationRepository generationRepo;
    private CurrentUserProvider currentUser;
    private ObjectMapper objectMapper;
    private RubricRepository rubricRepository;
    private RubricGeneratorService service;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID seedId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        promptBuilder = mock(RubricGeneratorPromptBuilder.class);
        quotaService = mock(AiQuotaService.class);
        generationRepo = mock(AiGenerationRepository.class);
        currentUser = mock(CurrentUserProvider.class);
        objectMapper = new ObjectMapper();
        rubricRepository = mock(RubricRepository.class);

        service = new RubricGeneratorService(
                llmClient, promptBuilder, quotaService, generationRepo, currentUser, objectMapper,
                rubricRepository);

        TenantContext.set(tenantId);
        when(currentUser.currentUserId()).thenReturn(Optional.of(userId));
        when(llmClient.providerId()).thenReturn("mock");
        when(promptBuilder.build(any(), any(), any()))
                .thenReturn(new LlmRequest("mock-model", "system", "user", 0.2, 2048, null, null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Quota gates")
    class QuotaGates {

        @Test
        @DisplayName("AiDisabledException → 403, no LLM call, no audit row")
        void aiDisabledNoLlmCall() {
            when(quotaService.verifyCanCall()).thenThrow(new AiDisabledException());

            GenerateRubricRequest req = baseRequest();

            assertThatThrownBy(() -> service.generateRubric(req))
                    .isInstanceOf(AiDisabledException.class);

            verify(llmClient, never()).complete(any());
            verify(generationRepo, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("AiQuotaExceededException → 429, no LLM call")
        void quotaExceededNoLlmCall() {
            when(quotaService.verifyCanCall()).thenThrow(new AiQuotaExceededException("monthly cap"));

            GenerateRubricRequest req = baseRequest();

            assertThatThrownBy(() -> service.generateRubric(req))
                    .isInstanceOf(AiQuotaExceededException.class);

            verify(llmClient, never()).complete(any());
        }
    }

    @Nested
    @DisplayName("Seed handling")
    class SeedHandling {

        @Test
        @DisplayName("Seed rubric not in tenant → 404, no LLM call")
        void seedNotFound() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());
            when(rubricRepository.findByPublicUuid(seedId)).thenReturn(Optional.empty());

            GenerateRubricRequest req = new GenerateRubricRequest(
                    "Rúbrica de Ensayo", null,
                    List.of("Claridad de tesis", "Calidad de argumentos"),
                    4, seedId);

            assertThatThrownBy(() -> service.generateRubric(req))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(llmClient, never()).complete(any());
            verify(generationRepo, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("LLM happy path")
    class HappyPath {

        @Test
        @DisplayName("Valid JSON with weights summing 100 → COMPLETED + bump success")
        void validLlmResponse() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());

            String llmJson = """
                    {
                      "name": "Rúbrica de Ensayo Argumentativo",
                      "description": "Evalúa ensayos argumentativos (4 niveles MINEDU).",
                      "criteria": [
                        {
                          "key": "thesis_clarity",
                          "name": "Claridad de tesis",
                          "description": "La tesis se enuncia de forma explícita.",
                          "weight": 30.0,
                          "descriptors": [
                            { "level": "EN_INICIO",       "text": "La tesis es ambigua o ausente." },
                            { "level": "EN_PROCESO",      "text": "La tesis aparece pero no se sostiene." },
                            { "level": "LOGRO_ESPERADO",  "text": "La tesis se enuncia con claridad y se argumenta." },
                            { "level": "LOGRO_DESTACADO", "text": "Latesis se enuncia, sostiene y se anticipa a contraargumentos." }
                          ]
                        },
                        {
                          "key": "argument_quality",
                          "name": "Calidad de argumentos",
                          "description": "Los argumentos son sólidos y bien sustentados.",
                          "weight": 70.0,
                          "descriptors": [
                            { "level": "EN_INICIO",       "text": "Los argumentos son débiles o irrelevantes." },
                            { "level": "EN_PROCESO",      "text": "Los argumentos son simples pero presentes." },
                            { "level": "LOGRO_ESPERADO",  "text": "Los argumentos son sólidos y variados." },
                            { "level": "LOGRO_DESTACADO", "text": "Los argumentos son sofisticados, variados y bien sustentados." }
                          ]
                        }
                      ],
                      "levels": [
                        { "code": "EN_INICIO",       "name": "En inicio",       "order": 0 },
                        { "code": "EN_PROCESO",      "name": "En proceso",      "order": 1 },
                        { "code": "LOGRO_ESPERADO",  "name": "Logro esperado",  "order": 2 },
                        { "code": "LOGRO_DESTACADO", "name": "Logro destacado", "order": 3 }
                      ]
                    }
                    """;
            when(llmClient.complete(any())).thenReturn(
                    new LlmResponse(llmJson, "mock-model", 200, 400, 1500L));

            GenerateRubricRequest req = baseRequest();

            RubricGeneratorService.RubricGeneratorResult result = service.generateRubric(req);

            assertThat(result.rubric().name()).isEqualTo("Rúbrica de Ensayo Argumentativo");
            assertThat(result.rubric().criteria()).hasSize(2);
            assertThat(result.rubric().levels()).hasSize(4);
            assertThat(result.rubric().criteria())
                    .extracting(c -> c.key())
                    .containsExactly("thesis_clarity", "argument_quality");
            assertThat(result.model()).isEqualTo("mock-model");
            assertThat(result.generationUuid()).isNotNull();

            verify(quotaService, times(1)).incrementCounters(true, 200L, 400L);

            // Verify final audit row state.
            ArgumentCaptor<AiGeneration> captor = ArgumentCaptor.forClass(AiGeneration.class);
            verify(generationRepo, atLeastOnce()).save(captor.capture());
            AiGeneration completed = captor.getValue();
            assertThat(completed.getStatus()).isEqualTo(AiGeneration.Status.COMPLETED);
            assertThat(completed.getFeature()).isEqualTo(AiGeneration.Feature.RUBRIC_SUGGEST);
            assertThat(completed.getResponseParsed()).isNotNull();
            assertThat(completed.getResponseParsed().get("name"))
                    .isEqualTo("Rúbrica de Ensayo Argumentativo");
        }
    }

    @Nested
    @DisplayName("LLM parse errors")
    class ParseErrors {

        @Test
        @DisplayName("Weights sum ≠ 100 → AiParseException + FAILED row + bump failure")
        void weightSumMismatch() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());

            // Weights sum to 110 (40 + 70), invalid.
            String llmJson = """
                    {
                      "name": "Rúbrica inválida",
                      "description": "Pesos no suman 100.",
                      "criteria": [
                        { "key": "c1", "name": "C1", "description": "d", "weight": 40.0 },
                        { "key": "c2", "name": "C2", "description": "d", "weight": 70.0 }
                      ],
                      "levels": [
                        { "code": "EN_INICIO",       "name": "En inicio",       "order": 0 },
                        { "code": "EN_PROCESO",      "name": "En proceso",      "order": 1 },
                        { "code": "LOGRO_ESPERADO",  "name": "Logro esperado",  "order": 2 },
                        { "code": "LOGRO_DESTACADO", "name": "Logro destacado", "order": 3 }
                      ]
                    }
                    """;
            when(llmClient.complete(any())).thenReturn(
                    new LlmResponse(llmJson, "mock-model", 200, 400, 1500L));

            GenerateRubricRequest req = baseRequest();

            assertThatThrownBy(() -> service.generateRubric(req))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("sum of criteria.weight");

            verify(quotaService, times(1)).incrementCounters(false, 200L, 400L);
        }

        @Test
        @DisplayName("LlmException thrown → FAILED row + bump failure")
        void llmExceptionMapped() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());

            when(llmClient.complete(any())).thenThrow(new LlmException("TIMEOUT", "upstream timeout"));

            GenerateRubricRequest req = baseRequest();

            assertThatThrownBy(() -> service.generateRubric(req))
                    .isInstanceOf(LlmException.class);

            verify(quotaService, times(1)).incrementCounters(false, 0L, 0L);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------

    private GenerateRubricRequest baseRequest() {
        return new GenerateRubricRequest(
                "Rúbrica de Ensayo", "Descripción breve.",
                List.of("Claridad de tesis", "Calidad de argumentos"),
                4, /* seedRubricId */ null);
    }

    private static TenantAiSettings stubSettings() {
        TenantAiSettings s = new TenantAiSettings();
        s.setAiEnabled(true);
        s.setDailyRequestQuota(100);
        s.setMonthlyTokenQuota(1_000_000L);
        s.setDefaultModel("mock-model");
        return s;
    }
}
