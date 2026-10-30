package com.edushift.infrastructure.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class HttpTenantResolverTest {

	@Mock
	private CurrentUserProvider currentUserProvider;

	@Mock
	private TenantHostResolver tenantHostResolver;

	@Test
	void resolvesKnownSchoolSubdomainFromTheTenantCatalog() {
		UUID hostTenantId = UUID.randomUUID();
		MockHttpServletRequest request = request("school-a.edushift.pe");
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("school-a", "school-a", hostTenantId)));

		Optional<UUID> resolved = new HttpTenantResolver(currentUserProvider, tenantHostResolver).resolve(request);

		assertThat(resolved).contains(hostTenantId);
	}

	@Test
	void unknownSchoolSubdomainCannotFallBackToCallerSuppliedTenantId() {
		MockHttpServletRequest request = request("made-up.edushift.pe");
		request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
		when(tenantHostResolver.resolve(request)).thenReturn(Optional.of(
				new TenantHostResolver.Resolution("made-up", "made-up", null)));

		Optional<UUID> resolved = new HttpTenantResolver(currentUserProvider, tenantHostResolver).resolve(request);

		assertThat(resolved).isEmpty();
	}

	@Test
	void authenticatedSessionWinsOverHeaderAndHostTenant() {
		UUID sessionTenantId = UUID.randomUUID();
		MockHttpServletRequest request = request("school-b.edushift.pe");
		request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(sessionTenantId));

		Optional<UUID> resolved = new HttpTenantResolver(currentUserProvider, tenantHostResolver).resolve(request);

		assertThat(resolved).contains(sessionTenantId);
	}

	private static MockHttpServletRequest request(String host) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students");
		request.setServerName(host);
		return request;
	}
}
