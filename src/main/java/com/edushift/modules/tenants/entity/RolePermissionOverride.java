package com.edushift.modules.tenants.entity;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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
 * Per-tenant override for the platform-default role → {@code LMS_*}
 * authority mapping (D1 / F0.5, QA plan 2026-07-02).
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>{@link #granted} = {@code true} → the platform default is
 *       replaced with this explicit grant; even if the platform default
 *       for the same authority is unset, the user gets it.</li>
 *   <li>{@link #granted} = {@code false} → the platform default is
 *       suppressed; even if the platform default for the same authority
 *       is set, the user does NOT get it.</li>
 *   <li>Absence of a row → platform default applies.</li>
 * </ul>
 *
 * <p>This is a <b>total replacement per role</b> by design
 * (decision Q2 closed as option (c) in
 * {@code docs/qa/11-decisiones-pendientes.md} §Q2). It deliberately
 * avoids the additive / subtractive ambiguity that complicates auditing.
 * Removing an override (soft delete) returns the role to the platform
 * default; the deleted row is preserved for history.</p>
 *
 * <h3>Authority whitelist</h3>
 * The DB CHECK constraint mirrors
 * {@code shared.security.LmsAuthorities}; adding a new constant
 * requires a follow-up migration that extends the IN-list and the
 * Java enum.
 *
 * <h3>Tenant scope</h3>
 * Rows are immutable after creation (a row cannot move tenants).
 * SUPER_ADMIN is intentionally excluded — platform-tier roles are
 * immutable and never customised per tenant.
 */
@Entity
@Table(
		name = "role_permission_overrides",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_role_permission_overrides_public_uuid",
						columnNames = "public_uuid"
				)
		},
		indexes = {
				@Index(
						name = "ix_role_permission_overrides_tenant_role",
						columnList = "tenant_id, role"
				)
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"role", "authority", "granted"})
@SQLDelete(sql = "UPDATE edushift.role_permission_overrides "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class RolePermissionOverride extends AuditableEntity {

	/** External, stable identifier exposed to clients (UUID v4). */
	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	/**
	 * Tenant this override applies to. Immutability is enforced at the
	 * controller layer (we never accept {@code tenant_id} from a request
	 * body — it always comes from the authenticated session).
	 */
	@Column(name = "tenant_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID tenantId;

	/** Coarse role this override applies to. SUPER_ADMIN never appears. */
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, updatable = false, length = 32)
	private UserRole role;

	/** Granular authority (one of the {@code LMS_*} constants). */
	@Column(name = "authority", nullable = false, updatable = false, length = 64)
	private String authority;

	/**
	 * Whether the authority is granted ({@code true}) or revoked
	 * ({@code false}). See class-level javadoc for semantics.
	 */
	@Column(name = "granted", nullable = false)
	private boolean granted;

	/**
	 * User who wrote this override. Used by the audit log; not exposed
	 * in the public DTO (it's redundant with {@code updated_by} on the
	 * base class — kept separate for fast index-only reads on the
	 * history view).
	 */
	@Column(name = "granted_by_user_id", nullable = false, columnDefinition = "uuid")
	private UUID grantedByUserId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
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
