package com.edushift.modules.tenants.mapper;

import com.edushift.modules.tenants.dto.BrandingDto;
import com.edushift.modules.tenants.dto.TenantResponse;
import com.edushift.modules.tenants.dto.TenantSummary;
import com.edushift.modules.tenants.dto.UpdateTenantRequest;
import com.edushift.modules.tenants.entity.Tenant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bidirectional translation between {@link Tenant} (entity) and the
 * tenants-module DTOs. Hand-written instead of MapStruct because:
 *
 * <ul>
 *   <li>Two of the fields ({@code branding}, {@code featureFlags},
 *       {@code settings}) are {@code Map<String, Object>} <-> typed-DTO
 *       conversions that need explicit logic anyway.</li>
 *   <li>The merge step in {@link #applyUpdate} carries non-trivial
 *       semantics (null = leave as-is for primitives, field-level merge
 *       for branding, replace-wholesale for opaque maps). Encoding
 *       that in MapStruct annotations is more brittle than just
 *       writing it out.</li>
 *   <li>Zero runtime annotation processors keeps incremental builds
 *       fast and the dependency surface small.</li>
 * </ul>
 */
@Component
public class TenantMapper {

	// ---------------------------------------------------------------------------
	// Entity -> DTO
	// ---------------------------------------------------------------------------

	/**
	 * Public-safe projection. See {@link TenantSummary} for what we
	 * deliberately omit and why.
	 */
	public TenantSummary toSummary(Tenant tenant) {
		return new TenantSummary(
				tenant.getPublicUuid(),
				tenant.getName(),
				tenant.getSlug(),
				tenant.getStatus(),
				toBrandingDto(tenant.getBranding())
		);
	}

	/**
	 * Authenticated projection. Returned by {@code GET /tenants/me}
	 * and as the result of a successful {@code PATCH /tenants/me}.
	 */
	public TenantResponse toResponse(Tenant tenant) {
		return new TenantResponse(
				tenant.getPublicUuid(),
				tenant.getName(),
				tenant.getSlug(),
				tenant.getCustomDomain(),
				tenant.getStatus(),
				tenant.getPlan(),
				tenant.getTrialEndsAt(),
				toBrandingDto(tenant.getBranding()),
				safeCopy(tenant.getSettings()),
				safeCopy(tenant.getFeatureFlags()),
				tenant.getMaxStudents(),
				tenant.getMaxTeachers(),
				tenant.getCreatedAt(),
				tenant.getUpdatedAt()
		);
	}

	// ---------------------------------------------------------------------------
	// DTO -> Entity (in-place merge)
	// ---------------------------------------------------------------------------

	/**
	 * Apply a partial update onto a managed entity. The caller is
	 * responsible for the surrounding transaction; this method only
	 * mutates the in-memory state field by field.
	 *
	 * <h3>Per-field merge semantics</h3>
	 * <ul>
	 *   <li>Primitive / scalar fields ({@code name}, {@code customDomain},
	 *       {@code maxStudents}, {@code maxTeachers}) — overwrite when
	 *       the request carries a non-null value, leave as-is otherwise.</li>
	 *   <li>{@code branding} — merge at the {@link BrandingDto} field
	 *       level. The front can change just {@code primaryColor}
	 *       without resending {@code logoUrl}.</li>
	 *   <li>{@code settings}, {@code featureFlags} — replaced wholesale
	 *       when present. They're opaque to the back, so we can't
	 *       reason about which keys to keep / drop on a partial update.
	 *       Documented in {@link UpdateTenantRequest}.</li>
	 * </ul>
	 */
	public void applyUpdate(UpdateTenantRequest patch, Tenant tenant) {
		if (patch.name() != null) {
			tenant.setName(patch.name());
		}
		if (patch.customDomain() != null) {
			// Trim + lowercase to match the partial unique index on lower(custom_domain).
			tenant.setCustomDomain(patch.customDomain().trim().toLowerCase());
		}
		if (patch.branding() != null) {
			tenant.setBranding(mergeBranding(tenant.getBranding(), patch.branding()));
		}
		if (patch.settings() != null) {
			tenant.setSettings(safeCopy(patch.settings()));
		}
		if (patch.featureFlags() != null) {
			tenant.setFeatureFlags(safeCopy(patch.featureFlags()));
		}
		if (patch.maxStudents() != null) {
			tenant.setMaxStudents(patch.maxStudents());
		}
		if (patch.maxTeachers() != null) {
			tenant.setMaxTeachers(patch.maxTeachers());
		}
	}

	// ---------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------

	/**
	 * Project the raw branding map to the typed DTO. Keys we don't
	 * know about are silently dropped — the typed contract is the
	 * source of truth for what the front sees, and an unknown key in
	 * the column is treated as "data we used to surface but no longer
	 * do" (acceptable forward/backward compat behavior).
	 */
	private BrandingDto toBrandingDto(Map<String, Object> branding) {
		if (branding == null || branding.isEmpty()) {
			return new BrandingDto(null, null, null, null);
		}
		return new BrandingDto(
				asString(branding.get("primaryColor")),
				asString(branding.get("logoUrl")),
				asString(branding.get("faviconUrl")),
				asString(branding.get("loginBgUrl"))
		);
	}

	/**
	 * Field-level branding merge: start from the existing map (so
	 * unknown forward-compat keys survive), then overwrite only the
	 * keys the client actually sent.
	 */
	private Map<String, Object> mergeBranding(Map<String, Object> current, BrandingDto patch) {
		Map<String, Object> merged = current == null ? new HashMap<>() : new HashMap<>(current);
		if (patch.primaryColor() != null) merged.put("primaryColor", patch.primaryColor());
		if (patch.logoUrl()      != null) merged.put("logoUrl",      patch.logoUrl());
		if (patch.faviconUrl()   != null) merged.put("faviconUrl",   patch.faviconUrl());
		if (patch.loginBgUrl()   != null) merged.put("loginBgUrl",   patch.loginBgUrl());
		return merged;
	}

	/**
	 * Defensive copy: the entity holds Hibernate-managed state, the
	 * DTO is a snapshot. Callers should never get a reference to the
	 * managed map (mutating it outside a transaction silently bypasses
	 * dirty checking).
	 */
	private Map<String, Object> safeCopy(Map<String, Object> map) {
		return map == null ? new HashMap<>() : new HashMap<>(map);
	}

	private String asString(Object value) {
		return value instanceof String s ? s : null;
	}

}
