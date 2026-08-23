package com.edushift.modules.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.ai.service.RubricGeneratorService;
import com.edushift.modules.ai.service.SessionGeneratorService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiGenerationController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class AiGenerationControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean SessionGeneratorService sessionGeneratorService;
    @MockitoBean RubricGeneratorService rubricGeneratorService;
    @MockitoBean(name = "aiJobExecutor") Executor aiJobExecutor;

    private static JwtAuthenticationToken aiUser() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(ANY_USER, ANY_TENANT, "ai", "ai@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_AI_GENERATE")));
    }

    @Test
    void generateSession_returnsOk_whenAuthorized() throws Exception {
        SessionGeneratorService.SessionGeneratorResult result =
                Mockito.mock(SessionGeneratorService.SessionGeneratorResult.class);
        given(sessionGeneratorService.generateSession(any())).willReturn(result);

        String body = """
                {"topic":"Fractions","courseId":"%s","unitId":"%s","durationMinutes":45}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/v1/ai/generate-session")
                        .with(csrf())
                        .with(auth(aiUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void generateSession_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/v1/ai/generate-session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void generateSession_returns4xx_whenStudent() throws Exception {
        String body = """
                {"topic":"Fractions","courseId":"%s","unitId":"%s","durationMinutes":45}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/v1/ai/generate-session")
                        .with(csrf())
                        .with(auth(student()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }
}
