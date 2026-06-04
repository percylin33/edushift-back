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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

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

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	/**
	 * Timestamp of soft deletion. Populated by the {@code @SQLDelete} statement
	 * and by {@link #markDeleted()}; cleared by {@link #restore()}.
	 */
	@Column(name = "deleted_at")
	private Instant deletedAt;

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

}
