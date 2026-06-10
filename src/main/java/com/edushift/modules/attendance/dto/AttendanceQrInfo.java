package com.edushift.modules.attendance.dto;

import com.edushift.modules.attendance.entity.QrRevokedReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle metadata of a student's QR credential
 * (Sprint 6 / BE-6.3).
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code POST /api/v1/students/{publicUuid}/attendance-qr/rotate}
 *       — admin explicitly invalidates the previous credential and
 *       issues a new one. Both {@code previousRevokedAt} and
 *       {@code previousRevokedReason} reflect the previous active
 *       row when the alumno already had one; {@code null} when this
 *       is the first issuance.</li>
 *   <li>{@code GET /api/v1/students/{publicUuid}/attendance-qr/info}
 *       — read-only view that the FE uses to decide whether to show
 *       a "Generar credencial" or "Reimprimir credencial" CTA.
 *       {@code issuedAt} reflects the current active row; both
 *       {@code previous*} fields are {@code null} on this read.</li>
 * </ul>
 *
 * <h3>Security</h3>
 * Never exposes {@code token_hash} or the raw JWT — those are
 * persisted (or transient, respectively) for internal use only.
 *
 * @param studentPublicUuid       the alumno whose QR this info
 *                                describes.
 * @param issuedAt                {@code iat} of the currently active
 *                                row.
 * @param previousRevokedAt       when the previous active row was
 *                                revoked. {@code null} on the first
 *                                issuance and on info reads.
 * @param previousRevokedReason   reason logged on rotation. {@code null}
 *                                on the first issuance and on info reads.
 */
public record AttendanceQrInfo(
		UUID studentPublicUuid,
		Instant issuedAt,
		Instant previousRevokedAt,
		QrRevokedReason previousRevokedReason
) {
}
