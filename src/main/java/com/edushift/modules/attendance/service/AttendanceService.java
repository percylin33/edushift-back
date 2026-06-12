package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionListItemResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.ManualCheckInRequest;
import com.edushift.modules.attendance.dto.ScanCheckInRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
 *   <li>{@link #listSessions(ListSessionsFilter, Pageable)} —
 *       tenant-scoped listing with optional date / section / slot /
 *       status filters and pagination (Sprint 6 / BE-6.7).</li>
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
	 * Register a check-in for a student picked by name + filters
	 * (Sprint 6 / BE-6.8 manual fallback). Auto-resolves the target
	 * session by looking up the student's current ACTIVE enrollment
	 * and {@code findOrOpen}-ing a session for
	 * ({@code section}, {@link ManualCheckInRequest#effectiveOccurredOn()},
	 * {@link ManualCheckInRequest#effectiveSlot(java.time.Instant)}).
	 *
	 * <p>Idempotent on {@code (resolved session, student)}: re-marking
	 * the same student in the same resolved session returns the
	 * existing row with {@code wasIdempotent=true}.
	 *
	 * @throws com.edushift.shared.exception.ResourceNotFoundException
	 *         the student does not belong to the bearer tenant (404).
	 * @throws com.edushift.modules.attendance.exception.StudentNoActiveEnrollmentException
	 *         the student exists but has no ACTIVE enrollment to
	 *         resolve a section from (422).
	 * @throws com.edushift.modules.attendance.exception.ForcedStatusForbiddenException
	 *         non-admin sent a {@code forcedStatus} override (403).
	 */
	AttendanceRecordResponse manualCheckIn(ManualCheckInRequest request);

	/**
	 * Session-less QR scan (BE-6.8.b). Validates the QR exactly like
	 * {@link #checkIn(CheckInRequest)} and then auto-resolves the
	 * target session from the student's current ACTIVE enrollment —
	 * same {@code findOrOpen} flow used by {@link #manualCheckIn(ManualCheckInRequest)}.
	 *
	 * <p>This is the canonical entry point for the "auxiliary at the
	 * school entrance" use case: the auxiliary attends students from
	 * many sections at once and pre-opening a session for each is
	 * impractical. Idempotent on {@code (resolved session, student)}.</p>
	 *
	 * @throws com.edushift.modules.attendance.exception.QrInvalidException
	 *         malformed / wrong-type / bad-signature token (401).
	 * @throws com.edushift.modules.attendance.exception.QrExpiredException
	 *         the QR is revoked (410).
	 * @throws com.edushift.shared.exception.ResourceNotFoundException
	 *         tenant mismatch (anti-enumeration 404).
	 * @throws com.edushift.modules.attendance.exception.StudentNoActiveEnrollmentException
	 *         the student has no ACTIVE enrollment (422).
	 */
	AttendanceRecordResponse scanCheckIn(ScanCheckInRequest request);

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

	/**
	 * Tenant-scoped listing of sessions (Sprint 6 / BE-6.7).
	 *
	 * <p>Drives the {@code GET /v1/attendance/sessions} endpoint
	 * consumed by {@code AttendanceSessionsListPage} (FE-6.2).
	 * Filters are AND-combined and skip-on-null: any combination of
	 * {@code dateFrom}/{@code dateTo}, {@code sectionPublicUuid},
	 * {@code slot} and {@code status} is supported. Results are
	 * ordered by {@code occurredOn DESC, startsAt DESC} at the
	 * repository level — the controller does not need to re-sort.</p>
	 *
	 * <p>The four counters are populated for {@code CLOSED} sessions
	 * (cheap: one {@code count} group-by per row) and left
	 * {@code null} for {@code ACTIVE} ones (the FE's "only pending"
	 * toggle doesn't need them; counting now would defeat the
	 * list-cheap goal).</p>
	 *
	 * @param filter all filters are optional
	 * @param pageable standard Spring Data pagination
	 * @return a non-null page; empty when the tenant has no sessions
	 *         matching the filter
	 */
	Page<AttendanceSessionListItemResponse> listSessions(
			ListSessionsFilter filter, Pageable pageable);

	/**
	 * Optional filter shape for {@link #listSessions(ListSessionsFilter, Pageable)}.
	 *
	 * <p>All fields are nullable (skipped on null). We pass a
	 * dedicated record rather than {@code @RequestParam}s in the
	 * controller to keep the service signature stable across future
	 * filter additions.</p>
	 */
	record ListSessionsFilter(
			UUID sectionPublicUuid,
			LocalDate from,
			LocalDate to,
			com.edushift.modules.attendance.entity.AttendanceSessionSlot slot,
			com.edushift.modules.attendance.entity.AttendanceSessionStatus status
	) {
		/** Canonical empty filter — used by the controller when no param is present. */
		public static ListSessionsFilter empty() {
			return new ListSessionsFilter(null, null, null, null, null);
		}
	}
}
