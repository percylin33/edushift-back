package com.edushift.shared.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds SPA URLs that identify a school. Local/dev uses
 * {@code ?tenant={slug}}; production may also use subdomains, but the
 * query param remains the canonical pin for emailed links.
 */
@Component
public class FrontendLinks {

	private final String frontendUrl;

	public FrontendLinks(@Value("${app.frontend.url:http://localhost:4201}") String frontendUrl) {
		this.frontendUrl = frontendUrl;
	}

	public String base() {
		String raw = frontendUrl == null || frontendUrl.isBlank()
				? "http://localhost:4201"
				: frontendUrl.trim();
		while (raw.endsWith("/")) {
			raw = raw.substring(0, raw.length() - 1);
		}
		return raw;
	}

	/** Founder onboarding — school does not exist yet, so no tenant slug. */
	public String schoolSetup(String token) {
		return base() + "/auth/setup-school?token=" + enc(token);
	}

	public String schoolLogin(String tenantSlug) {
		return withTenant("/auth/login", tenantSlug, null);
	}

	public String userInvitation(String token, String tenantSlug) {
		return withTenant("/invitation/" + enc(token), tenantSlug, null);
	}

	public String passwordReset(String token, String tenantSlug) {
		return withTenant("/auth/reset-password", tenantSlug, "token=" + enc(token));
	}

	private String withTenant(String path, String tenantSlug, String extraQuery) {
		StringBuilder url = new StringBuilder(base()).append(path);
		boolean hasQuery = false;
		String slug = tenantSlug == null ? "" : tenantSlug.trim().toLowerCase();
		if (!slug.isEmpty()) {
			url.append("?tenant=").append(enc(slug));
			hasQuery = true;
		}
		if (extraQuery != null && !extraQuery.isBlank()) {
			url.append(hasQuery ? '&' : '?').append(extraQuery);
		}
		return url.toString();
	}

	private static String enc(String value) {
		if (value == null) return "";
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
