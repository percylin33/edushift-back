package com.edushift.modules.tenants.branding;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.branding.PlatformPdfBrand;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bytes of the current tenant logo, when {@code branding.logoUrl} is reachable.
 * Callers fall back to the platform isotipo when this returns {@code null}.
 */
@Component
@RequiredArgsConstructor
public class CurrentTenantLogoLoader {

	private final TenantRepository tenantRepository;

	public byte[] schoolLogoOrNull() {
		return PlatformPdfBrand.fetchLogo(logoUrl());
	}

	private String logoUrl() {
		UUID tenantId = TenantContext.current().orElse(null);
		if (tenantId == null) {
			return null;
		}
		return tenantRepository.findById(tenantId)
				.map(CurrentTenantLogoLoader::logoUrlOf)
				.orElse(null);
	}

	private static String logoUrlOf(Tenant tenant) {
		Map<String, Object> branding = tenant.getBranding();
		if (branding == null || branding.isEmpty()) {
			return null;
		}
		Object logoUrl = branding.get("logoUrl");
		if (logoUrl instanceof String s && !s.isBlank()) {
			return s;
		}
		return null;
	}
}
