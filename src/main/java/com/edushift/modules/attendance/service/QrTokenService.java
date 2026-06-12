package com.edushift.modules.attendance.service;

import java.util.UUID;

/**
 * Mints and validates the opaque token printed inside a student's QR
 * credential (Sprint 6 / BE-6.2.B / ADR-6.1).
 *
 * <h3>Token shape</h3>
 * 12-character base32 short ID using an OCR-friendly alphabet
 * ({@code 23456789ABCDEFGHJKMNPQRSTVWXYZ}, no {@code 0/O}, {@code 1/I/L}).
 * 60 bits of entropy — collisions are statistically impossible (~1 in
 * 10^18) and brute-forcing a valid token at any realistic request rate
 * would take billions of years.
 *
 * <p>The token is intentionally <em>opaque</em>: it carries no embedded
 * claims. The previous design used an HS256 JWT with student + tenant
 * claims, which generated payloads of 250-380 characters and forced the
 * resulting QR into very dense versions (10+). Switching to a short ID
 * drops the QR to version 1-2 (~21-25 modules per side) so a phone
 * camera can decode it from twice the distance and under poor lighting.
 *
 * <h3>Validation</h3>
 * The student + tenant are recovered server-side via a DB lookup on the
 * SHA-256 hash of the scanned token ({@code student_attendance_qr.token_hash}).
 * Tenant scoping is enforced by Hibernate's {@code @TenantId} filter on
 * {@code StudentAttendanceQr}, so a token issued by tenant A can never
 * be matched when scanning under tenant B — the row is simply invisible.
 *
 * <h3>Hash storage</h3>
 * The service never persists the raw token. We store SHA-256(token) hex
 * in {@code student_attendance_qr.token_hash}; the hash is the lookup
 * key during check-in. Same pattern as {@code refresh_tokens.token_hash}.
 */
public interface QrTokenService {

	/**
	 * Issue a new QR token for a student.
	 *
	 * @param studentPublicUuid the student's public UUID — recorded by
	 *                          the caller in {@code student_attendance_qr.student_id}.
	 * @param tenantId          internal {@code tenants.id} — used only
	 *                          for audit / logging today; tenant
	 *                          isolation at validation time is enforced
	 *                          by Hibernate's {@code @TenantId} filter
	 *                          on the entity row.
	 * @return the raw token string (12 chars) and its SHA-256 hex hash.
	 * @throws IllegalArgumentException if any argument is {@code null}.
	 */
	IssuedQrToken issue(UUID studentPublicUuid, UUID tenantId);

	/**
	 * Validate that a scanned string has the expected shape. Does NOT
	 * touch the database — the caller is responsible for resolving the
	 * student via {@code findActiveByTokenHash(hash(token))}.
	 *
	 * @throws com.edushift.modules.attendance.exception.QrInvalidException
	 *         when the token is null, blank, or does not match the
	 *         12-character alphabet pattern. A malformed token is
	 *         rejected without a DB roundtrip.
	 */
	void validateFormat(String token);

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
}
