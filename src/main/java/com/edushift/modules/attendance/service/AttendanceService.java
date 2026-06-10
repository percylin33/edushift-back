package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import java.util.List;
import java.util.UUID;

/**
 * Domain service of the {@code attendance} module
 * (Sprint 6 / BE-6.2.F-H).
 *
 * <p>Implements the five business primitives the controllers expose
 * to the FE:
 *
 * <ol>
 *   <li>{@link #openSession(CreateSessionRequest)} — open or recover an
 *       ACTIVE session for {@code (section, day, slot)}.</li>
 *   <li>{@link #closeSession(UUID)} — transition to CLOSED and
 *       materialize {@code ABSENT} records (ADR-6.6).</li>
 *   <li>{@link #checkIn(CheckInRequest)} — idempotent QR scan
 *       (ADR-6.3).</li>
 *   <li>{@link #listRecords(UUID)} — roster with virtual rows for
 *       enrolled students missing a record.</li>
 *   <li>{@link #updateRecord(UUID, UpdateRecordRequest)} — manual
 *       correction subject to the 24h teacher window (ADR-6.7).</li>
 * </ol>
 *
 * <p>All inputs are public UUIDs (UUIDv4); cross-tenant access is
 * blocked by Hibernate's {@code @TenantId} discriminator and surfaces
 * as {@code RESOURCE_NOT_FOUND} (anti-enumeration, see
 * {@code attendance.md} §7.2).
 */
public interface AttendanceService {

	/**
	 * Open a new ACTIVE attendance session, or return the one already
	 * open for {@code (section, occurredOn, slot)} (idempotent).
	 *
	 * @return the persisted (or pre-existing) session. The
	 *         {@code wasIdempotent} flag distinguishes the two cases.
	 */
	AttendanceSessionResponse openSession(CreateSessionRequest request);

	/**
	 * Close an ACTIVE session and materialize {@code ABSENT} records
	 * for every enrolled student that did not get scanned.
	 * Idempotent: calling on an already-CLOSED session returns the
	 * existing snapshot without mutating it.
	 */
	AttendanceSessionResponse closeSession(UUID sessionPublicUuid);

	/**
	 * Register a check-in scan. Idempotent on
	 * {@code (session, student)} — the second scan of the same student
	 * returns the existing row with {@code wasIdempotent=true}
	 * (ADR-6.3).
	 *
	 * @throws com.edushift.modules.attendance.exception.QrInvalidException
	 *         malformed / wrong-type / bad-signature token (401).
	 * @throws com.edushift.modules.attendance.exception.QrExpiredException
	 *         the QR is revoked (410).
	 * @throws com.edushift.shared.exception.ResourceNotFoundException
	 *         session or student do not belong to the bearer tenant (404).
	 *         Also thrown for {@code QR_TENANT_MISMATCH} as
	 *         anti-enumeration measure.
	 * @throws com.edushift.modules.attendance.exception.SessionClosedException
	 *         the session is CLOSED (409).
	 * @throws com.edushift.modules.attendance.exception.StudentNotEnrolledException
	 *         the student is not enrolled in the section on the
	 *         session's date (422).
	 * @throws com.edushift.modules.attendance.exception.ForcedStatusForbiddenException
	 *         non-admin sent a {@code forcedStatus} override (403).
	 */
	AttendanceRecordResponse checkIn(CheckInRequest request);

	/**
	 * Roster of a session: one item per enrolled student. Items
	 * lacking a real record show {@code status=null} (session is
	 * ACTIVE) or {@code status=ABSENT} (session is CLOSED).
	 */
	List<AttendanceRecordResponse> listRecords(UUID sessionPublicUuid);

	/**
	 * Manual edit of a record's {@code status} and/or {@code notes}.
	 * Subject to the 24h post-close window for {@code TEACHER}; no
	 * window for {@code TENANT_ADMIN} (ADR-6.7).
	 *
	 * @throws com.edushift.modules.attendance.exception.EditWindowExpiredException
	 *         when a TEACHER tries after 24h.
	 */
	AttendanceRecordResponse updateRecord(UUID recordPublicUuid, UpdateRecordRequest request);
}
