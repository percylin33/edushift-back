package com.edushift.modules.ai.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.ai.service.PromptManagementService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.test.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PromptManagementController.class)
@Import({com.edushift.test.EdushiftWebMvcTestConfig.class, com.edushift.test.TestSecurityConfig.class})
class PromptManagementControllerTest extends AbstractControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PromptManagementService service;

    private static JwtAuthenticationToken aiUser() {
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(ANY_USER, ANY_TENANT, "ai", "ai@t"),
                "t",
                List.of(new SimpleGrantedAuthority("LMS_AI_GENERATE")));
    }

    @Test
    void listTemplateKeys_returnsOk_whenAuthorized() throws Exception {
        given(service.listTemplateKeys()).willReturn(List.of("session.outline"));

        mockMvc.perform(get("/v1/ai/prompts/template-keys").with(auth(aiUser())))
                .andExpect(status().isOk());
    }

    @Test
    void listTemplateKeys_returns4xx_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/v1/ai/prompts/template-keys"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listTemplateKeys_returns4xx_whenStudent() throws Exception {
        mockMvc.perform(get("/v1/ai/prompts/template-keys").with(auth(student())))
                .andExpect(status().is4xxClientError());
    }
}
