package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.attendance.audit.AttendanceAuditLogger;
import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.CheckInRequest;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.dto.UpdateRecordRequest;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.entity.StudentAttendanceQr;
import com.edushift.modules.attendance.error.AttendanceErrorCodes;
import com.edushift.modules.attendance.exception.EditWindowExpiredException;
import com.edushift.modules.attendance.exception.ForcedStatusForbiddenException;
import com.edushift.modules.attendance.exception.QrExpiredException;
import com.edushift.modules.attendance.exception.QrInvalidException;
import com.edushift.modules.attendance.exception.SessionAlreadyOpenException;
import com.edushift.modules.attendance.exception.SessionClosedException;
import com.edushift.modules.attendance.exception.StudentNotEnrolledException;
import com.edushift.modules.attendance.mapper.AttendanceMapper;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.AttendanceService;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
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
	private final UserRepository userRepository;
	private final QrTokenService qrTokenService;
	private final TenantSettingsService tenantSettingsService;
	private final AttendanceMapper mapper;
	private final CurrentUserProvider currentUserProvider;
	private final AttendanceAuditLogger auditLogger;

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

		QrTokenService.QrClaims claims = qrTokenService.parseAndValidate(request.qrToken());

		// Anti-enumeration: a QR from another tenant must look exactly
		// like a non-existent QR. We compare BEFORE any DB lookup so we
		// never reveal "QR exists in another tenant". Internally the
		// rejection is recorded in the audit timeline with the precise
		// reason (QR_TENANT_MISMATCH) so security can investigate; the
		// caller still sees a generic 404.
		if (!claims.tenantId().equals(currentTenantId)) {
			log.warn("[attendance] {} -- claim_tenant={} actor_tenant={} actor={}",
					AttendanceErrorCodes.QR_TENANT_MISMATCH,
					claims.tenantId(), currentTenantId, actorPublicUuid);
			auditLogger.logQrRejected(
					AttendanceErrorCodes.QR_TENANT_MISMATCH,
					claims.studentPublicUuid(),
					request.sessionPublicUuid());
			throw new ResourceNotFoundException(
					"Student", claims.studentPublicUuid());
		}

		String tokenHash = qrTokenService.hash(request.qrToken());
		Optional<StudentAttendanceQr> activeQr =
				qrRepository.findActiveByTokenHash(tokenHash);
		if (activeQr.isEmpty()) {
			Optional<StudentAttendanceQr> anyQr =
					qrRepository.findAnyByTokenHash(tokenHash);
			if (anyQr.isPresent() && anyQr.get().getRevokedAt() != null) {
				log.info("[attendance] QR_EXPIRED -- qr={} actor={}",
						anyQr.get().getId(), actorPublicUuid);
				auditLogger.logQrRejected(
						AttendanceErrorCodes.QR_EXPIRED,
						claims.studentPublicUuid(),
						request.sessionPublicUuid());
				throw new QrExpiredException(
						"QR was revoked at " + anyQr.get().getRevokedAt());
			}
			// No row at all: token was minted with our secret + correct
			// tenant + correct typ, but never persisted. Treat as
			// invalid — same outcome as a forged or stale-secret token.
			log.info("[attendance] QR_INVALID (no persisted row) -- actor={}",
					actorPublicUuid);
			auditLogger.logQrRejected(
					AttendanceErrorCodes.QR_INVALID,
					claims.studentPublicUuid(),
					request.sessionPublicUuid());
			throw new QrInvalidException(
					"QR token was not issued by this system");
		}

		StudentAttendanceQr qr = activeQr.get();
		Student student = qr.getStudent();
		if (student == null
				|| !student.getPublicUuid().equals(claims.studentPublicUuid())) {
			// Defensive: hash collision or tampering. Treat as invalid.
			log.warn("[attendance] QR_INVALID (sub/student mismatch) -- qr={} actor={}",
					qr.getId(), actorPublicUuid);
			auditLogger.logQrRejected(
					AttendanceErrorCodes.QR_INVALID,
					claims.studentPublicUuid(),
					request.sessionPublicUuid());
			throw new QrInvalidException(
					"QR token does not match its persisted record");
		}

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
		Instant requested = request.occurredAt() != null
				? request.occurredAt() : Instant.now();
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
			userRepository.findByPublicUuid(id).ifPresent(u -> out.put(id, u));
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
}
