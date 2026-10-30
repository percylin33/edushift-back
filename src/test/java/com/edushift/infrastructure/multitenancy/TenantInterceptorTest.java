package com.edushift.infrastructure.multitenancy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

	@Mock
	private CurrentUserProvider currentUserProvider;

	@Mock
	private TenantHostResolver tenantHostResolver;

	@AfterEach
	void cleanContext() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void rejectsAnAuthenticatedSessionOnAnotherSchoolSubdomain() {
		UUID sessionTenant = UUID.randomUUID();
		UUID hostTenant = UUID.randomUUID();
		MockHttpServletRequest request = request("/api/v1/students");
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("school-b", "school-b", hostTenant)));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(sessionTenant));
		SecurityContextHolder.getContext().setAuthentication(authenticated(sessionTenant));
		TenantContext.set(sessionTenant);

		assertThatThrownBy(() -> interceptor().preHandle(request, new MockHttpServletResponse(), new Object()))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("subdomain");
	}

	@Test
	void rejectsALoginSlugThatDiffersFromTheSchoolSubdomain() {
		UUID hostTenant = UUID.randomUUID();
		MockHttpServletRequest request = request("/api/v1/auth/login");
		request.addHeader("X-Tenant-Slug", "school-b");
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("school-a", "school-a", hostTenant)));

		assertThatThrownBy(() -> interceptor().preHandle(request, new MockHttpServletResponse(), new Object()))
				.isInstanceOf(ForbiddenException.class)
				.hasMessageContaining("Login tenant");
	}

	@Test
	void rejectsUnknownSchoolSubdomainsInsteadOfAcceptingTheSlugAsTenantIdentity() {
		MockHttpServletRequest request = request("/api/v1/tenants/me");
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("made-up", "made-up", null)));

		assertThatThrownBy(() -> interceptor().preHandle(request, new MockHttpServletResponse(), new Object()))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void allowsLoginWhenTheSubdomainAndLoginTenantMatch() throws Exception {
		UUID hostTenant = UUID.randomUUID();
		MockHttpServletRequest request = request("/api/v1/auth/login");
		request.addHeader("X-Tenant-Slug", "school-a");
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("school-a", "school-a", hostTenant)));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());

		boolean allowed = interceptor().preHandle(request, new MockHttpServletResponse(), new Object());

		assertThat(allowed).isTrue();
	}

	private TenantInterceptor interceptor() {
		return new TenantInterceptor(currentUserProvider, tenantHostResolver);
	}

	private static MockHttpServletRequest request(String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
		request.setServerName("school-a.edushift.pe");
		return request;
	}

	private static UsernamePasswordAuthenticationToken authenticated(UUID tenantId) {
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				UUID.randomUUID(), tenantId, "school-a", null);
		return UsernamePasswordAuthenticationToken.authenticated(
				principal, null, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}
}
