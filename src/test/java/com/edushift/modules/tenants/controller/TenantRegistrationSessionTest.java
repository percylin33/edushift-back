package com.edushift.modules.tenants.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.edushift.modules.admin.invitations.SchoolInvitationService;
import com.edushift.modules.auth.controller.CookieHelper;
import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.auth.dto.UserSummary;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.tenants.dto.RegisterTenantRequest;
import com.edushift.modules.tenants.service.TenantManifestService;
import com.edushift.modules.tenants.service.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class TenantRegistrationSessionTest {
    private TenantService service;
    private TenantController controller;
    private final RegisterTenantRequest request = new RegisterTenantRequest(
            "Colegio QA", "colegioqa", "founder@example.test", "SchoolAccess2026!", "Founder", "QA", "synthetic-invitation-token");
    @BeforeEach void setUp() {
        service = mock(TenantService.class);
        CookieHelper cookies = new CookieHelper();
        ReflectionTestUtils.setField(cookies, "secure", false);
        ReflectionTestUtils.setField(cookies, "sameSite", "Strict");
        ReflectionTestUtils.setField(cookies, "cookieDomain", "");
        controller = new TenantController(service, mock(TenantManifestService.class), mock(SchoolInvitationService.class), cookies);
    }
    @Test void registrationSetsHostOnlyHttpOnlyCookiesInsteadOfExposingTokensInBody() {
        var user = new UserSummary(UUID.randomUUID(), "Founder QA", "founder@example.test", null, UserStatus.ACTIVE);
        when(service.register(request)).thenReturn(new AuthResponse("access-fixture", "refresh-fixture", "Bearer", 900L, user));
        var response = new MockHttpServletResponse();
        var result = controller.register(request, response);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().accessToken()).isNull();
        assertThat(result.getBody().refreshToken()).isNull();
        assertThat(result.getBody().user()).isEqualTo(user);
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2).allSatisfy(cookie -> {
            assertThat(cookie).contains("HttpOnly", "Path=/", "SameSite=Strict");
            assertThat(cookie).doesNotContain("Domain=");
        });
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("edushift_access_token=access-fixture;"));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(cookie -> cookie.startsWith("edushift_refresh_token=refresh-fixture;"));
    }
    @Test void failedRegistrationDoesNotSetAuthenticationCookies() {
        when(service.register(request)).thenThrow(new IllegalStateException("registration rejected"));
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> controller.register(request, response)).hasMessageContaining("rejected");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }
}
