package com.edushift.modules.attendance.controller;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.attendance.service.DashboardService;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
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
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean CurrentUserProvider currentUserProvider; @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken admin() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))); }
    @Test void overview() throws Exception { mockMvc.perform(get("/attendance/dashboard/overview").with(authentication(admin()))).andExpect(status().isOk()); }
}
