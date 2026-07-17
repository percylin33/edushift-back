package com.edushift.modules.tenants.entity;

import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Root tenant record (one row = one school / institution).
 *
 * <h3>Why this is NOT {@code TenantAwareEntity}</h3>
 * The {@code tenants} table is the catalog of tenants — it IS the discriminator
 * source. It lives in the global schema, has no {@code tenant_id} column, and
 * is queried directly by the auth flow during login (slug → tenant_id).
 *
 * <h3>Mapped columns</h3>
 * After Sprint 2 (V7) this entity covers the full tenant catalog surface
 * needed by the SaaS:
 * <ul>
 *   <li><strong>Identity</strong>: {@code publicUuid}, {@code name},
 *       {@code slug}, {@code customDomain}.</li>
 *   <li><strong>Lifecycle</strong>: {@code status} (PENDING/ACTIVE/...).</li>
 *   <li><strong>Billing</strong>: {@code plan} + {@code trialEndsAt}.</li>
 *   <li><strong>Branding & flags</strong>: {@code branding} + {@code featureFlags}
 *       as schema-less {@code jsonb}. The contract with consumers is
 *       enforced at the DTO layer, not here, so the database doesn't
 *       have to migrate every time the front wants a new color or
 *       toggle.</li>
 *   <li><strong>Soft caps</strong>: {@code maxStudents}, {@code maxTeachers}.
 *       NULL means "use plan default".</li>
 * </ul>
 *
 * <h3>Why the JSON fields use {@code Map<String, Object>}</h3>
 * Hibernate 6's {@code @JdbcTypeCode(SqlTypes.JSON)} round-trips this
 * to {@code jsonb} via Jackson without any custom type. The same
 * pattern is used by {@code AuditLog.metadata}; keeping the convention
 * uniform across modules makes the persistence layer predictable.
 */
@Entity
@Table(
		name = "tenants",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_tenants_public_uuid", columnNames = "public_uuid")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"slug", "name", "status", "plan"})
@SQLDelete(sql = "UPDATE edushift.tenants "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class Tenant extends AuditableEntity {

	/** External, stable identifier exposed to clients (UUIDv4). Required by DB. */
	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	/**
	 * URL-friendly identifier. Lowercased before persistence; the DB enforces a
	 * partial unique index on {@code lower(slug)} for non-deleted rows.
	 */
	@Column(name = "slug", nullable = false, length = 80)
	private String slug;

	/** Optional vanity domain (e.g. {@code app.micolegio.edu.pe}). */
	@Column(name = "custom_domain", length = 200)
	private String customDomain;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private TenantStatus status = TenantStatus.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan", nullable = false, length = 30)
	private TenantPlan plan = TenantPlan.TRIAL;

	/**
	 * FK to {@code platform_plans.id} (Sprint 15 / V54_1). The DB column is
	 * {@code NOT NULL}, so every new tenant must resolve its plan row before
	 * the INSERT. {@link com.edushift.modules.tenants.service.impl.TenantServiceImpl}
	 * looks up the matching {@code platform_plans} row by {@link TenantPlan#getCode()}
	 * during self-signup and stores the UUID here.
	 */
	@Column(name = "plan_id", nullable = false, columnDefinition = "uuid")
	private UUID planId;

	@Column(name = "trial_ends_at")
	private Instant trialEndsAt;

	/**
	 * Free-form branding bag (primaryColor, logoUrl, faviconUrl, loginBgUrl, ...).
	 * The DTO layer ({@code BrandingDto}) defines the typed contract;
	 * this map is the storage layer.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "branding", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> branding = new HashMap<>();

	/**
	 * Free-form per-tenant feature flags ({@code {flag: bool}}). Reads
	 * default to "off" when a key is missing — the absence of a flag
	 * is intentional and equivalent to a disabled feature.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "feature_flags", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> featureFlags = new HashMap<>();

	/**
	 * Optional hard cap on the number of students. {@code null} means
	 * "use the plan's default cap" (resolved by the service layer).
	 */
	@Column(name = "max_students")
	private Integer maxStudents;

	/** See {@link #maxStudents}. */
	@Column(name = "max_teachers")
	private Integer maxTeachers;

	@Column(name = "settings", nullable = false, columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> settings = new HashMap<>();

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = TenantStatus.PENDING;
		}
		if (plan == null) {
			plan = TenantPlan.TRIAL;
		}
		if (slug != null) {
			slug = slug.trim().toLowerCase();
		}
		if (branding == null) {
			branding = new HashMap<>();
		}
		if (featureFlags == null) {
			featureFlags = new HashMap<>();
		}
		if (settings == null) {
			settings = new HashMap<>();
		}
	}

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}

}
