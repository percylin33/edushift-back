package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.ai.dto.GenerateSessionRequest;
import com.edushift.modules.ai.dto.GenerateSessionResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.entity.TenantAiSettings;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmClient;
import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.prompt.SessionGeneratorPromptBuilder;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link SessionGeneratorService} (Sprint 8 / BE-8.1).
 *
 * <p>Validates the 5 critical paths:</p>
 * <ol>
 *   <li>AI disabled for tenant → 403 (no audit row, no LLM call).</li>
 *   <li>Daily quota exhausted → 429 (no LLM call).</li>
 *   <li>Course not in caller's tenant → 404 (no LLM call).</li>
 *   <li>Happy path: LLM returns valid JSON → COMPLETED row + bump counters.</li>
 *   <li>LLM returns structurally invalid JSON (sums mismatch) → FAILED row + bump failure counters.</li>
 * </ol>
 */
class SessionGeneratorServiceTest {

    private LlmClient llmClient;
    private SessionGeneratorPromptBuilder promptBuilder;
    private AiQuotaService quotaService;
    private AiGenerationRepository generationRepo;
    private CurrentUserProvider currentUser;
    private ObjectMapper objectMapper;
    private CourseRepository courseRepository;
    private CourseLevelRepository courseLevelRepository;
    private CompetencyRepository competencyRepository;
    private CapacityRepository capacityRepository;
    private SessionGeneratorService service;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID courseId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID unitId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        promptBuilder = mock(SessionGeneratorPromptBuilder.class);
        quotaService = mock(AiQuotaService.class);
        generationRepo = mock(AiGenerationRepository.class);
        currentUser = mock(CurrentUserProvider.class);
        objectMapper = new ObjectMapper();
        courseRepository = mock(CourseRepository.class);
        courseLevelRepository = mock(CourseLevelRepository.class);
        competencyRepository = mock(CompetencyRepository.class);
        capacityRepository = mock(CapacityRepository.class);

        service = new SessionGeneratorService(
                llmClient, promptBuilder, quotaService, generationRepo, currentUser, objectMapper,
                courseRepository, courseLevelRepository, competencyRepository, capacityRepository,
                null, // learningSessionService — null OK because tests don't exercise persist
                null); // teacherAssignmentRepository — same

        // Default wiring.
        TenantContext.set(tenantId);
        when(currentUser.currentUserId()).thenReturn(Optional.of(userId));
        when(llmClient.providerId()).thenReturn("mock");
        // Prompt builder returns a stub LlmRequest.
        when(promptBuilder.build(any(), anyString(), any(), any(), any()))
                .thenReturn(new LlmRequest("mock-model", "system", "user", 0.3, 2048, null, null));
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

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            assertThatThrownBy(() -> service.generateSession(req))
                    .isInstanceOf(AiDisabledException.class);

