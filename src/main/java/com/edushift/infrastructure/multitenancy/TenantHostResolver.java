package com.edushift.infrastructure.multitenancy;

import com.edushift.modules.tenants.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves a school hostname to its catalog identity; the hostname never grants access by itself. */
@Component
public class TenantHostResolver {

	private static final String REQUEST_ATTRIBUTE = TenantHostResolver.class.getName() + ".resolution";
	private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
			"www", "app", "api", "admin", "auth", "mail", "support", "static", "cdn");

	private final TenantRepository tenantRepository;
	private final String baseDomain;

	public TenantHostResolver(
			TenantRepository tenantRepository,
			@Value("${app.tenant.base-domain:edushift.pe}") String baseDomain) {
		this.tenantRepository = tenantRepository;
		this.baseDomain = baseDomain.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.+|\\.+$", "");
	}

	public Optional<Resolution> resolve(HttpServletRequest request) {
		Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
		if (cached instanceof CachedResolution result) {
			return result.resolution();
		}

		Optional<Resolution> resolution = resolveHost(request.getServerName());
		request.setAttribute(REQUEST_ATTRIBUTE, new CachedResolution(resolution));
		return resolution;
	}

	private Optional<Resolution> resolveHost(String requestHost) {
		if (requestHost == null || requestHost.isBlank()) {
			return Optional.empty();
		}
		String host = requestHost.trim().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");

		if (host.endsWith(".localhost")) {
			String alias = host.substring(0, host.length() - ".localhost".length());
			if (isSingleDnsLabel(alias)) {
				return Optional.of(resolveTenant(alias, true));
			}
		}

		String suffix = "." + baseDomain;
		if (!host.endsWith(suffix)) {
			return Optional.empty();
		}
		String subdomain = host.substring(0, host.length() - suffix.length());
		if (!isSingleDnsLabel(subdomain) || RESERVED_SUBDOMAINS.contains(subdomain)) {
			return Optional.empty();
		}
		return Optional.of(resolveTenant(subdomain, false));
	}

	private Resolution resolveTenant(String hostnameSlug, boolean compactLocalAlias) {
		var tenant = compactLocalAlias
				? tenantRepository.findByHostnameAliasIgnoreCase(hostnameSlug)
				: tenantRepository.findBySlugIgnoreCase(hostnameSlug);
		return tenant
				.map(value -> new Resolution(hostnameSlug, value.getSlug(), value.getId()))
				.orElseGet(() -> new Resolution(hostnameSlug, hostnameSlug, null));
	}

	private static boolean isSingleDnsLabel(String value) {
		return value != null && value.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
	}

	/** A recognized tenant host, including its slug if it does not exist in the catalog. */
	public record Resolution(String requestedSlug, String canonicalSlug, UUID tenantId) {
		public boolean exists() {
			return tenantId != null;
		}
	}

	private record CachedResolution(Optional<Resolution> resolution) {
	}
}
