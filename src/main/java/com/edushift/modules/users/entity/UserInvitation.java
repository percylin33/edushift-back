package com.edushift.modules.users.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * Pending invitation for a new user to join a tenant.
 *
 * <h3>Identifiers</h3>
 * <ul>
 *   <li><strong>{@code id}</strong> — internal UUIDv7, FK target inside the DB.</li>
 *   <li><strong>{@code publicUuid}</strong> — exposed to TENANT_ADMIN tooling
 *       (cancel-invitation flow takes a publicUuid path variable).</li>
 *   <li><strong>{@code token}</strong> — the secret payload mailed to (or
 *       handed to) the invitee. Globally unique because the accept endpoint
 *       is public and tenant-less. Treated as a credential — never log it.</li>
 * </ul>
 *
 * <h3>Lifecycle (computed, not stored)</h3>
 * The status — {@code PENDING / ACCEPTED / CANCELLED / EXPIRED} — is derived
 * from three timestamps:
 * <pre>
 *   if (acceptedAt != null)                     → ACCEPTED
 *   else if (cancelledAt != null)               → CANCELLED
 *   else if (expiresAt is in the past)          → EXPIRED
 *   else                                         → PENDING
 * </pre>
 * Keeping the truth in the timestamps (rather than a redundant {@code status}
 * column) eliminates a class of "the flag and the dates disagree" bugs that
 * are common in invitation systems. The trade-off is that "list pending"
 * queries need to express the predicate three columns at a time — see
 * {@code UserInvitationRepository}.
 *
 * <h3>Token uniqueness scope</h3>
 * Tokens live in a global namespace (partial unique index on the table).
 * The accept endpoint runs without {@code TenantContext}, so per-tenant
 * uniqueness would be insufficient: a collision across two tenants would
 * leave the public lookup ambiguous. Tokens are 32+ url-safe-base64 chars
 * (~192 bits of entropy) so collisions are statistically irrelevant; the
 * unique index is the belt-and-suspenders guarantee.
 */
@Entity
@Table(
		name = "user_invitations",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_user_invitations_public_uuid", columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_user_invitations_tenant_pending", columnList = "tenant_id, expires_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "email", "expiresAt"})
@SQLDelete(sql = "UPDATE edushift.user_invitations "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class UserInvitation extends TenantAwareEntity {

	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	/** Recipient profile — snapshotted so the public preflight endpoint needs zero joins. */
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	/** Roles to grant when this invitation is accepted. Mirrors {@code users.roles}. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "roles", nullable = false, columnDefinition = "varchar[]")
	private String[] roles = new String[0];

	/**
	 * Globally-unique opaque secret. Treat as a credential: never log it,
	 * never include it in {@link #toString()}.
	 */
	@Column(name = "token", nullable = false, updatable = false, length = 64)
	private String token;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Free-form jsonb side-channel callers can use to carry domain ids
	 * that should be reacted on at accept time. Sprint 4 / BE-4.6 uses
	 * the convention {@code { "teacherId": "<uuid>" }} so the teachers
	 * module's listener can atomically link {@code teacher.user_id} to
	 * the freshly-created user inside the same transaction.
	 *
	 * <p>Keys are convention-driven; nothing here is enforced by the
	 * invitations module itself — listeners agree on the schema.</p>
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata = new HashMap<>();

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (email != null) {
			email = email.trim().toLowerCase();
		}
		if (metadata == null) {
			metadata = new HashMap<>();
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

	// ---------------------------------------------------------------------------
	// Lifecycle helpers
	// ---------------------------------------------------------------------------

	public boolean isAccepted() {
		return acceptedAt != null;
	}

	public boolean isCancelled() {
		return cancelledAt != null;
	}

	public boolean isExpired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean isPending(Instant now) {
		return !isAccepted() && !isCancelled() && !isExpired(now);
	}

	public void markAccepted(Instant when) {
		this.acceptedAt = when;
	}

	public void markCancelled(Instant when) {
		this.cancelledAt = when;
	}

	// ---------------------------------------------------------------------------
	// Role helpers (type-safe view over the raw varchar[])
	// ---------------------------------------------------------------------------

	public Set<String> getRoleNames() {
		if (roles == null || roles.length == 0) {
			return Set.of();
		}
		return new LinkedHashSet<>(Arrays.asList(roles));
	}

	public void setRoleNames(Set<String> names) {
		if (names == null || names.isEmpty()) {
			this.roles = new String[0];
			return;
		}
		this.roles = names.toArray(new String[0]);
	}
}
