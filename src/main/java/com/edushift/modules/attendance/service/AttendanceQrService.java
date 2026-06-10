package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import java.util.UUID;

/**
 * Lifecycle of a student's printable QR credential
 * (Sprint 6 / BE-6.3).
 *
 * <h3>Three operations, three semantics</h3>
 * <ul>
 *   <li>{@link #getOrIssueQr(UUID)} — drives
 *       {@code GET /attendance-qr}. Returns the JWT to encode in the
 *       PNG/SVG. <strong>Always re-issues</strong> a fresh JWT and
 *       persists its hash, revoking the previous active row if any.
 *       This is the documented behaviour: every "Generar credencial"
 *       press from the admin panel produces a brand-new printable
 *       QR. The previous printed QR, if any, becomes invalid the
 *       moment a new one is issued. The FE uses {@link #getInfo(UUID)}
 *       (no rotation) to decide whether to show the "Generar" or
 *       "Reimprimir" CTA before the user commits.</li>
 *   <li>{@link #rotate(UUID)} — drives
 *       {@code POST /attendance-qr/rotate}. Same DB effect as
 *       {@code getOrIssueQr}, but exposed as a deliberate action
 *       (admin-only) so we can attach a distinct audit event in
 *       BE-6.4. The FE wires this to "Credencial perdida" CTAs.</li>
 *   <li>{@link #getInfo(UUID)} — drives
 *       {@code GET /attendance-qr/info}. Read-only metadata, never
 *       mutates the QR row.</li>
 * </ul>
 *
 * <h3>Token raw vs hash</h3>
 * The persisted column is {@code token_hash} (SHA-256 hex), not the
 * raw JWT. {@link #getOrIssueQr(UUID)} therefore returns the raw
 * token in {@link IssuedQr}; the renderer encodes it into the QR
 * image and the controller discards it after writing the response
 * body. Same hardening as {@code refresh_tokens}.
 */
public interface AttendanceQrService {

	/**
	 * Issue a fresh JWT for the student, revoking the previous active
	 * row (if any) with reason {@code ROTATED}.
	 *
	 * @param studentPublicUuid the alumno (must exist in current tenant).
	 * @return the raw JWT to render and the persisted info.
	 * @throws com.edushift.shared.exception.ResourceNotFoundException
	 *         the student is not in the current tenant.
	 */
	IssuedQr getOrIssueQr(UUID studentPublicUuid);

	/**
	 * Admin-only explicit rotation. Same DB effect as
	 * {@link #getOrIssueQr(UUID)}; kept as a separate entry point so
	 * controllers can attach a distinct {@code @PreAuthorize} rule and
	 * audit event.
	 */
	IssuedQr rotate(UUID studentPublicUuid);

	/**
	 * Read-only metadata for the student's currently active QR row.
	 * Returns {@code null} when the alumno has never been issued a QR
	 * (so the FE can render a "Generar credencial" CTA on first use).
	 */
	AttendanceQrInfo getInfo(UUID studentPublicUuid);

	/**
	 * Composite return of {@link #getOrIssueQr(UUID)} /
	 * {@link #rotate(UUID)}: the raw JWT (used once, by the renderer)
	 * plus the persisted info envelope.
	 */
	record IssuedQr(String jwt, AttendanceQrInfo info) {
	}
}
