package com.edushift.modules.notifications.email;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link EmailBrandingContext} from the current tenant's
 * {@code branding} jsonb (logoUrl, primaryColor) with EduShift fallbacks.
 */
@Component
@RequiredArgsConstructor
public class EmailBrandingResolver {

	private final TenantRepository tenantRepository;

	@Value("${app.notifications.email.default-logo-url:}")
	private String defaultLogoUrl;

	/** Branding for the current {@link TenantContext}, or EduShift defaults. */
	public EmailBrandingContext current() {
		UUID tenantId = TenantContext.current().orElse(null);
		if (tenantId == null) {
			return EmailBrandingContext.edushiftDefault(defaultLogoUrl);
		}
		return forTenantId(tenantId);
	}

	public EmailBrandingContext forTenantId(UUID tenantId) {
		if (tenantId == null) {
			return EmailBrandingContext.edushiftDefault(defaultLogoUrl);
		}
		return tenantRepository.findById(tenantId)
				.map(this::fromTenant)
				.orElseGet(() -> EmailBrandingContext.edushiftDefault(defaultLogoUrl));
	}

	private EmailBrandingContext fromTenant(Tenant tenant) {
		Map<String, Object> branding = tenant.getBranding();
		String logo = asString(branding == null ? null : branding.get("logoUrl"));
		if (logo == null || logo.isBlank()) {
			logo = blankToNull(defaultLogoUrl);
		}
		String primary = asString(branding == null ? null : branding.get("primaryColor"));
		return new EmailBrandingContext(
				tenant.getName(),
				logo,
				primary,
				tenant.getSlug());
	}

	private static String asString(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
