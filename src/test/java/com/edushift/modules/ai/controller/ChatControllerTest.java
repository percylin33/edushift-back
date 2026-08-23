package com.edushift.modules.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.config.SecurityConfig;
import com.edushift.config.WebConfiguration;
import com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration;
import com.edushift.infrastructure.multitenancy.TenantInterceptor;
import com.edushift.modules.ai.entity.AiChatSession;
import com.edushift.modules.ai.service.ChatService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.security.LmsAuthorities;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
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

@WebMvcTest(
        controllers = ChatController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {MultiTenancyConfiguration.class, TenantInterceptor.class}))
@Import({GlobalExceptionHandler.class, SecurityConfig.class, WebConfiguration.class, com.edushift.test.EdushiftWebMvcTestConfig.class})
class ChatControllerTest {

    private static final String CANONICAL = "/v1/ai/chat/sessions";
    private static final String DOUBLE_V1 = "/v1/v1/ai/chat/sessions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean(name = "aiJobExecutor")
    private Executor aiJobExecutor;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.edushift.shared.security.LmsRoleAuthorityMapper roleAuthorityMapper;

    private JwtAuthenticationToken teacherToken(UUID userId) {
        JwtAuthenticatedPrincipal p = new JwtAuthenticatedPrincipal(
                userId, UUID.randomUUID(), "tecnosur", "cesar.ortega@tecnosur.edushift.pe");
        return new JwtAuthenticationToken(
                p,
                "test.access.jwt",
                List.of(new SimpleGrantedAuthority(LmsAuthorities.LMS_AI_GENERATE)));
    }

    @Test
    @DisplayName("POST /v1/ai/chat/sessions → 201 ApiResponse envelope")
    void createSessionCanonicalPath() throws Exception {
        UUID userId = UUID.fromString("a01104b4-99b6-42d3-a128-c39bc922700d");
        UUID sessionUuid = UUID.randomUUID();
        AiChatSession session = new AiChatSession();
        session.setPublicUuid(sessionUuid);
        session.setUserId(userId);
        given(chatService.createSession(eq(userId))).willReturn(session);

        mockMvc.perform(post(CANONICAL)
                        .with(csrf())
                        .with(authentication(teacherToken(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicUuid").value(sessionUuid.toString()));
    }

    @Test
    @DisplayName("POST /v1/v1/ai/chat/sessions → 404 (no double /v1 prefix)")
    void createSessionRejectsDoubleV1Path() throws Exception {
        mockMvc.perform(post(DOUBLE_V1)
                        .with(csrf())
                        .with(authentication(teacherToken(UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
