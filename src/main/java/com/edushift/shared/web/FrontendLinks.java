package com.edushift.shared.web;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds SPA URLs that identify a school. Local development uses a
 * dash-free {@code <tenant>.localhost} subdomain; other environments retain
 * their configured legacy URL format until their domain routing is migrated.
 */
@Component
public class FrontendLinks {

	private final String frontendUrl;

	public FrontendLinks(@Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
		this.frontendUrl = frontendUrl;
	}

	public String base() {
		String raw = frontendUrl == null || frontendUrl.isBlank()
				? "http://localhost:4200"
				: frontendUrl.trim();
		while (raw.endsWith("/")) {
			raw = raw.substring(0, raw.length() - 1);
		}
		return raw;
	}

	/** Tenant-aware frontend origin, using compact aliases for localhost. */
	public String tenantBase(String tenantSlug) {
		return tenantBase(tenantSlug, base());
	}

	/** Tenant-aware origin based on a validated frontend origin. */
	public String tenantBase(String tenantSlug, String frontendOrigin) {
		String raw = frontendOrigin == null || frontendOrigin.isBlank() ? base() : trimTrailingSlash(frontendOrigin.trim());
		try {
			URI uri = URI.create(raw);
			String host = uri.getHost();
			if (!isLocalhostHost(host) || tenantSlug == null || tenantSlug.isBlank()) return raw;

			String alias = tenantSlug.trim().toLowerCase().replace("-", "");
			if (!alias.matches("[a-z0-9][a-z0-9]{0,62}")) return raw;
			String authority = alias + ".localhost" + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
			String path = uri.getRawPath() == null ? "" : uri.getRawPath();
			return uri.getScheme() + "://" + authority + path;
		}
		catch (IllegalArgumentException invalidOrigin) {
			return raw;
		}
	}

	public boolean isLocalhostOrigin(String origin) {
		try {
			return isLocalhostHost(URI.create(origin).getHost());
		}
		catch (IllegalArgumentException invalidOrigin) {
			return false;
		}
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
		String frontendBase = base();
		boolean localSubdomain = tenantSlug != null && isLocalhostOrigin(frontendBase);
		StringBuilder url = new StringBuilder(
				localSubdomain ? tenantBase(tenantSlug, frontendBase) : frontendBase).append(path);
		boolean hasQuery = false;
		String slug = tenantSlug == null ? "" : tenantSlug.trim().toLowerCase();
		if (!slug.isEmpty() && !localSubdomain) {
			url.append("?tenant=").append(enc(slug));
			hasQuery = true;
		}
		if (extraQuery != null && !extraQuery.isBlank()) {
			url.append(hasQuery ? '&' : '?').append(extraQuery);
		}
		return url.toString();
	}

	private static boolean isLocalhostHost(String host) {
		return host != null && ("localhost".equalsIgnoreCase(host)
				|| "127.0.0.1".equals(host)
				|| "[::1]".equals(host)
				|| host.toLowerCase().endsWith(".localhost"));
	}

	private static String trimTrailingSlash(String value) {
		while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
		return value;
	}

	private static String enc(String value) {
		if (value == null) return "";
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
