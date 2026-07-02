package com.edushift.modules.auth.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Tracks a JWT-issued password-reset token (Sprint 17 / BE-17.1).
 *
 * <h3>Why a separate table when the JWT is self-contained?</h3>
 * <ul>
 *   <li><strong>Idempotency</strong> — once consumed, the {@link #usedAt}
 *       timestamp enforces single-use semantics. A JWT alone cannot express
 *       "consumed" without server-side state.</li>
 *   <li><strong>Superseding</strong> — when a user requests a new reset
 *       before the previous token expired, we mark the older token's
 *       {@link #supersededAt} so audit logs can answer "was this link
 *       still valid when the user clicked it?".</li>
 *   <li><strong>Tenant isolation</strong> — the tenant_id column enables the
 *       cross-tenant IT ({@code ResetPasswordTenantIsolationIT}) that proves
 *       a token issued in tenant A cannot be redeemed in tenant B.</li>
 *   <li><strong>Forensic trail</strong> — {@link #requestIp} survives even
 *       after the token is consumed/expired; useful for incident review.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *  request → PENDING (usedAt = null, supersededAt = null)
 *     ↓ consume(...)
 *   CONSUMED (usedAt = now())
 *     OR
 *   SUPERSEDED (supersededAt = now())  // when a newer reset is requested
 * </pre>
 *
 * The {@code expiresAt} is enforced both in the JWT (cryptographic) and here
 * (database) so we have a server-side second source of truth.
 */
@Entity
@Table(
		name = "password_reset_tokens",
		schema = "edushift",
		indexes = {
				@Index(name = "idx_password_reset_tokens_user",
						columnList = "user_id, tenant_id"),
				@Index(name = "idx_password_reset_tokens_expires_at",
						columnList = "expires_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"jti", "userId", "expiresAt", "usedAt", "supersededAt"})
@SQLDelete(sql = "UPDATE edushift.password_reset_tokens "
		+ "SET deleted = true, updated_at = NOW() "
		+ "WHERE id = ?")
public class PasswordResetToken extends TenantAwareEntity {

	/**
	 * JWT ID of the reset token. Stored as the canonical id so lookups by
	 * {@code jti} can be answered from the index without parsing the JWT.
	 */
	@Column(name = "jti", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID jti;

	/** Internal id of the user the reset is for. */
	@Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
	private UUID userId;

	/** Absolute expiration timestamp (issued_at + ttl, default ttl = 1h). */
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** Non-null once the token has been redeemed. */
	@Column(name = "used_at")
	private Instant usedAt;

	/** Non-null when a newer reset superseded this one. */
	@Column(name = "superseded_at")
	private Instant supersededAt;

	/** IP that initiated the reset request, captured for forensic review. */
	@Column(name = "request_ip", length = 64)
	private String requestIp;

	// ------------------------------------------------------------------------
	// Behavior
	// ------------------------------------------------------------------------

	/** True when the token is still redeemable. */
	public boolean isUsable(Instant now) {
		return usedAt == null && supersededAt == null && expiresAt.isAfter(now);
	}

	/** Atomically mark this token as redeemed. Idempotent. */
	public void markUsed(Instant when) {
		if (usedAt == null) {
			this.usedAt = when;
		}
	}

	/** Atomically mark this token as superseded by a newer request. Idempotent. */
	public void markSuperseded(Instant when) {
		if (supersededAt == null) {
			this.supersededAt = when;
		}
	}
}