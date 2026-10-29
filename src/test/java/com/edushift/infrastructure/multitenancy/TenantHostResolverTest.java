package com.edushift.infrastructure.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class TenantHostResolverTest {

	@Mock
	private TenantRepository tenantRepository;

	@Test
	void resolvesOneTenantSubdomainThroughTheCatalog() {
		UUID tenantId = UUID.randomUUID();
		Tenant tenant = new Tenant();
		tenant.setId(tenantId);
		tenant.setSlug("school-a");
		when(tenantRepository.findBySlugIgnoreCase("school-a")).thenReturn(Optional.of(tenant));
		MockHttpServletRequest request = request("school-a.edushift.pe");

		var resolution = new TenantHostResolver(tenantRepository, "edushift.pe").resolve(request);

		assertThat(resolution).isPresent();
		assertThat(resolution.get().canonicalSlug()).isEqualTo("school-a");
		assertThat(resolution.get().tenantId()).isEqualTo(tenantId);
		verify(tenantRepository).findBySlugIgnoreCase("school-a");
	}

	@Test
	void marksUnknownSubdomainAsUnresolvedInsteadOfTreatingItsSlugAsIdentity() {
		when(tenantRepository.findBySlugIgnoreCase("unknown-school")).thenReturn(Optional.empty());

		var resolution = new TenantHostResolver(tenantRepository, "edushift.pe")
				.resolve(request("unknown-school.edushift.pe"));

		assertThat(resolution).isPresent();
		assertThat(resolution.get().requestedSlug()).isEqualTo("unknown-school");
		assertThat(resolution.get().exists()).isFalse();
	}

	@Test
	void doesNotInterpretPlatformHostsOrRootDomainAsTenantSlugs() {
		TenantHostResolver resolver = new TenantHostResolver(tenantRepository, "edushift.pe");

		assertThat(resolver.resolve(request("edushift.pe"))).isEmpty();
		assertThat(resolver.resolve(request("app.edushift.pe"))).isEmpty();
		assertThat(resolver.resolve(request("api.edushift.pe"))).isEmpty();
		verify(tenantRepository, never()).findBySlugIgnoreCase(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void resolvesLocalAliasUsingCatalogAliasLookup() {
		UUID tenantId = UUID.randomUUID();
		Tenant tenant = new Tenant();
		tenant.setId(tenantId);
		tenant.setSlug("school-a");
		when(tenantRepository.findByHostnameAliasIgnoreCase("schoola")).thenReturn(Optional.of(tenant));

		var resolution = new TenantHostResolver(tenantRepository, "edushift.pe")
				.resolve(request("schoola.localhost"));

		assertThat(resolution).isPresent();
		assertThat(resolution.get().canonicalSlug()).isEqualTo("school-a");
		assertThat(resolution.get().tenantId()).isEqualTo(tenantId);
	}

	private static MockHttpServletRequest request(String host) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants/me");
		request.setServerName(host);
		return request;
	}
}
