package com.edushift.modules.auth.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
 * Application user. Scoped to a single {@code tenant} (school / institution)
 * and identified externally by {@link #publicUuid} — the internal
 * {@code id} (UUIDv7) is never exposed via the API.
 *
 * <h3>Identifiers</h3>
 * <ul>
 *   <li><strong>{@code id}</strong> (inherited) — internal UUIDv7, time-ordered,
 *       used as foreign key inside the database.</li>
 *   <li><strong>{@code publicUuid}</strong> — random UUIDv4 exposed via REST
 *       endpoints; decouples the wire contract from the internal PK and
 *       prevents enumeration / timing attacks against UUIDv7 ordering.</li>
 * </ul>
 *
 * <h3>Multi-tenancy</h3>
 * Tenant isolation is provided by {@link TenantAwareEntity}: Hibernate's
 * {@code @TenantId} discriminator auto-filters queries by {@code tenant_id}
 * and the column is immutable after insert. Email uniqueness is therefore
 * <em>per tenant</em> (a partial unique index on
 * {@code (tenant_id, lower(email))} is declared in the Flyway migration).
 *
 * <h3>Soft delete</h3>
 * {@code BaseEntity} contributes the {@code deleted} flag and the global
 * {@code @SQLRestriction("deleted = false")} filter. This class adds
 * {@link #deletedAt} (forensic timestamp) and a custom {@code @SQLDelete}
 * that updates both columns atomically when {@code repository.delete(user)}
 * is called.
 *
 * <h3>Sensitive data</h3>
 * {@link #passwordHash} is annotated with {@link JsonIgnore} and excluded
 * from {@code toString}; it must never appear in DTOs, log lines or
 * response payloads.
 */
@Entity
@Table(
		name = "users",
		schema = "edushift",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_users_public_uuid", columnNames = "public_uuid")
		},
		indexes = {
				@Index(name = "idx_users_tenant_status", columnList = "tenant_id, status"),
				@Index(name = "idx_users_last_login", columnList = "last_login_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"publicUuid", "email", "status"})
@SQLDelete(sql = "UPDATE edushift.users "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class User extends TenantAwareEntity {

	/** External, stable identifier exposed to clients (UUIDv4). */
	@Column(name = "public_uuid", nullable = false, updatable = false,
			unique = true, columnDefinition = "uuid")
	private UUID publicUuid;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	/**
	 * Lowercased and trimmed before persistence. Uniqueness is enforced at the
	 * database level by a partial unique index on {@code (tenant_id, lower(email))}
	 * scoped to non-deleted rows.
	 */
	@Column(name = "email", nullable = false, length = 254)
	private String email;

	/**
	 * BCrypt/Argon2 hash. <strong>Never serialized</strong>; excluded from
	 * {@code toString} (not listed in {@code @ToString(of = ...)}) and from
	 * JSON output ({@link JsonIgnore}).
	 */
	@JsonIgnore
	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "phone", length = 32)
	private String phone;

	@Column(name = "avatar_url", length = 512)
	private String avatarUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private UserStatus status = UserStatus.PENDING_VERIFICATION;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	@Column(name = "mfa_enabled", nullable = false)
	private boolean mfaEnabled = false;

	/**
	 * BCrypt-hashed TOTP secret (RFC 6238). Stored hashed so a DB leak
	 * does not let an attacker forge codes. Cleared when MFA is disabled.
	 * Sprint 17 / BE-17.2.
	 */
	@Column(name = "mfa_secret_hash", length = 255)
	private String mfaSecretHash;

	/**
	 * JSONB array of BCrypt hashes of the user's recovery codes. At
	 * enrollment, 10 codes are generated; the user is shown them once
	 * and we keep only the hashes. A code is consumed (replaced by null)
	 * after successful use. Sprint 17 / BE-17.2.
	 */
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
	@Column(name = "mfa_recovery_codes_hash", columnDefinition = "jsonb")
	private java.util.List<String> mfaRecoveryCodesHash;

	/** When the user enrolled MFA. Used for audit + re-enrollment detection. */
	@Column(name = "mfa_enrolled_at")
	private Instant mfaEnrolledAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	/**
	 * Role names assigned to this user. Mirrors the {@link UserRole} enum
	 * values stored as plain strings — see the enum's javadoc for the
	 * rationale (table-less catalog for Sprint 2, relational promotion in
	 * Sprint 3 without breaking the JWT shape).
	 *
	 * <p>Persisted as a Postgres {@code varchar[]} via Hibernate 6's
	 * {@code @JdbcTypeCode(SqlTypes.ARRAY)}. The Java type is the raw
	 * {@code String[]} that Hibernate accepts natively; type-safe access
	 * goes through {@link #getRoleSet} / {@link #setRoleSet} /
	 * {@link #addRole} / {@link #hasRole}.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "roles", nullable = false, columnDefinition = "varchar[]")
	private String[] roles = new String[0];

	/**
	 * Timestamp of soft deletion. Populated by the {@code @SQLDelete} statement
	 * and by {@link #markDeleted()}; cleared by {@link #restore()}.
	 */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Stable Google subject ({@code sub} claim) when this user has linked
	 * a Google account. {@code null} for legacy email/password users that
	 * have not yet gone through {@code POST /auth/google}.
	 *
	 * <p>Uniqueness is enforced PER TENANT on non-deleted rows by the
	 * {@code uk_users_tenant_google_subject} partial unique index declared
	 * in {@code V49__add_google_identity_and_tokens.sql}. The {@code sub}
	 * itself is opaque (lowercase, max 128 chars) — we don't try to
	 * normalize it.
	 */
	@Column(name = "google_subject", length = 128)
	private String googleSubject;

	/**
	 * DEBT-AUTH-7: temporary lockout timestamp. When a user accumulates
	 * 5 failed login attempts within 15 minutes, this field is set to
	 * {@code now() + 15 minutes}. {@link com.edushift.modules.auth.service.LoginAttemptService}
	 * checks this field on every login attempt.
	 *
	 * <p>Semantics:
	 * <ul>
	 *   <li>{@code null} → no active lock.</li>
	 *   <li>{@code future} → account locked; login rejected until the timestamp passes.</li>
	 *   <li>{@code past} → lock has expired; cleared lazily on the next successful authentication.</li>
	 * </ul>
	 */
	@Column(name = "temporarily_locked_until")
	private Instant temporarilyLockedUntil;

	@PrePersist
	private void onPrePersist() {
		if (publicUuid == null) {
			publicUuid = UUID.randomUUID();
		}
		if (status == null) {
			status = UserStatus.PENDING_VERIFICATION;
		}
		normalizeEmail();
	}

	@PreUpdate
	private void onPreUpdate() {
		normalizeEmail();
	}

	private void normalizeEmail() {
		if (email != null) {
			email = email.trim().toLowerCase();
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

	/** Convenience accessor for display purposes. */
	public String fullName() {
		if (firstName == null && lastName == null) {
			return "";
		}
		if (firstName == null) {
			return lastName;
		}
		if (lastName == null) {
			return firstName;
		}
		return firstName + " " + lastName;
	}

	/** Records a successful authentication. */
	public void recordSuccessfulLogin() {
		this.lastLoginAt = Instant.now();
	}

	/** Promotes a {@code PENDING_VERIFICATION} account to {@code ACTIVE}. */
	public void markEmailVerified() {
		this.emailVerified = true;
		if (this.status == UserStatus.PENDING_VERIFICATION) {
			this.status = UserStatus.ACTIVE;
		}
	}

	// ---------------------------------------------------------------------------
	// Role helpers (type-safe view over the raw varchar[] column)
	// ---------------------------------------------------------------------------

	/**
	 * Returns the role names as a defensive copy. Always non-null; an
	 * empty set means "no privileged endpoints accessible". Used by
	 * {@code AuthService.issueSession} / {@code AuthService.login} to
	 * populate the {@code roles} JWT claim.
	 */
	public Set<String> getRoleNames() {
		if (roles == null || roles.length == 0) {
			return Set.of();
		}
		// LinkedHashSet preserves the order from the DB array — useful for
		// deterministic JWT claim shape (helps debugging + cache hits).
		return new LinkedHashSet<>(Arrays.asList(roles));
	}

	/** Type-safe view: drops names that don't match any {@link UserRole}. */
	public Set<UserRole> getRoleSet() {
		if (roles == null || roles.length == 0) {
			return Set.of();
		}
		Set<UserRole> result = new LinkedHashSet<>();
		for (String name : roles) {
			UserRole role = UserRole.fromName(name);
			if (role != null) result.add(role);
		}
		return result;
	}

	/** Replace the user's roles in one shot. {@code null} clears the roles. */
	public void setRoleSet(Set<UserRole> newRoles) {
		if (newRoles == null || newRoles.isEmpty()) {
			this.roles = new String[0];
			return;
		}
		this.roles = newRoles.stream()
				.filter(java.util.Objects::nonNull)
				.map(Enum::name)
				.toArray(String[]::new);
	}

	/** Idempotent: adds a role if not already present, no-op otherwise. */
	public void addRole(UserRole role) {
		if (role == null) return;
		if (hasRole(role)) return;
		String[] current = roles == null ? new String[0] : roles;
		String[] next = new String[current.length + 1];
		System.arraycopy(current, 0, next, 0, current.length);
		next[current.length] = role.name();
		this.roles = next;
	}

	/** Membership check. */
	public boolean hasRole(UserRole role) {
		if (role == null || roles == null) return false;
		for (String name : roles) {
			if (role.name().equals(name)) return true;
		}
		return false;
	}

}
