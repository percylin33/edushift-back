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
 *       {@code GET /attendance-qr} and student {@code POST /me/qr/reveal}.
 *       Returns the existing printable token when an active row has
 *       {@code token_plain}. Issues (and revokes the previous hash-only
 *       row) only when the student has no reprintable credential.
 *       Lost cards use {@link #rotate(UUID)}.</li>
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
	 * Return the active printable token, or issue the first one.
	 * Does not rotate a reprintable credential.
	 *
	 * @param studentPublicUuid the alumno (must exist in current tenant).
	 * @return the raw JWT to render and the persisted info.
	 * @throws com.edushift.shared.exception.ResourceNotFoundException
	 *         the student is not in the current tenant.
	 */
	IssuedQr getOrIssueQr(UUID studentPublicUuid);

	/**
	 * Admin-only explicit rotation (lost printed card). Revokes the
	 * active row and issues a new token.
	 */
	IssuedQr rotate(UUID studentPublicUuid);

	/**
	 * Read-only metadata for the student's currently active QR row.
	 * Returns {@code null} when the alumno has never been issued a QR
	 * (so the FE can render a "Generar credencial" CTA on first use).
	 */
	AttendanceQrInfo getInfo(UUID studentPublicUuid);

	/**
	 * DEBT-STUDENT-PRIVACY (Fase 0.4): same as
	 * {@link #getOrIssueQr(UUID)} with a caller-side ownership check.
	 */
	IssuedQr getOrIssueQrForCaller(UUID studentPublicUuid);

	/**
	 * Raw printable token for the active row, or {@code null} when
	 * missing / pre-V100 (hash-only). Read-only; never rotates.
	 */
	String peekActiveTokenForCaller(UUID studentPublicUuid);

	/**
	 * DEBT-STUDENT-PRIVACY (Fase 0.4): same as {@link #getInfo(UUID)}
	 * but with the caller-side ownership check.
	 */
	AttendanceQrInfo getInfoForCaller(UUID studentPublicUuid);

	/**
	 * Composite return of {@link #getOrIssueQr(UUID)} /
	 * {@link #rotate(UUID)}: the raw JWT (used once, by the renderer)
	 * plus the persisted info envelope.
	 */
	record IssuedQr(String jwt, AttendanceQrInfo info) {
	}
}
