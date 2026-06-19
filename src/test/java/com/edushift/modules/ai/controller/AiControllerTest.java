package com.edushift.modules.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.config.SecurityConfig;
import com.edushift.config.WebConfiguration;
import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.ai.dto.QuestionSuggestion;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsRequest;
import com.edushift.modules.ai.dto.SuggestQuizQuestionsResponse;
import com.edushift.modules.ai.entity.AiGeneration;
import com.edushift.modules.ai.exception.AiDisabledException;
import com.edushift.modules.ai.exception.AiGenerationNotFoundException;
import com.edushift.modules.ai.exception.AiQuotaExceededException;
import com.edushift.modules.ai.llm.LlmException;
import com.edushift.modules.ai.repository.AiGenerationRepository;
import com.edushift.modules.ai.service.AsyncLmsAiOrchestrator;
import com.edushift.modules.ai.service.LmsAiService;
import com.edushift.modules.ai.service.RubricGeneratorService;
import com.edushift.modules.ai.service.SessionGeneratorService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.security.LmsAuthorities;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AiController} HTTP-slice tests (BE-7c.1).
 *
 * <p>Focused on routing, validation, RBAC and the error envelope.
 * The {@code LmsAiService} is fully mocked.</p>
 */
@WebMvcTest(
        controllers = AiController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({GlobalExceptionHandler.class, SecurityConfig.class, WebConfiguration.class})
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private LmsAiService lmsAiService;

    @MockitoBean
    private AsyncLmsAiOrchestrator asyncOrchestrator;

    @MockitoBean
    private SessionGeneratorService sessionGeneratorService;

    @MockitoBean
    private RubricGeneratorService rubricGeneratorService;

    @MockitoBean
    private AiGenerationRepository generationRepo;

    /**
     * The JWT filter is constructor-injected with {@link JwtService} and
     * {@link com.edushift.shared.security.LmsRoleAuthorityMapper}
     * (the latter was added in the BE-7c.1.1 bugfix). The filter
     * itself is not exercised in this slice (we use
     * {@code SecurityMockMvcRequestPostProcessors.authentication(...)})
     * but Spring still needs both beans to wire the security chain.
     */
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

    private static final String BASE = "/v1/lms/ai/quiz-questions";

    private JwtAuthenticationToken teacherToken() {
        JwtAuthenticatedPrincipal p = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "demo", "teacher-1@demo.edushift.pe");
        return new JwtAuthenticationToken(
                p,
                "test.access.jwt",
                List.of(new SimpleGrantedAuthority(LmsAuthorities.LMS_AI_GENERATE)));
    }

    private JwtAuthenticationToken studentToken() {
        JwtAuthenticatedPrincipal p = new JwtAuthenticatedPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "demo", "student-1@demo.edushift.pe");
        return new JwtAuthenticationToken(
                p,
                "test.access.jwt",
                List.of(new SimpleGrantedAuthority("LMS_QUIZ_SUBMIT")));  // no LMS_AI_GENERATE
    }

    // -------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------

    @Test
    @DisplayName("happy path: 200 with the SuggestQuizQuestionsResponse envelope")
    void happyPath() throws Exception {
        SuggestQuizQuestionsRequest req = new SuggestQuizQuestionsRequest(
                "Capitales de Europa", 2, "MC");
        SuggestQuizQuestionsResponse resp = new SuggestQuizQuestionsResponse(
                List.of(new QuestionSuggestion("id-1", "¿Capital de Francia?", "MC", 5,
                        List.of(new QuestionSuggestion.OptionSuggestion("París", true, null),
                                new QuestionSuggestion.OptionSuggestion("Londres", false, null)),
                        "geografía")),
                "openai/gpt-4o-mini", "mock", "quiz-question-suggest/v1",
                List.of(UUID.randomUUID().toString()));
        given(lmsAiService.suggestQuizQuestions(any())).willReturn(resp);

        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questions[0].prompt").value("¿Capital de Francia?"))
                .andExpect(jsonPath("$.data.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.data.provider").value("mock"))
                .andExpect(jsonPath("$.data.promptVersion").value("quiz-question-suggest/v1"));

        then(lmsAiService).should().suggestQuizQuestions(any());
    }

    // -------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------

    @Test
    @DisplayName("blank topic → 400 VALIDATION with field=topic")
    void blankTopic() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("", 1, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("topic"));

        then(lmsAiService).should(never()).suggestQuizQuestions(any());
    }

    @Test
    @DisplayName("count=0 → 400 VALIDATION with field=count")
    void countZero() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("Algo", 0, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("count"));
    }

    @Test
    @DisplayName("questionType=INVALID → 400 VALIDATION")
    void invalidQuestionType() throws Exception {
        String body = """
                { "topic": "Algo", "count": 1, "questionType": "INVALID" }
                """;
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("questionType"));
    }

    // -------------------------------------------------------------------
    // Error mapping
    // -------------------------------------------------------------------

    @Test
    @DisplayName("AiDisabledException → 403 with code=AI_DISABLED")
    void aiDisabled() throws Exception {
        given(lmsAiService.suggestQuizQuestions(any()))
                .willThrow(new AiDisabledException());
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("Algo", 1, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("AI_DISABLED"));
    }

    @Test
    @DisplayName("AiQuotaExceededException → 429 with code=AI_QUOTA_EXCEEDED")
    void aiQuotaExceeded() throws Exception {
        given(lmsAiService.suggestQuizQuestions(any()))
                .willThrow(new AiQuotaExceededException("Daily AI request quota exhausted"));
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("Algo", 1, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errors[0].code").value("AI_QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("LlmException(TIMEOUT) → 504 with code=LLM_TIMEOUT")
    void llmTimeout() throws Exception {
        given(lmsAiService.suggestQuizQuestions(any()))
                .willThrow(new LlmException(LlmException.TIMEOUT, "timed out"));
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("Algo", 1, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errors[0].code").value("LLM_TIMEOUT"));
    }

    @Test
    @DisplayName("LlmException(QUOTA) → 402 with code=LLM_QUOTA")
    void llmQuota() throws Exception {
        given(lmsAiService.suggestQuizQuestions(any()))
                .willThrow(new LlmException(LlmException.QUOTA, "out of credits"));
        String body = objectMapper.writeValueAsString(
                new SuggestQuizQuestionsRequest("Algo", 1, null));
        mockMvc.perform(post(BASE)
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errors[0].code").value("LLM_QUOTA"));
    }

    // -------------------------------------------------------------------
    // Async mode (BE-7c.2)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("POST ?async=true → 202 with generationUuid and pollUrl")
    void asyncAccepted() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        given(asyncOrchestrator.startGeneration(any())).willReturn(generationUuid);
        SuggestQuizQuestionsRequest req = new SuggestQuizQuestionsRequest("Topic", 1, null);
        mockMvc.perform(post(BASE + "?async=true")
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationUuid").value(generationUuid.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.pollUrl").value(
                        "/api/v1/lms/ai/generations/" + generationUuid));

        then(lmsAiService).should(never()).suggestQuizQuestions(any());
        then(asyncOrchestrator).should().startGeneration(any());
    }

    @Test
    @DisplayName("POST ?async=true with disabled AI → 403 AI_DISABLED (orchestrator throws)")
    void asyncDisabled() throws Exception {
        given(asyncOrchestrator.startGeneration(any()))
                .willThrow(new AiDisabledException());
        SuggestQuizQuestionsRequest req = new SuggestQuizQuestionsRequest("Topic", 1, null);
        mockMvc.perform(post(BASE + "?async=true")
                        .with(csrf())
                        .with(authentication(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("AI_DISABLED"));
    }

    // -------------------------------------------------------------------
    // GET /generations/{publicUuid} (BE-7c.2)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("GET generation: COMPLETED row returns 200 with questions")
    void getGenerationCompleted() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        AiGeneration gen = new AiGeneration();
        gen.setPublicUuid(generationUuid);
        gen.setStatus(AiGeneration.Status.COMPLETED);
        gen.setModelUsed("openai/gpt-4o-mini");
        gen.setResponseParsed(java.util.Map.of("questions",
                List.of(java.util.Map.of("id", "q-1", "prompt", "Capital de Francia?",
                        "type", "MC", "points", 5, "options", List.of(),
                        "rationale", "geografía"))));
        given(generationRepo.findByPublicUuid(generationUuid))
                .willReturn(java.util.Optional.of(gen));

        mockMvc.perform(get("/v1/lms/ai/generations/" + generationUuid)
                        .with(authentication(teacherToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.generationUuid").value(generationUuid.toString()))
                .andExpect(jsonPath("$.data.questions[0].prompt").value("Capital de Francia?"));
    }

    @Test
    @DisplayName("GET generation: PENDING row returns 200 with no questions")
    void getGenerationPending() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        AiGeneration gen = new AiGeneration();
        gen.setPublicUuid(generationUuid);
        gen.setStatus(AiGeneration.Status.PENDING);
        given(generationRepo.findByPublicUuid(generationUuid))
                .willReturn(java.util.Optional.of(gen));

        mockMvc.perform(get("/v1/lms/ai/generations/" + generationUuid)
                        .with(authentication(teacherToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.questions").doesNotExist());
    }

    @Test
    @DisplayName("GET generation: missing row → 404 AI_GENERATION_NOT_FOUND")
    void getGenerationMissing() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        given(generationRepo.findByPublicUuid(generationUuid))
                .willReturn(java.util.Optional.empty());

        mockMvc.perform(get("/v1/lms/ai/generations/" + generationUuid)
                        .with(authentication(teacherToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("AI_GENERATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET generation: student without LMS_AI_GENERATE → 403")
    void getGenerationRbac() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        mockMvc.perform(get("/v1/lms/ai/generations/" + generationUuid)
                        .with(authentication(studentToken())))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------
    // DELETE /generations/{publicUuid} (BE-7c.2)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("DELETE generation: PENDING → 204 and status persisted as CANCELLED")
    void cancelPending() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        AiGeneration gen = new AiGeneration();
        gen.setPublicUuid(generationUuid);
        gen.setStatus(AiGeneration.Status.PENDING);
        given(generationRepo.findByPublicUuid(generationUuid))
                .willReturn(java.util.Optional.of(gen));
        given(generationRepo.save(any(AiGeneration.class)))
                .willAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/v1/lms/ai/generations/" + generationUuid)
                        .with(csrf())
                        .with(authentication(teacherToken())))
                .andExpect(status().isNoContent());
        then(generationRepo).should().save(any(AiGeneration.class));
    }

    @Test
    @DisplayName("DELETE generation: COMPLETED → 204 (idempotent, no save)")
    void cancelCompletedIdempotent() throws Exception {
        UUID generationUuid = UUID.randomUUID();
        AiGeneration gen = new AiGeneration();
        gen.setPublicUuid(generationUuid);
        gen.setStatus(AiGeneration.Status.COMPLETED);
        given(generationRepo.findByPublicUuid(generationUuid))
                .willReturn(java.util.Optional.of(gen));

        mockMvc.perform(delete("/v1/lms/ai/generations/" + generationUuid)
                        .with(csrf())
                        .with(authentication(teacherToken())))
                .andExpect(status().isNoContent());
        then(generationRepo).should(never()).save(any(AiGeneration.class));
    }
}
