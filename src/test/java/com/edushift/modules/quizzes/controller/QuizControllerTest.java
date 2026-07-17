package com.edushift.modules.quizzes.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.quizzes.service.QuizRubricService;
import com.edushift.modules.quizzes.service.QuizService;
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

@WebMvcTest(QuizController.class)
@DisplayName("QuizController — REST surface for the quiz builder & reader")
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class QuizControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean QuizService quizService;
    @MockitoBean QuizRubricService quizRubricService;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService;
    @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;

    private static JwtAuthenticationToken teacher() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_QUIZ_CREATE"),
                        new SimpleGrantedAuthority("LMS_QUIZ_READ"),
                        new SimpleGrantedAuthority("LMS_QUIZ_GRADE")));
    }

    @Test
    @DisplayName("POST /sections/{uuid}/quizzes → 201")
    void createQuiz() throws Exception {
        mockMvc.perform(post("/v1/sections/{u}/quizzes", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /sections/{uuid}/quizzes → 200")
    void listQuizzes() throws Exception {
        mockMvc.perform(get("/v1/sections/{u}/quizzes", UUID.randomUUID())
                        .with(authentication(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /quizzes/{uuid} → 200")
    void getQuiz() throws Exception {
        mockMvc.perform(get("/v1/quizzes/{u}", UUID.randomUUID())
                        .with(authentication(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /quizzes/{uuid} → 200")
    void patchQuiz() throws Exception {
        mockMvc.perform(patch("/v1/quizzes/{u}", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{\"title\":\"new\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /quizzes/{uuid} → 204")
    void deleteQuiz() throws Exception {
        mockMvc.perform(delete("/v1/quizzes/{u}", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /quizzes/{uuid}/questions → 201")
    void addQuestion() throws Exception {
        mockMvc.perform(post("/v1/quizzes/{u}/questions", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /questions/{uuid}/options → 201")
    void addOption() throws Exception {
        mockMvc.perform(post("/v1/questions/{u}/options", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /quizzes/{uuid}/publish → 200")
    void publishQuiz() throws Exception {
        mockMvc.perform(post("/v1/quizzes/{u}/publish", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /quizzes/{uuid}/close → 200")
    void closeQuiz() throws Exception {
        mockMvc.perform(post("/v1/quizzes/{u}/close", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /quizzes/{q}/attempts/{a}/answers/{ans} → 200")
    void gradeAnswer() throws Exception {
        mockMvc.perform(patch("/v1/quizzes/{q}/attempts/{a}/answers/{ans}",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{\"pointsAwarded\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /quizzes/{uuid}/rubric → 200")
    void attachRubric() throws Exception {
        mockMvc.perform(patch("/v1/quizzes/{u}/rubric", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher()))
                        .contentType("application/json")
                        .content("{\"rubricPublicUuid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /quizzes/{uuid}/rubric → 200")
    void detachRubric() throws Exception {
        mockMvc.perform(delete("/v1/quizzes/{u}/rubric", UUID.randomUUID())
                        .with(csrf()).with(authentication(teacher())))
                .andExpect(status().isOk());
    }
}