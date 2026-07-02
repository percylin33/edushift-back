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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Encrypted Google OAuth refresh-token storage for one user.
 *
 * <p>One active row per user at any time (enforced by the partial unique
 * index {@code uk_user_google_tokens_user_active} declared in
 * {@code V49__add_google_identity_and_tokens.sql}). When a user re-consents
 * with new scopes or a new client, the previous row is marked revoked and a
 * fresh one is inserted — preserving the full history of consents for
 * forensics / audit.
 *
 * <h3>Why {@code bytea} for the token</h3>
 * The Google refresh token is opaque ciphertext produced by
 * {@code com.edushift.infrastructure.integrations.google.GoogleTokenCrypto}
 * (AES-256-GCM with a 12-byte nonce prepended and a 16-byte tag appended,
 * 29 bytes minimum). Storing it as {@code bytea} keeps the field untyped at
 * the JDBC layer; the application layer always sees the raw ciphertext
 * (never the plaintext token) and is responsible for encryption.
 *
 * <h3>Why {@code varchar[]} for scopes</h3>
 * The scopes the user consented to are a closed set of OAuth scope strings
 * we know about at compile time. {@code varchar[]} matches the conventions
 * already used in {@link User#roles}.
 */
@Entity
@Table(
		name = "user_google_tokens",
		schema = "edushift",
		indexes = {
				@Index(name = "idx_user_google_tokens_tenant",
						columnList = "tenant_id"),
				@Index(name = "idx_user_google_tokens_user_created",
						columnList = "user_id, created_at")
		}
)
@Getter
@Setter
@NoArgsConstructor
public class UserGoogleIdentity extends TenantAwareEntity {

	/** Owner of the refresh token. FK -> {@code users.id} (internal UUIDv7). */
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** AES-GCM ciphertext of the Google refresh token (NEVER plain text). */
	@Column(name = "encrypted_refresh_token", nullable = false, updatable = false)
	private byte[] encryptedRefreshToken;

	/** Scopes the user consented to. Stored verbatim from Google's response. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "scopes", nullable = false, columnDefinition = "varchar(64)[]")
	private String[] scopes = new String[0];

	/** When the Google access token (derived from this refresh token) expires. */
	@Column(name = "expires_at")
	private Instant expiresAt;

	/** Set when the user / admin revokes this token; never cleared. */
	@Column(name = "revoked_at")
	private Instant revokedAt;

	/** Human-readable revoke reason — see DB CHECK constraint for allowed values. */
	@Column(name = "revoked_reason", length = 50)
	private String revokedReason;

	/** Convenience flag mirroring {@link #revokedAt} for call-site readability. */
	public boolean isRevoked() {
		return revokedAt != null;
	}

	/** Mark the row as revoked; idempotent. */
	public void revoke(String reason) {
		if (this.revokedAt == null) {
			this.revokedAt = Instant.now();
			this.revokedReason = reason;
		}
	}
}