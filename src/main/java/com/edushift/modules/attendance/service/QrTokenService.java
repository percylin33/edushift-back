package com.edushift.modules.attendance.service;

import java.util.UUID;

/**
 * Mints and validates the JWT printed inside a student's QR code
 * (Sprint 6 / BE-6.2.B / ADR-6.1).
 *
 * <h3>Token shape</h3>
 * Same HS256 secret as the {@code auth} module
 * ({@code app.security.jwt.secret}). The {@code typ="attendance"} claim
 * is mandatory and lets {@link com.edushift.modules.auth.security.JwtAuthenticationFilter}
 * reject these tokens for {@code /auth/me} and other access-token-only
 * endpoints — they are designed to be parsed exclusively by the
 * attendance check-in flow.
 *
 * <p>Standard claims:
 * <ul>
 *   <li>{@code sub} — {@code student.publicUuid} (UUIDv4 string).</li>
 *   <li>{@code tenant_id} — {@code tenant.id} (internal UUIDv7 string),
 *       checked against the docente's current tenant in the service.</li>
 *   <li>{@code typ} — fixed string {@code "attendance"}.</li>
 *   <li>{@code iat} — issued-at, epoch seconds. Used for audit + UI
 *       (e.g. "QR emitido hace 3 dias").</li>
 *   <li>{@code jti} — random UUIDv4 to make every emission produce a
 *       distinct hash, even if {@code iat} ties.</li>
 *   <li>No {@code exp} — QRs do not expire by time; revocation lives in
 *       {@code student_attendance_qr.revoked_at}.</li>
 * </ul>
 *
 * <h3>Hash storage</h3>
 * The service never persists the raw JWT. We store SHA-256(token) hex
 * in {@code student_attendance_qr.token_hash}; the hash is the lookup
 * key during check-in. Same pattern as
 * {@code refresh_tokens.token_hash}.
 */
public interface QrTokenService {

	/**
	 * Issue a new QR token for a student.
	 *
	 * @param studentPublicUuid the student's public UUID — used as
	 *                          {@code sub}.
	 * @param tenantId          internal {@code tenants.id} — used as
	 *                          {@code tenant_id} claim.
	 * @return the raw JWT string and its SHA-256 hex hash.
	 * @throws IllegalArgumentException if any argument is {@code null}.
	 */
	IssuedQrToken issue(UUID studentPublicUuid, UUID tenantId);

	/**
	 * Parse the JWT scanned from a student credential and validate its
	 * shape.
	 *
	 * @return the decoded claims if signature is valid, the issuer
	 *         matches and {@code typ="attendance"}.
	 * @throws com.edushift.modules.attendance.exception.QrInvalidException
	 *         when the token is null/blank/malformed/wrong-type or has
	 *         an invalid signature.
	 */
	QrClaims parseAndValidate(String token);

	/**
	 * Compute the SHA-256 hex digest used as the lookup key in
	 * {@code student_attendance_qr.token_hash}. Idempotent and
	 * deterministic.
	 */
	String hash(String token);

	/**
	 * Result of {@link #issue(UUID, UUID)}: the raw token (used once at
	 * QR rendering time) and its persistent hash.
	 */
	record IssuedQrToken(String token, String tokenHash) {}

	/**
	 * Decoded {@code attendance} JWT claims.
	 */
	record QrClaims(UUID studentPublicUuid, UUID tenantId) {}
}
