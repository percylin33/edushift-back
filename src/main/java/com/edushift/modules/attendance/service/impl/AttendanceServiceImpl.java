package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.attendance.audit.AttendanceAuditLogger;
import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionListItemResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.ManualCheckInRequest;
import com.edushift.modules.attendance.dto.ScanCheckInRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.entity.StudentAttendanceQr;
import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.modules.attendance.exception.EditWindowExpiredException;
import com.edushift.modules.attendance.exception.ForcedStatusForbiddenException;
import com.edushift.modules.attendance.exception.QrExpiredException;
import com.edushift.modules.attendance.exception.QrInvalidException;
import com.edushift.modules.attendance.exception.SessionAlreadyOpenException;
import com.edushift.modules.attendance.exception.SessionClosedException;
import com.edushift.modules.attendance.exception.StudentNoActiveEnrollmentException;
import com.edushift.modules.attendance.exception.StudentNotEnrolledException;
import com.edushift.modules.attendance.mapper.AttendanceMapper;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.modules.attendance.service.AttendanceUserCache;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.tenants.service.TenantSettingsService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AttendanceService} implementation
 * (Sprint 6 / BE-6.2.F-H).
 *
 * <p>All write operations run in a tenant-bound transaction. Hibernate
 * auto-filters by {@code tenant_id} thanks to {@code @TenantId} on
 * {@code TenantAwareEntity}, so cross-tenant lookups silently surface
 * as {@code Optional.empty()} (then mapped to 404) without leaking
 * existence of other tenants' data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

	private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
	private static final String AUTHORITY_PREFIX = "ROLE_";

	private final AttendanceSessionRepository sessionRepository;
	private final AttendanceRecordRepository recordRepository;
	private final StudentAttendanceQrRepository qrRepository;
	private final SectionRepository sectionRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final AttendanceUserCache userCache;
	private final QrTokenService qrTokenService;
	private final TenantSettingsService tenantSettingsService;
	private final AttendanceMapper mapper;
	private final CurrentUserProvider currentUserProvider;
	private final AttendanceAuditLogger auditLogger;
	private final ApplicationEventPublisher eventPublisher; // Sprint 9 / BE-9.3

	@Value("${edushift.attendance.future-drift-tolerance-minutes:1}")
	private long futureDriftToleranceMinutes;

	@Value("${edushift.attendance.edit-window-hours:24}")
	private long editWindowHours;

	// =====================================================================
	// openSession
	// =====================================================================

	@Override
	@Transactional
	public AttendanceSessionResponse openSession(CreateSessionRequest request) {
		requireAuthenticatedUser();

		Section section = sectionRepository.findByPublicUuid(request.sectionPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Section", request.sectionPublicUuid()));

		Optional<AttendanceSession> existing = sessionRepository.findActiveBySectionDaySlot(
				section, request.occurredOn(), request.effectiveSlot());
		if (existing.isPresent()) {
			AttendanceSession active = existing.get();
			log.info("[attendance] openSession idempotent -- section={} day={} slot={} session={}",
					section.getPublicUuid(), request.occurredOn(),
					request.effectiveSlot(), active.getPublicUuid());
			auditLogger.logSessionOpened(active, true);
			return toResponseWithUsers(active, true);
		}

		AttendanceSession fresh = new AttendanceSession();
		fresh.setSection(section);
		fresh.setOccurredOn(request.occurredOn());
		fresh.setSlot(request.effectiveSlot());
		fresh.setStartsAt(request.startsAt() != null ? request.startsAt() : Instant.now());
		fresh.setStatus(AttendanceSessionStatus.ACTIVE);
		fresh.setNotes(blankToNull(request.notes()));

		AttendanceSession saved;
		try {
			saved = sessionRepository.saveAndFlush(fresh);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			// Race: another request opened the same (section, day, slot)
			// between our pre-check and the insert. Surface a clean 409.
			throw new SessionAlreadyOpenException(
					"An ACTIVE session already exists for section "
							+ section.getPublicUuid() + " on "
							+ request.occurredOn() + " (" + request.effectiveSlot() + ")");
		}

		log.info("[attendance] session opened -- session={} section={} day={} slot={}",
				saved.getPublicUuid(), section.getPublicUuid(),
				saved.getOccurredOn(), saved.getSlot());
		auditLogger.logSessionOpened(saved, false);
		return toResponseWithUsers(saved, false);
	}

	// =====================================================================
	// closeSession
	// =====================================================================

	@Override
	@Transactional
	public AttendanceSessionResponse closeSession(UUID sessionPublicUuid) {
		UUID actorPublicUuid = requireAuthenticatedUser();
		AttendanceSession session = loadSession(sessionPublicUuid);

		if (session.getStatus() == AttendanceSessionStatus.CLOSED) {
			// Idempotent close: surface the current snapshot instead of
			// failing. The dashboard can already see the counts; the
			// docente that hits "Cerrar" twice should not see an error.
			log.info("[attendance] closeSession idempotent -- session={}",
					session.getPublicUuid());
			return toResponseWithCounts(session);
		}

		session.setStatus(AttendanceSessionStatus.CLOSED);
		session.setClosedAt(Instant.now());
		session.setClosedByUserId(actorPublicUuid);

		int materialized = materializeAbsentRecords(session);

		AttendanceSession saved = sessionRepository.saveAndFlush(session);
		log.info("[attendance] session closed -- session={} absent_materialized={}",
				saved.getPublicUuid(), materialized);
		auditLogger.logSessionClosed(saved, materialized);
		return toResponseWithCounts(saved);
	}

	private int materializeAbsentRecords(AttendanceSession session) {
		// All ACTIVE enrollments of the section (regardless of date —
		// the section is the same, and a withdrawn student has status
		// != ACTIVE so they won't be materialized as ABSENT).
		List<StudentEnrollment> activeEnrollments = enrollmentRepository
				.findActiveBySection(session.getSection());
		if (activeEnrollments.isEmpty()) {
			return 0;
		}

		Set<UUID> alreadyRecordedStudentIds = recordRepository
				.findBySessionOrderedByStudentName(session)
				.stream()
				.map(r -> r.getStudent().getId())
				.collect(Collectors.toSet());

		List<AttendanceRecord> toInsert = new ArrayList<>();
		Instant absentAt = session.getClosedAt() != null
				? session.getClosedAt() : Instant.now();
		for (StudentEnrollment enrollment : activeEnrollments) {
			Student student = enrollment.getStudent();
			if (alreadyRecordedStudentIds.contains(student.getId())) continue;

			AttendanceRecord absent = new AttendanceRecord();
			absent.setSession(session);
			absent.setStudent(student);
			absent.setStatus(AttendanceRecordStatus.ABSENT);
			absent.setOccurredAt(absentAt);
			toInsert.add(absent);
		}
		if (toInsert.isEmpty()) {
			return 0;
		}
		recordRepository.saveAll(toInsert);
		return toInsert.size();
	}

	// =====================================================================
	// checkIn
	// =====================================================================

	@Override
	@Transactional
	public AttendanceRecordResponse checkIn(CheckInRequest request) {
		UUID actorPublicUuid = requireAuthenticatedUser();
		UUID currentTenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated tenant is required for check-in"));

		validateForcedStatus(request);

		Student student = validateQrAndLoadStudent(
				request.qrToken(), currentTenantId, actorPublicUuid,
				request.sessionPublicUuid());

		AttendanceSession session = loadSession(request.sessionPublicUuid());
		if (session.getStatus() == AttendanceSessionStatus.CLOSED) {
			throw new SessionClosedException(
					"Session " + session.getPublicUuid() + " is closed");
		}

		Instant occurredAt = resolveOccurredAt(request);

		boolean enrolled = enrollmentRepository.existsActiveAt(
				student, session.getSection(), session.getOccurredOn());
		if (!enrolled) {
			throw new StudentNotEnrolledException(
					"Student " + student.getPublicUuid()
							+ " is not enrolled in section "
							+ session.getSection().getPublicUuid()
							+ " on " + session.getOccurredOn());
		}

		Optional<AttendanceRecord> existing =
				recordRepository.findBySessionAndStudent(session, student);
		if (existing.isPresent()) {
			AttendanceRecord stored = existing.get();
			log.info("[attendance] check-in idempotent -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logCheckedIn(stored, true);
			return toRecordResponseWithUsers(stored, true);
		}

		AttendanceRecordStatus status = computeStatus(
				request.forcedStatus(), session, occurredAt, currentTenantId);

		AttendanceRecord fresh = new AttendanceRecord();
		fresh.setSession(session);
		fresh.setStudent(student);
		fresh.setStatus(status);
		fresh.setOccurredAt(occurredAt);
		fresh.setScannedByUserId(actorPublicUuid);

		AttendanceRecord saved;
		try {
			saved = recordRepository.saveAndFlush(fresh);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			// Race: another scan persisted the (session, student) pair
			// between our pre-check and the insert. Reload and return
			// it as idempotent — semantically the same outcome.
			AttendanceRecord stored = recordRepository
					.findBySessionAndStudent(session, student)
					.orElseThrow(() -> e);
			log.info("[attendance] check-in race resolved -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logCheckedIn(stored, true);
			return toRecordResponseWithUsers(stored, true);
		}

		log.info("[attendance] check-in created -- session={} student={} status={} record={}",
				session.getPublicUuid(), student.getPublicUuid(),
				saved.getStatus(), saved.getPublicUuid());
		auditLogger.logCheckedIn(saved, false);
		return toRecordResponseWithUsers(saved, false);
	}

	// =====================================================================
	// manualCheckIn (BE-6.8)
	// =====================================================================

	@Override
	@Transactional
	public AttendanceRecordResponse manualCheckIn(ManualCheckInRequest request) {
		UUID actorPublicUuid = requireAuthenticatedUser();
		UUID currentTenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated tenant is required for manual check-in"));

		validateManualForcedStatus(request);

		Student student = studentRepository.findByPublicUuid(request.studentPublicUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Student", request.studentPublicUuid()));

		// Pick the most recent ACTIVE enrollment of the student. In a
		// healthy dataset there's at most one (partial unique index on
		// `(student, year) WHERE status=ACTIVE`), but we sort defensively
		// to stay deterministic if a data anomaly slips in. JOIN FETCH
		// the section to avoid a follow-up lazy SELECT.
		List<StudentEnrollment> active = enrollmentRepository.findActiveByStudentFetchSection(student);
		if (active.isEmpty()) {
			throw new StudentNoActiveEnrollmentException(
					"Student " + student.getPublicUuid()
							+ " has no ACTIVE enrollment to resolve a section from");
		}
		Section section = active.get(0).getSection();
		if (section == null) {
			// Defensive: schema-wise impossible, but a corrupt row should
			// surface a clean 422 rather than NPE.
			throw new StudentNoActiveEnrollmentException(
					"Student " + student.getPublicUuid()
							+ " has an ACTIVE enrollment with no section");
		}

		Instant requestedAt = request.occurredAt() != null
				? request.occurredAt() : Instant.now();
		LocalDate occurredOn = request.effectiveOccurredOn();
		AttendanceSessionSlot slot = request.effectiveSlot(requestedAt);

		ResolvedSession resolved = findOrOpenSession(section, occurredOn, slot, requestedAt);
		AttendanceSession session = resolved.session();

		if (session.getStatus() == AttendanceSessionStatus.CLOSED) {
			// findOrOpenSession can only return an ACTIVE row for the
			// happy path; if the only matching row is CLOSED, surface
			// it the same way the QR flow does.
			throw new SessionClosedException(
					"Session " + session.getPublicUuid() + " is closed");
		}

		Instant occurredAt = resolveOccurredAt(request.occurredAt());

		Optional<AttendanceRecord> existing =
				recordRepository.findBySessionAndStudent(session, student);
		if (existing.isPresent()) {
			AttendanceRecord stored = existing.get();
			log.info("[attendance] manual check-in idempotent -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logManualCheckedIn(stored, true, resolved.opened());
			return toRecordResponseWithUsers(stored, true);
		}

		AttendanceRecordStatus status = computeStatus(
				request.forcedStatus(), session, occurredAt, currentTenantId);

		AttendanceRecord fresh = new AttendanceRecord();
		fresh.setSession(session);
		fresh.setStudent(student);
		fresh.setStatus(status);
		fresh.setOccurredAt(occurredAt);
		fresh.setScannedByUserId(actorPublicUuid);

		AttendanceRecord saved;
		try {
			saved = recordRepository.saveAndFlush(fresh);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			AttendanceRecord stored = recordRepository
					.findBySessionAndStudent(session, student)
					.orElseThrow(() -> e);
			log.info("[attendance] manual check-in race resolved -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logManualCheckedIn(stored, true, resolved.opened());
			return toRecordResponseWithUsers(stored, true);
		}

		log.info("[attendance] manual check-in created -- session={} student={} status={} record={} sessionOpened={} actor={}",
				session.getPublicUuid(), student.getPublicUuid(),
				saved.getStatus(), saved.getPublicUuid(), resolved.opened(),
				actorPublicUuid);
		auditLogger.logManualCheckedIn(saved, false, resolved.opened());
		return toRecordResponseWithUsers(saved, false);
	}

	// =====================================================================
	// scanCheckIn (BE-6.8.b)
	// =====================================================================

	@Override
	@Transactional
	public AttendanceRecordResponse scanCheckIn(ScanCheckInRequest request) {
		UUID actorPublicUuid = requireAuthenticatedUser();
		UUID currentTenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated tenant is required for scan check-in"));

		if (request.forcedStatus() != null && !isCurrentUserAdmin()) {
			throw new ForcedStatusForbiddenException(
					"Only TENANT_ADMIN can force a status on scan check-in");
		}

		Student student = validateQrAndLoadStudent(
				request.qrToken(), currentTenantId, actorPublicUuid, null);

		// Auto-resolve section from the student's current ACTIVE
		// enrollment — same fallback logic as manualCheckIn. JOIN FETCH
		// the section to avoid a follow-up lazy SELECT.
		List<StudentEnrollment> active = enrollmentRepository.findActiveByStudentFetchSection(student);
		if (active.isEmpty()) {
			throw new StudentNoActiveEnrollmentException(
					"Student " + student.getPublicUuid()
							+ " has no ACTIVE enrollment to resolve a section from");
		}
		Section section = active.get(0).getSection();
		if (section == null) {
			throw new StudentNoActiveEnrollmentException(
					"Student " + student.getPublicUuid()
							+ " has an ACTIVE enrollment with no section");
		}

		Instant requestedAt = request.occurredAt() != null
				? request.occurredAt() : Instant.now();
		LocalDate occurredOn = LocalDate.now();
		// Wall-clock heuristic identical to ManualCheckInRequest#effectiveSlot:
		// MORNING before 12:00, AFTERNOON otherwise. FULL_DAY stays admin-driven.
		int hour = requestedAt.atZone(java.time.ZoneId.systemDefault()).getHour();
		AttendanceSessionSlot slot = hour < 12
				? AttendanceSessionSlot.MORNING
				: AttendanceSessionSlot.AFTERNOON;

		ResolvedSession resolved = findOrOpenSession(section, occurredOn, slot, requestedAt);
		AttendanceSession session = resolved.session();

		if (session.getStatus() == AttendanceSessionStatus.CLOSED) {
			throw new SessionClosedException(
					"Session " + session.getPublicUuid() + " is closed");
		}

		Instant occurredAt = resolveOccurredAt(request.occurredAt());

		Optional<AttendanceRecord> existing =
				recordRepository.findBySessionAndStudent(session, student);
		if (existing.isPresent()) {
			AttendanceRecord stored = existing.get();
			log.info("[attendance] scan check-in idempotent -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logCheckedIn(stored, true);
			return toRecordResponseWithUsers(stored, true);
		}

		AttendanceRecordStatus status = computeStatus(
				request.forcedStatus(), session, occurredAt, currentTenantId);

		AttendanceRecord fresh = new AttendanceRecord();
		fresh.setSession(session);
		fresh.setStudent(student);
		fresh.setStatus(status);
		fresh.setOccurredAt(occurredAt);
		fresh.setScannedByUserId(actorPublicUuid);

		AttendanceRecord saved;
		try {
			saved = recordRepository.saveAndFlush(fresh);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			AttendanceRecord stored = recordRepository
					.findBySessionAndStudent(session, student)
					.orElseThrow(() -> e);
			log.info("[attendance] scan check-in race resolved -- session={} student={} record={}",
					session.getPublicUuid(), student.getPublicUuid(),
					stored.getPublicUuid());
			auditLogger.logCheckedIn(stored, true);
			return toRecordResponseWithUsers(stored, true);
		}

		log.info("[attendance] scan check-in created -- session={} student={} status={} record={} sessionOpened={} actor={}",
				session.getPublicUuid(), student.getPublicUuid(),
				saved.getStatus(), saved.getPublicUuid(), resolved.opened(),
				actorPublicUuid);
		auditLogger.logCheckedIn(saved, false);
		return toRecordResponseWithUsers(saved, false);
	}

	/**
	 * Shared QR validation used by both {@link #checkIn(CheckInRequest)}
	 * and {@link #scanCheckIn(ScanCheckInRequest)}. Performs:
	 *
	 * <ol>
	 *   <li>Format check on the scanned short ID (no DB roundtrip).</li>
	 *   <li>Hash lookup on the active set. Hibernate's {@code @TenantId}
	 *       filter scopes the result to the bearer tenant — a token
	 *       issued elsewhere is simply invisible, surfaced as
	 *       {@code QR_INVALID} for anti-enumeration.</li>
	 *   <li>Revoked-vs-missing disambiguation via the unfiltered
	 *       {@code findAnyByTokenHash} (still tenant-scoped).</li>
	 * </ol>
	 *
	 * @param sessionUuidForAudit optional session UUID surfaced in QR
	 *        rejection audit entries; {@code null} for the session-less
	 *        scan flow.
	 */
	private Student validateQrAndLoadStudent(
			String qrToken, UUID currentTenantId, UUID actorPublicUuid,
			UUID sessionUuidForAudit) {
		qrTokenService.validateFormat(qrToken);

		String tokenHash = qrTokenService.hash(qrToken);
		Optional<StudentAttendanceQr> activeQr =
				qrRepository.findActiveByTokenHashFetchStudent(tokenHash);
		if (activeQr.isEmpty()) {
			Optional<StudentAttendanceQr> anyQr =
					qrRepository.findAnyByTokenHash(tokenHash);
			if (anyQr.isPresent() && anyQr.get().getRevokedAt() != null) {
				log.info("[attendance] QR_EXPIRED -- qr={} actor={} tenant={}",
						anyQr.get().getId(), actorPublicUuid, currentTenantId);
				auditLogger.logQrRejected(
						AttendanceErrorCodes.QR_EXPIRED,
						null,
						sessionUuidForAudit);
				throw new QrExpiredException(
						"QR was revoked at " + anyQr.get().getRevokedAt());
			}
			log.info("[attendance] QR_INVALID (no persisted row) -- actor={} tenant={}",
					actorPublicUuid, currentTenantId);
			auditLogger.logQrRejected(
					AttendanceErrorCodes.QR_INVALID,
					null,
					sessionUuidForAudit);
			throw new QrInvalidException(
					"QR token was not issued by this system");
		}

		Student student = activeQr.get().getStudent();
		if (student == null) {
			// Defensive: a NOT NULL FK guarantees this never happens in
			// healthy data, but surface it as INVALID rather than NPE.
			log.warn("[attendance] QR_INVALID (orphan row) -- qr={} actor={}",
					activeQr.get().getId(), actorPublicUuid);
			auditLogger.logQrRejected(
					AttendanceErrorCodes.QR_INVALID,
					null,
					sessionUuidForAudit);
			throw new QrInvalidException(
					"QR token row has no associated student");
		}
		return student;
	}

	/**
	 * Find an existing ACTIVE session for {@code (section, day, slot)}
	 * or open a brand-new one. Used exclusively by the manual flow,
	 * which must not require the auxiliary to pre-open a session.
	 *
	 * <p>The newly opened session is audited via
	 * {@link AttendanceAuditLogger#logSessionOpened(AttendanceSession, boolean)}
	 * with {@code wasIdempotent=false} so the timeline records the side
	 * effect explicitly.
	 */
	private ResolvedSession findOrOpenSession(
			Section section, LocalDate occurredOn,
			AttendanceSessionSlot slot, Instant referenceStartsAt) {
		Optional<AttendanceSession> existing = sessionRepository
				.findActiveBySectionDaySlot(section, occurredOn, slot);
		if (existing.isPresent()) {
			return new ResolvedSession(existing.get(), false);
		}

		AttendanceSession fresh = new AttendanceSession();
		fresh.setSection(section);
		fresh.setOccurredOn(occurredOn);
		fresh.setSlot(slot);
		fresh.setStartsAt(referenceStartsAt != null ? referenceStartsAt : Instant.now());
		fresh.setStatus(AttendanceSessionStatus.ACTIVE);

		try {
			AttendanceSession saved = sessionRepository.saveAndFlush(fresh);
			log.info("[attendance] manual flow opened session -- session={} section={} day={} slot={}",
					saved.getPublicUuid(), section.getPublicUuid(),
					occurredOn, slot);
			auditLogger.logSessionOpened(saved, false);
			return new ResolvedSession(saved, true);
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			// Race: another auxiliary opened the same (section, day,
			// slot) between our pre-check and the insert. Re-read and
			// treat as found.
			AttendanceSession winner = sessionRepository
					.findActiveBySectionDaySlot(section, occurredOn, slot)
					.orElseThrow(() -> e);
			return new ResolvedSession(winner, false);
		}
	}

	private void validateManualForcedStatus(ManualCheckInRequest request) {
		if (request.forcedStatus() == null) return;
		if (!isCurrentUserAdmin()) {
			throw new ForcedStatusForbiddenException(
					"Only TENANT_ADMIN can force a status on manual check-in");
		}
	}

	/** Result tuple of {@link #findOrOpenSession}. */
	private record ResolvedSession(AttendanceSession session, boolean opened) {
	}

	private AttendanceRecordStatus computeStatus(
			AttendanceRecordStatus forced,
			AttendanceSession session,
			Instant occurredAt,
			UUID tenantId) {
		if (forced != null) {
			// Already validated by validateForcedStatus(): only admin
			// reaches here.
			return forced;
		}
		Instant startsAt = session.getStartsAt();
		if (startsAt == null || !occurredAt.isAfter(startsAt)) {
			// Scan equal to or before startsAt (e.g. attendance taken
			// while doors are still opening) -> always PRESENT.
			return AttendanceRecordStatus.PRESENT;
		}
		int lateAfter = tenantSettingsService.getLateAfterMinutes(tenantId);
		Instant lateThreshold = startsAt.plus(Duration.ofMinutes(lateAfter));
		return occurredAt.isAfter(lateThreshold)
				? AttendanceRecordStatus.LATE
				: AttendanceRecordStatus.PRESENT;
	}

	private Instant resolveOccurredAt(CheckInRequest request) {
		return resolveOccurredAt(request.occurredAt());
	}

	/**
	 * Shared anti-clock-skew gate used by both the QR-driven
	 * {@link #checkIn(CheckInRequest)} and the manual fallback
	 * {@link #manualCheckIn(ManualCheckInRequest)} flows.
	 */
	private Instant resolveOccurredAt(Instant requestedOrNull) {
		Instant requested = requestedOrNull != null
				? requestedOrNull : Instant.now();
		Instant tolerance = Instant.now().plus(
				Duration.ofMinutes(Math.max(futureDriftToleranceMinutes, 0)));
		if (requested.isAfter(tolerance)) {
			throw new BadRequestException(
					AttendanceErrorCodes.OCCURRED_AT_DRIFT,
					"occurredAt drifts more than "
							+ futureDriftToleranceMinutes
							+ " minute(s) into the future");
		}
		return requested;
	}

	private void validateForcedStatus(CheckInRequest request) {
		if (request.forcedStatus() == null) return;
		if (!isCurrentUserAdmin()) {
			throw new ForcedStatusForbiddenException(
					"Only TENANT_ADMIN can force a status on check-in");
		}
	}

	// =====================================================================
	// listRecords
	// =====================================================================

	@Override
	@Transactional(readOnly = true)
	public List<AttendanceRecordResponse> listRecords(UUID sessionPublicUuid) {
		AttendanceSession session = loadSession(sessionPublicUuid);

		List<AttendanceRecord> realRecords =
				recordRepository.findBySessionOrderedByStudentName(session);
		Map<UUID, AttendanceRecord> recordByStudentId = new HashMap<>();
		for (AttendanceRecord r : realRecords) {
			recordByStudentId.put(r.getStudent().getId(), r);
		}

		List<StudentEnrollment> enrollments =
				enrollmentRepository.findActiveBySection(session.getSection());

		Map<UUID, User> usersByPublicUuid =
				resolveUsersForRecords(realRecords);

		boolean closed = session.getStatus() == AttendanceSessionStatus.CLOSED;
		AttendanceRecordStatus virtualStatus = closed
				? AttendanceRecordStatus.ABSENT : null;

		List<AttendanceRecordResponse> out = new ArrayList<>(enrollments.size());
		Set<UUID> alreadyEmitted = new HashSet<>();
		for (StudentEnrollment enrollment : enrollments) {
			Student student = enrollment.getStudent();
			AttendanceRecord existing = recordByStudentId.get(student.getId());
			if (existing != null) {
				out.add(mapper.toResponse(existing, usersByPublicUuid));
			}
			else {
				out.add(mapper.virtualRow(session, student, virtualStatus));
			}
			alreadyEmitted.add(student.getId());
		}

		// Edge case: a student has a record but is no longer in the
		// active roster (transferred mid-period). Surface them at the
		// bottom so the docente can still see and edit.
		for (AttendanceRecord r : realRecords) {
			if (alreadyEmitted.contains(r.getStudent().getId())) continue;
			out.add(mapper.toResponse(r, usersByPublicUuid));
		}

		out.sort(Comparator.comparing(
				AttendanceRecordResponse::studentFullName,
				Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
		return out;
	}

	// =====================================================================
	// updateRecord
	// =====================================================================

	@Override
	@Transactional
	public AttendanceRecordResponse updateRecord(
			UUID recordPublicUuid, UpdateRecordRequest request) {
		UUID actorPublicUuid = requireAuthenticatedUser();

		if (request == null || request.isEmpty()) {
			throw new BadRequestException(
					AttendanceErrorCodes.RECORD_EMPTY_PATCH,
					"Update payload must contain status and/or notes");
		}

		AttendanceRecord record = recordRepository.findByPublicUuid(recordPublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"AttendanceRecord", recordPublicUuid));

		boolean admin = isCurrentUserAdmin();
		AttendanceSession session = record.getSession();
		enforceEditWindow(session, admin);

		AttendanceRecordStatus prevStatus = record.getStatus();
		String prevNotes = record.getNotes();

		if (request.status() != null && request.status() != prevStatus) {
			if (!admin) {
				Set<AttendanceRecordStatus> legal =
						prevStatus.legalManualTransitions();
				if (!legal.contains(request.status())) {
					throw new BadRequestException(
							AttendanceErrorCodes.RECORD_ILLEGAL_TRANSITION,
							"Transition " + prevStatus + " -> "
									+ request.status() + " is not allowed");
				}
			}
			record.setStatus(request.status());
		}
		if (request.notes() != null) {
			record.setNotes(blankToNull(request.notes()));
		}

		record.setEditedByUserId(actorPublicUuid);
		record.setEditedAt(Instant.now());

		AttendanceRecord saved = recordRepository.saveAndFlush(record);
		log.info("[attendance] record edited -- record={} prevStatus={} newStatus={} actor={}",
				saved.getPublicUuid(), prevStatus, saved.getStatus(),
				actorPublicUuid);
		auditLogger.logRecordEdited(saved, prevStatus, prevNotes);

		// Sprint 9 / BE-9.3 — fire a notification event when a record
		// is manually marked ABSENT (the parent should know). The
		// NotificationEventListener consumes AFTER_COMMIT so a
		// rollback cancels the notification.
		if (saved.getStatus() == AttendanceRecordStatus.ABSENT
				&& prevStatus != AttendanceRecordStatus.ABSENT) {
			java.util.UUID studentUserId = saved.getStudent() == null ? null : saved.getStudent().getUserId();
			String studentName = saved.getStudent() == null ? "" : saved.getStudent().fullName();
			String sectionName = saved.getSession() == null || saved.getSession().getSection() == null
					? "" : saved.getSession().getSection().getName();
			eventPublisher.publishEvent(
					com.edushift.modules.notifications.event.NotificationEvent.builder()
							.templateKey("STUDENT_ABSENT")
							.category(com.edushift.modules.notifications.entity.Notification.Category.ABSENCE)
							.sourceId(saved.getPublicUuid())
							.recipients(studentUserId == null
									? java.util.List.of()
									: java.util.List.of(
										new com.edushift.modules.notifications.event.NotificationEvent.Recipient(
												studentUserId, null)))
							.payload(java.util.Map.of(
									"studentName", studentName,
									"date", saved.getSession().getOccurredOn().toString(),
									"reason", saved.getNotes() == null ? "" : saved.getNotes(),
									"courseName", sectionName,
									"parentName", ""
							))
							.build());
		}

		return toRecordResponseWithUsers(saved, null);
	}

	private void enforceEditWindow(AttendanceSession session, boolean admin) {
		if (admin) return;
		if (session == null) {
			throw new EditWindowExpiredException(
					"Cannot determine edit window: session missing");
		}
		Instant closedAt = session.getClosedAt();
		if (closedAt == null) {
			// Session still ACTIVE: edits allowed until it closes.
			return;
		}
		Instant deadline = closedAt.plus(Duration.ofHours(
				Math.max(editWindowHours, 0)));
		if (Instant.now().isAfter(deadline)) {
			throw new EditWindowExpiredException(
					"Editing window of " + editWindowHours
							+ "h after session close has expired");
		}
	}

	// =====================================================================
	// Lookup / mapping helpers
	// =====================================================================

	private AttendanceSession loadSession(UUID publicUuid) {
		return sessionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"AttendanceSession", publicUuid));
	}

	private AttendanceSessionResponse toResponseWithUsers(
			AttendanceSession session, boolean wasIdempotent) {
		Map<UUID, User> users = resolveUsers(collectAuditUserIds(session, List.of()));
		return mapper.toResponseIdempotent(session, wasIdempotent, users);
	}

	private AttendanceSessionResponse toResponseWithCounts(AttendanceSession session) {
		long present = recordRepository.countBySessionAndStatus(
				session, AttendanceRecordStatus.PRESENT);
		long late = recordRepository.countBySessionAndStatus(
				session, AttendanceRecordStatus.LATE);
		long absent = recordRepository.countBySessionAndStatus(
				session, AttendanceRecordStatus.ABSENT);
		long excused = recordRepository.countBySessionAndStatus(
				session, AttendanceRecordStatus.EXCUSED);
		Map<UUID, User> users = resolveUsers(collectAuditUserIds(session, List.of()));
		return mapper.toResponseWithCounts(session, present, late, absent, excused, users);
	}

	private AttendanceRecordResponse toRecordResponseWithUsers(
			AttendanceRecord record, Boolean wasIdempotent) {
		Map<UUID, User> users = resolveUsersForRecords(List.of(record));
		return mapper.toResponse(record, wasIdempotent, users);
	}

	private Map<UUID, User> resolveUsersForRecords(List<AttendanceRecord> records) {
		Set<UUID> ids = new HashSet<>();
		for (AttendanceRecord r : records) {
			if (r == null) continue;
			if (r.getScannedByUserId() != null) ids.add(r.getScannedByUserId());
			if (r.getEditedByUserId() != null) ids.add(r.getEditedByUserId());
		}
		return resolveUsers(ids);
	}

	private Set<UUID> collectAuditUserIds(
			AttendanceSession session, List<AttendanceRecord> records) {
		Set<UUID> ids = new HashSet<>();
		if (session != null && session.getClosedByUserId() != null) {
			ids.add(session.getClosedByUserId());
		}
		for (AttendanceRecord r : records) {
			if (r == null) continue;
			if (r.getScannedByUserId() != null) ids.add(r.getScannedByUserId());
			if (r.getEditedByUserId() != null) ids.add(r.getEditedByUserId());
		}
		return ids;
	}

	private Map<UUID, User> resolveUsers(Set<UUID> publicUuids) {
		if (publicUuids == null || publicUuids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, User> out = new HashMap<>(publicUuids.size());
		for (UUID id : publicUuids) {
			userCache.findByPublicUuid(id).ifPresent(u -> out.put(id, u));
		}
		return out;
	}

	private UUID requireAuthenticatedUser() {
		return currentUserProvider.currentUserId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated user is required"));
	}

	private boolean isCurrentUserAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) return false;
		for (GrantedAuthority granted : auth.getAuthorities()) {
			String authority = granted.getAuthority();
			if (authority == null) continue;
			if (authority.equals(ROLE_TENANT_ADMIN)) return true;
			if (authority.equals(AUTHORITY_PREFIX + ROLE_TENANT_ADMIN)) return true;
		}
		return false;
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	// =====================================================================
	// listSessions (BE-6.7)
	// =====================================================================

	@Override
	@Transactional(readOnly = true)
	public Page<AttendanceSessionListItemResponse> listSessions(
			ListSessionsFilter filter, Pageable pageable) {
		// Anti-enumeration: never accept tenant hints from the request.
		// Hibernate's @TenantId filter on TenantAwareEntity auto-scopes
		// the query to the current tenant — there's literally no way for
		// the caller to widen the result set by passing IDs from another
		// tenant.
		Section section = null;
		if (filter != null && filter.sectionPublicUuid() != null) {
			// If the section does not belong to the current tenant, we
			// return an empty page rather than 404. The list page is
			// the canonical "search sessions" UI; 404 is reserved for
			// direct-id access (per anti-enumeration policy).
			section = sectionRepository.findByPublicUuid(filter.sectionPublicUuid())
					.orElse(null);
			if (section == null) {
				return Page.empty(pageable);
			}
		}

		Page<AttendanceSession> raw = sessionRepository.findFilteredPaged(
				section,
				filter != null ? filter.status() : null,
				filter != null ? filter.slot() : null,
				filter != null ? filter.from() : null,
				filter != null ? filter.to() : null,
				pageable);

		if (raw.isEmpty()) {
			return raw.map(s -> mapper.toListItem(s, null, null, null, null));
		}

		// Pre-compute the four counts only for CLOSED rows (ACTIVE
		// ones would return 0/0/0/0 because ABSENT is not materialised
		// until close time — see ADR-6.6).
		Map<UUID, long[]> countsBySession = new HashMap<>();
		List<AttendanceSession> closed = raw.stream()
				.filter(s -> s.getStatus() == AttendanceSessionStatus.CLOSED)
				.toList();
		if (!closed.isEmpty()) {
			List<Object[]> rows = recordRepository
					.countGroupedByStatusForSessions(closed);
			for (Object[] row : rows) {
				AttendanceSession s = (AttendanceSession) row[0];
				AttendanceRecordStatus st = (AttendanceRecordStatus) row[1];
				Long n = (Long) row[2];
				long[] bucket = countsBySession.computeIfAbsent(
						s.getPublicUuid(), k -> new long[4]);
				switch (st) {
					case PRESENT -> bucket[0] = n;
					case LATE    -> bucket[1] = n;
					case ABSENT  -> bucket[2] = n;
					case EXCUSED -> bucket[3] = n;
				}
			}
		}

		return raw.map(s -> {
			long[] c = countsBySession.get(s.getPublicUuid());
			if (c == null) {
				return mapper.toListItem(s, null, null, null, null);
			}
			return mapper.toListItem(s, c[0], c[1], c[2], c[3]);
		});
	}
}
