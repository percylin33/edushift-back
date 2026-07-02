package com.edushift.modules.notifications.controller;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.security.UnsubscribeTokenSigner;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.modules.notifications.web.UnsubscribePageRenderer;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(UnsubscribeController.class)
class UnsubscribeControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UnsubscribeTokenSigner signer;
    @MockitoBean NotificationService notificationService; @MockitoBean UnsubscribePageRenderer pageRenderer;
    @MockitoBean CurrentUserProvider currentUserProvider; @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken auth() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))); }
    @Test void unsubscribeSuccess() throws Exception { given(signer.verify(any())).willReturn(new UnsubscribeTokenSigner.Parsed(UUID.randomUUID(), Notification.Channel.IN_APP, Notification.Category.SYSTEM, System.currentTimeMillis() + 3600000L)); given(pageRenderer.renderSuccess(any())).willReturn("ok"); mockMvc.perform(get("/api/v1/unsubscribe").param("token", "x").with(authentication(auth()))).andExpect(status().isOk()); }
    @Test void unsubscribeBadToken() throws Exception { given(signer.verify(any())).willThrow(new SecurityException("bad")); given(pageRenderer.renderError(any())).willReturn("err"); mockMvc.perform(get("/api/v1/unsubscribe").param("token", "x").with(authentication(auth()))).andExpect(status().is4xxClientError()); }
}
