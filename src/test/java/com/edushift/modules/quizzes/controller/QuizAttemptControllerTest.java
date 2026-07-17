package com.edushift.modules.quizzes.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.quizzes.service.QuizRubricService;
import com.edushift.shared.multitenancy.TenantResolver;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

@WebMvcTest(QuizAttemptController.class)
@DisplayName("QuizAttemptController — REST surface for taker & grading flows")
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class QuizAttemptControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean QuizAttemptService attemptService;
    @MockitoBean QuizRubricService quizRubricService;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService;
    @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;

    private static JwtAuthenticationToken taker() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "s", "s@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_QUIZ_SUBMIT"),
                        new SimpleGrantedAuthority("LMS_QUIZ_READ")));
    }

    private static JwtAuthenticationToken grader() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "g", "g@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_QUIZ_GRADE"),
                        new SimpleGrantedAuthority("LMS_QUIZ_READ")));
    }

    @Test
    @DisplayName("POST /quizzes/{uuid}/attempts → 201 (start)")
    void startAttempt() throws Exception {
        mockMvc.perform(post("/v1/quizzes/{u}/attempts", UUID.randomUUID())
                        .with(csrf()).with(authentication(taker())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /attempts/{uuid} → 200 (taker)")
    void getAttemptAsTaker() throws Exception {
        mockMvc.perform(get("/v1/attempts/{u}", UUID.randomUUID())
                        .with(authentication(taker())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /attempts/{uuid} → 200 (grader)")
    void getAttemptAsGrader() throws Exception {
        mockMvc.perform(get("/v1/attempts/{u}", UUID.randomUUID())
                        .with(authentication(grader())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /attempts/{uuid} → 200 (save answers)")
    void saveAnswers() throws Exception {
        mockMvc.perform(patch("/v1/attempts/{u}", UUID.randomUUID())
                        .with(csrf()).with(authentication(taker()))
                        .contentType("application/json")
                        .content("{\"answers\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /attempts/{uuid}/submit → 200")
    void submitAttempt() throws Exception {
        mockMvc.perform(post("/v1/attempts/{u}/submit", UUID.randomUUID())
                        .with(csrf()).with(authentication(taker())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /quizzes/{uuid}/attempts → 200")
    void listAttempts() throws Exception {
        mockMvc.perform(get("/v1/quizzes/{u}/attempts", UUID.randomUUID())
                        .with(authentication(grader())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /quizzes/{uuid}/grading-queue → 200")
    void gradingQueue() throws Exception {
        mockMvc.perform(get("/v1/quizzes/{u}/grading-queue", UUID.randomUUID())
                        .with(authentication(grader())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /attempts/{uuid}/grade → 200")
    void gradeAttempt() throws Exception {
        mockMvc.perform(post("/v1/attempts/{u}/grade", UUID.randomUUID())
                        .with(csrf()).with(authentication(grader()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /attempts/{uuid}/grade-with-rubric → 200")
    void gradeWithRubric() throws Exception {
        mockMvc.perform(post("/v1/attempts/{u}/grade-with-rubric", UUID.randomUUID())
                        .with(csrf()).with(authentication(grader()))
                        .contentType("application/json")
                        .content("{\"picks\":[{\"criterionKey\":\"c1\",\"levelCode\":\"A\"}]}"))
                .andExpect(status().isOk());
    }
}