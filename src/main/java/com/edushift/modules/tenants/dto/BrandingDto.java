package com.edushift.modules.tenants.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Visual identity bag for a tenant. Mirrors the keys we read/write into
 * the {@code tenants.branding} jsonb column.
 *
 * <h3>Why a typed record over the raw {@code Map<String, Object>}</h3>
 * The persistence layer keeps the column as a free-form jsonb so we can
 * iterate on UI tweaks without schema migrations, but the contract with
 * the front needs to be explicit and validated. {@code BrandingDto} is
 * that contract: every key here has a known shape and a validation
 * rule. Front and back agree on this exact shape.
 *
 * <h3>Field rules</h3>
 * <ul>
 *   <li>{@code primaryColor} — CSS hex color (3 or 6 digits, with leading
 *       {@code #}). The front uses it to derive the full palette via
 *       {@code TenantThemeService.apply()}.</li>
 *   <li>{@code logoUrl} / {@code faviconUrl} / {@code loginBgUrl} —
 *       absolute URLs. Sprint 2 only accepts external URLs (no upload
 *       path yet); a future "branding upload" sprint will mint these
 *       from blob storage.</li>
 * </ul>
 *
 * Every field is optional; {@code null} on incoming PATCH requests
 * means "leave whatever the tenant already has". On {@code GET}
 * responses, missing keys serialize as omitted fields (Jackson
 * {@code @JsonInclude(NON_NULL)} via the global config).
 */
public record BrandingDto(

		@Pattern(
				regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
				message = "primaryColor must be a CSS hex color (e.g. #1e90ff or #18f)"
		)
		String primaryColor,

		@Size(max = 2048, message = "logoUrl exceeds 2048 chars")
		@Pattern(
				regexp = "^(https?://).+",
				message = "logoUrl must start with http:// or https://"
		)
		String logoUrl,

		@Size(max = 2048, message = "faviconUrl exceeds 2048 chars")
		@Pattern(
				regexp = "^(https?://).+",
				message = "faviconUrl must start with http:// or https://"
		)
		String faviconUrl,

		@Size(max = 2048, message = "loginBgUrl exceeds 2048 chars")
		@Pattern(
				regexp = "^(https?://).+",
				message = "loginBgUrl must start with http:// or https://"
		)
		String loginBgUrl

) {
}