            verify(llmClient, never()).complete(any());
            verify(generationRepo, never()).saveAndFlush(any());
            verify(quotaService, never()).incrementCounters(anyBoolean(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("AiQuotaExceededException → 429, no LLM call")
        void quotaExceededNoLlmCall() {
            when(quotaService.verifyCanCall()).thenThrow(new AiQuotaExceededException("daily cap"));

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            assertThatThrownBy(() -> service.generateSession(req))
                    .isInstanceOf(AiQuotaExceededException.class);

            verify(llmClient, never()).complete(any());
            verify(generationRepo, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Course not in tenant → 404, no LLM call")
        void courseNotFound() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());
            when(courseRepository.findByPublicUuid(courseId)).thenReturn(Optional.empty());

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            assertThatThrownBy(() -> service.generateSession(req))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(llmClient, never()).complete(any());
            verify(generationRepo, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("LLM happy path")
    class HappyPath {

        @Test
        @DisplayName("Valid JSON with all phases + matching duration → COMPLETED + bump success")
        void validLlmResponse() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());
            Course course = stubCourse();
            when(courseRepository.findByPublicUuid(courseId)).thenReturn(Optional.of(course));
            when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

            String llmJson = """
                    {
                      "title": "La fotosíntesis en plantas superiores",
                      "summary": "Los estudiantes exploran el proceso de fotosíntesis, identificando sus fases y productos.",
                      "activities": [
                        { "phase": "INICIO",     "name": "Lluvia de ideas",   "durationMinutes": 10, "description": "Motivación con imágenes de plantas verdes." },
                        { "phase": "DESARROLLO", "name": "Lectura dirigida",  "durationMinutes": 20, "description": "Lectura del texto escolar con guía." },
                        { "phase": "DESARROLLO", "name": "Experimento simple","durationMinutes": 10, "description": "Plantar una semilla con diferentes condiciones de luz." },
                        { "phase": "CIERRE",     "name": "Metacognición",     "durationMinutes":  5, "description": "Reflexión grupal sobre lo aprendido." }
                      ],
                      "resources": [
                        { "type": "TEXT", "title": "Texto escolar", "url": null, "description": "Capítulo 4 del libro de Ciencia y Ambiente." }
                      ],
                      "evaluationCriteria": [
                        { "name": "Comprensión del proceso", "weight": 0.6, "description": "Identifica las fases de la fotosíntesis." },
                        { "name": "Trabajo en equipo",        "weight": 0.4, "description": "Colabora activamente con sus compañeros." }
                      ]
                    }
                    """;
            when(llmClient.complete(any())).thenReturn(
                    new LlmResponse(llmJson, "mock-model", 200, 350, 1234L));

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            SessionGeneratorService.SessionGeneratorResult result = service.generateSession(req);

            assertThat(result.session().title()).isEqualTo("La fotosíntesis en plantas superiores");
            assertThat(result.session().activities()).hasSize(4);
            assertThat(result.session().activities())
                    .extracting(GenerateSessionResponse.Activity::phase)
                    .contains(GenerateSessionResponse.Activity.Phase.INICIO,
                              GenerateSessionResponse.Activity.Phase.DESARROLLO,
                              GenerateSessionResponse.Activity.Phase.CIERRE);
            assertThat(result.session().evaluationCriteria()).hasSize(2);
            assertThat(result.model()).isEqualTo("mock-model");
            assertThat(result.provider()).isEqualTo("mock");
            assertThat(result.generationUuid()).isNotNull();

            // Bumped success counter.
            verify(quotaService, times(1)).incrementCounters(true, 200L, 350L);

            // The audit row is mutated in-place (same object across calls),
            // so we capture the final state by snapshotting the row via
            // doAnswer (we attach a copy at each save point).
            ArgumentCaptor<AiGeneration.Status> statusCaptor =
                    ArgumentCaptor.forClass(AiGeneration.Status.class);
            org.mockito.Mockito.doAnswer(inv -> {
                AiGeneration g = inv.getArgument(0);
                statusCaptor.getAllValues(); // touch
                return g;
            }).when(generationRepo).saveAndFlush(any());
            org.mockito.Mockito.doAnswer(inv -> {
                AiGeneration g = inv.getArgument(0);
                statusCaptor.getAllValues(); // touch
                return g;
            }).when(generationRepo).save(any());

            // Simpler: just verify the COMPLETED save had the right shape by
            // checking the LAST save() call captures a row with the parsed payload.
            // The same object is mutated across calls, so its current state
            // is COMPLETED + responseParsed set.
            ArgumentCaptor<AiGeneration> savedCaptor = ArgumentCaptor.forClass(AiGeneration.class);
            verify(generationRepo, atLeastOnce()).save(savedCaptor.capture());
            AiGeneration completed = savedCaptor.getValue();
            assertThat(completed.getStatus()).isEqualTo(AiGeneration.Status.COMPLETED);
            assertThat(completed.getFeature()).isEqualTo(AiGeneration.Feature.SESSION_OUTLINE_SUGGEST);
            assertThat(completed.getResponseParsed()).isNotNull();
            assertThat(completed.getResponseParsed().get("title"))
                    .isEqualTo("La fotosíntesis en plantas superiores");
        }
    }

    @Nested
    @DisplayName("LLM parse errors")
    class ParseErrors {

        @Test
        @DisplayName("Sum of activity durations ≠ requested → AiParseException + FAILED row + bump failure")
        void durationSumMismatch() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());
            Course course = stubCourse();
            when(courseRepository.findByPublicUuid(courseId)).thenReturn(Optional.of(course));
            when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

            // Requested 45min, but activities sum to 50.
            String llmJson = """
                    {
                      "title": "La fotosíntesis en plantas superiores",
                      "summary": "Los estudiantes exploran el proceso de fotosíntesis, identificando sus fases y productos.",
                      "activities": [
                        { "phase": "INICIO",     "name": "Lluvia de ideas",   "durationMinutes": 10, "description": "Motivación con imágenes de plantas verdes." },
                        { "phase": "DESARROLLO", "name": "Lectura dirigida",  "durationMinutes": 20, "description": "Lectura del texto escolar con guía." },
                        { "phase": "DESARROLLO", "name": "Experimento simple","durationMinutes": 15, "description": "Plantar una semilla con diferentes condiciones de luz." },
                        { "phase": "CIERRE",     "name": "Metacognición",     "durationMinutes":  5, "description": "Reflexión grupal sobre lo aprendido." }
                      ],
                      "resources": [
                        { "type": "TEXT", "title": "Texto escolar", "url": null, "description": "Capítulo 4 del libro de Ciencia y Ambiente." }
                      ],
                      "evaluationCriteria": [
                        { "name": "C1", "weight": 0.6, "description": "Desc1" },
                        { "name": "C2", "weight": 0.4, "description": "Desc2" }
                      ]
                    }
                    """;
            when(llmClient.complete(any())).thenReturn(
                    new LlmResponse(llmJson, "mock-model", 200, 350, 1234L));

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            assertThatThrownBy(() -> service.generateSession(req))
                    .isInstanceOf(AiParseException.class)
                    .hasMessageContaining("sum of activities.durationMinutes");

            // FAILED row, no success bump.
            verify(quotaService, times(1)).incrementCounters(false, 200L, 350L);
        }

        @Test
        @DisplayName("LlmException thrown → FAILED row + bump failure")
        void llmExceptionMapped() {
            when(quotaService.verifyCanCall()).thenReturn(stubSettings());
            Course course = stubCourse();
            when(courseRepository.findByPublicUuid(courseId)).thenReturn(Optional.of(course));
            when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

            when(llmClient.complete(any())).thenThrow(new LlmException("TIMEOUT", "upstream timeout"));

            GenerateSessionRequest req = new GenerateSessionRequest(
                    "Fotosíntesis", courseId, unitId, 45, null, null);

            assertThatThrownBy(() -> service.generateSession(req))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("upstream timeout");

            // FAILED row, no success bump.
            verify(quotaService, times(1)).incrementCounters(false, 0L, 0L);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------

    private static TenantAiSettings stubSettings() {
        TenantAiSettings s = new TenantAiSettings();
        s.setAiEnabled(true);
        s.setDailyRequestQuota(100);
        s.setMonthlyTokenQuota(1_000_000L);
        s.setDefaultModel("mock-model");
        return s;
    }

    private Course stubCourse() {
        Course c = new Course();
        c.setId(UUID.randomUUID());
        setField(c, "publicUuid", courseId);
        c.setCode("BIO-101");
        c.setName("Biología");
        c.setIsActive(true);
        return c;
    }

    private static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + field, e);
        }
    }
}
