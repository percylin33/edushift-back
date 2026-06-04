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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * Root tenant record (one row = one school / institution).
 *
 * <h3>Why this is NOT {@code TenantAwareEntity}</h3>
 * The {@code tenants} table is the catalog of tenants — it IS the discriminator
 * source. It lives in the global schema, has no {@code tenant_id} column, and
 * is queried directly by the auth flow during login (slug → tenant_id).
 *
 * <h3>Sprint 1 scope (BE-1.3)</h3>
 * Minimal columns required for login: {@code id}, {@code slug}, {@code status},
 * {@code name}. Sprint 2 ({@code Tenants module}) extends this entity with
 * {@code customDomain}, {@code settings} (jsonb), {@code plan}, {@code branding},
 * {@code featureFlags}, etc.
 *
 * <p>The DB columns omitted here ({@code public_uuid}, {@code custom_domain},
 * {@code settings}) have safe defaults at the DB level — Hibernate's
 * {@code ddl-auto=validate} only checks that entity columns ⊆ DB columns.
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
@ToString(callSuper = true, of = {"slug", "name", "status"})
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

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private TenantStatus status = TenantStatus.PENDING;

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
		if (slug != null) {
			slug = slug.trim().toLowerCase();
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
