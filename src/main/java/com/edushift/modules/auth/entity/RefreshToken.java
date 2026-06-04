package com.edushift.modules.auth.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

/**
 * A refresh token issued to a {@link User}, used to mint new access tokens
 * via {@code POST /auth/refresh}.
 *
 * <h3>Hashing</h3>
 * The {@link #tokenHash} is a SHA-256 hex digest of the raw token. The raw
 * value lives only in the response payload to the client — it is never
 * persisted. Lookups happen by hash, so a DB leak does not enable replay.
 *
 * <h3>Rotation lineage</h3>
 * Every successful {@code /refresh} mints a new token whose
 * {@link #parentTokenId} points to the previous one. The previous token gets
 * {@code revoked_at = now()} with reason {@code ROTATED}. This forms a chain
 * that supports theft detection: if a {@code revoked} token is presented
 * again, the entire chain is compromised and revoked
 * (reason {@code COMPROMISED}).
 *
 * <h3>FK to user</h3>
 * Stored as a raw {@link UUID} (not a {@code @ManyToOne} relationship) so
 * that the auth module remains decoupled from the {@code User} aggregate at
 * the persistence layer and can be queried efficiently in admin views.
 */
@Entity
@Table(
		name = "refresh_tokens",
		schema = "edushift",
		indexes = {
				@Index(name = "idx_refresh_tokens_user_active",
						columnList = "user_id, tenant_id"),
				@Index(name = "idx_refresh_tokens_expires_at",
						columnList = "expires_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true,
		of = {"userId", "expiresAt", "revokedAt", "revokedReason"})
@SQLDelete(sql = "UPDATE edushift.refresh_tokens "
		+ "SET deleted = true, deleted_at = NOW(), updated_at = NOW() "
		+ "WHERE id = ?")
public class RefreshToken extends TenantAwareEntity {

	/** SHA-256 hex digest of the raw token (64 lowercase hex chars). */
	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	private String tokenHash;

	/** Internal id of the owning {@link User}. */
	@Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID userId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** When the token was revoked, if at all. {@code null} for active tokens. */
	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "revoked_reason", length = 50)
	private RevocationReason revokedReason;

	/** Self FK to the predecessor in the rotation chain; {@code null} for the first issue. */
	@Column(name = "parent_token_id", columnDefinition = "uuid")
	private UUID parentTokenId;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	// ------------------------------------------------------------------------
	// Behavior
	// ------------------------------------------------------------------------

	/** True when {@link #revokedAt} is set. Idempotent-safe. */
	public boolean isRevoked() {
		return revokedAt != null;
	}

	/** True when {@link #expiresAt} is in the past. */
	public boolean isExpired() {
		return expiresAt != null && expiresAt.isBefore(Instant.now());
	}

	/** Returns true when the token is currently usable. */
	public boolean isActive() {
		return !isRevoked() && !isExpired();
	}

	/**
	 * Marks the token as revoked. Idempotent: a token already revoked keeps
	 * its original {@code revokedAt} / {@code revokedReason} (caller should
	 * check {@link #isRevoked()} first if they need to flag re-use).
	 */
	public void revoke(RevocationReason reason) {
		if (revokedAt != null) {
			return;
		}
		this.revokedAt = Instant.now();
		this.revokedReason = reason;
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
