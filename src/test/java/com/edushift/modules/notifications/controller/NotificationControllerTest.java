package com.edushift.modules.notifications.controller;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
@WebMvcTest(NotificationController.class)
@Import(com.edushift.test.EdushiftWebMvcTestConfig.class)
class NotificationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService notificationService; @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken auth() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))); }
    @Test void list() throws Exception { given(currentUserProvider.currentUserId()).willReturn(Optional.of(UUID.randomUUID())); mockMvc.perform(get("/v1/notifications").with(authentication(auth()))).andExpect(status().isOk()); }
    @Test void unreadCount() throws Exception { given(currentUserProvider.currentUserId()).willReturn(Optional.of(UUID.randomUUID())); given(notificationService.countUnread(any())).willReturn(5L); mockMvc.perform(get("/v1/notifications/unread-count").with(authentication(auth()))).andExpect(status().isOk()); }
    @Test void markRead() throws Exception { given(currentUserProvider.currentUserId()).willReturn(Optional.of(UUID.randomUUID())); given(notificationService.markRead(any(), any())).willReturn(true); mockMvc.perform(patch("/v1/notifications/{id}/read", UUID.randomUUID()).with(authentication(auth()))).andExpect(status().isOk()); }
}
