package com.edushift.modules.notifications.email;

/**
 * Visual branding injected into transactional email layouts.
 *
 * @param tenantName    school display name (or "EduShift" for pre-tenant mail)
 * @param logoUrl       absolute HTTPS logo URL; may be null
 * @param primaryColor  CSS hex for CTA / accent (e.g. {@code #0e7490})
 * @param slug          tenant slug for footer copy; may be null
 */
public record EmailBrandingContext(
		String tenantName,
		String logoUrl,
		String primaryColor,
		String slug
) {
	public static final String DEFAULT_PRIMARY = "#0e7490";
	public static final String DEFAULT_NAME = "EduShift";

	public static EmailBrandingContext edushiftDefault(String defaultLogoUrl) {
		return new EmailBrandingContext(
				DEFAULT_NAME,
				blankToNull(defaultLogoUrl),
				DEFAULT_PRIMARY,
				null);
	}

	public String primaryOrDefault() {
		return primaryColor == null || primaryColor.isBlank() ? DEFAULT_PRIMARY : primaryColor;
	}

	public String nameOrDefault() {
		return tenantName == null || tenantName.isBlank() ? DEFAULT_NAME : tenantName;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
