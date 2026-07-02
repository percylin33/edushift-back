package com.edushift.modules.attendance.controller;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.attendance.service.AttendanceQrService;
import com.edushift.modules.attendance.service.QrRenderer;
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
@WebMvcTest(AttendanceQrController.class)
class AttendanceQrControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AttendanceQrService qrService; @MockitoBean QrRenderer qrRenderer;
    @MockitoBean CurrentUserProvider currentUserProvider; @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken auth() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))); }
    @Test void getQr() throws Exception { mockMvc.perform(get("/students/{id}/attendance-qr", UUID.randomUUID()).with(authentication(auth()))).andExpect(status().isOk()); }
}
