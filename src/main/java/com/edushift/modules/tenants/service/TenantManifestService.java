package com.edushift.modules.tenants.service;

import com.edushift.modules.tenants.dto.TenantManifest;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.shared.web.FrontendLinks;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Builds the install metadata from the public-safe tenant identity projection. */
@Service
public class TenantManifestService {
	private static final String PLATFORM_ICON = "/branding/icons/isotipo.png";
	private final TenantService tenantService;
	private final FrontendLinks frontendLinks;

	public TenantManifestService(TenantService tenantService, FrontendLinks frontendLinks) {
		this.tenantService = tenantService;
		this.frontendLinks = frontendLinks;
	}

	public TenantManifest findBySlug(String slug, String requestOrigin) {
		TenantSummary tenant = tenantService.findBySlug(slug);
		String requestedBase = normalizedOrigin(requestOrigin).orElseGet(frontendLinks::base);
		boolean localHost = frontendLinks.isLocalhostOrigin(requestedBase);
		String base = localHost
				? frontendLinks.tenantBase(tenant.slug(), requestedBase)
				: requestedBase;
		String tenantQuery = localHost ? "" : "?tenant=" + encode(tenant.slug());
		List<TenantManifest.Icon> icons = new ArrayList<>();
		String schoolLogo = tenant.branding() == null ? null : tenant.branding().logoUrl();
		validImageUrl(schoolLogo).ifPresent(url -> addIconVariants(icons, url, imageType(url)));
		addIconVariants(icons, base + PLATFORM_ICON, "image/png");

		String name = tenant.name() == null || tenant.name().isBlank() ? "EduShift" : tenant.name().trim();
		return new TenantManifest(
				base + "/" + tenantQuery,
				name,
				shortName(name),
				"Plataforma escolar de " + name,
				base + "/auth/login" + tenantQuery,
				base + "/",
				"standalone",
				"portrait",
				"#ffffff",
				"#4f46e5",
				"es-PE",
				List.of("education", "productivity"),
				List.copyOf(icons)
		);
	}

	private static String shortName(String name) {
		String compact = name.trim().replaceAll("\\s+", " ");
		return compact.length() <= 12 ? compact : compact.substring(0, 12).trim();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static void addIconVariants(List<TenantManifest.Icon> icons, String src, String type) {
		if ("image/svg+xml".equals(type)) {
			icons.add(new TenantManifest.Icon(src, "any", type, "any"));
			return;
		}
		icons.add(new TenantManifest.Icon(src, "192x192", type, "any"));
		icons.add(new TenantManifest.Icon(src, "512x512", type, "any"));
	}

	private static String imageType(String url) {
		String path = URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT);
		if (path.endsWith(".svg")) return "image/svg+xml";
		if (path.endsWith(".webp")) return "image/webp";
		if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
		if (path.endsWith(".avif")) return "image/avif";
		return "image/png";
	}

	private static Optional<String> normalizedOrigin(String rawOrigin) {
		if (rawOrigin == null || rawOrigin.isBlank()) return Optional.empty();
		try {
			URI origin = URI.create(rawOrigin.trim());
			if (!("https".equalsIgnoreCase(origin.getScheme())
					|| "http".equalsIgnoreCase(origin.getScheme()))
					|| origin.getHost() == null
					|| origin.getRawUserInfo() != null
					|| (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
					|| origin.getRawQuery() != null
					|| origin.getRawFragment() != null) {
				return Optional.empty();
			}
			return Optional.of(origin.getScheme() + "://" + origin.getRawAuthority());
		} catch (IllegalArgumentException invalidOrigin) {
			return Optional.empty();
		}
	}

	private static Optional<String> validImageUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) return Optional.empty();
		try {
			URI image = URI.create(rawUrl.trim());
			if (!("https".equalsIgnoreCase(image.getScheme())
					|| "http".equalsIgnoreCase(image.getScheme()))
					|| image.getHost() == null
					|| image.getRawUserInfo() != null) {
				return Optional.empty();
			}
			return Optional.of(image.toString());
		} catch (IllegalArgumentException invalidUrl) {
			return Optional.empty();
		}
	}
}
