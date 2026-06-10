package com.edushift.modules.attendance.service.impl;

import com.edushift.modules.attendance.audit.AttendanceAuditLogger;
import com.edushift.modules.attendance.dto.AttendanceQrInfo;
import com.edushift.modules.attendance.entity.QrRevokedReason;
import com.edushift.modules.attendance.entity.StudentAttendanceQr;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.AttendanceQrService;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AttendanceQrService} implementation
 * (Sprint 6 / BE-6.3).
 *
 * <p>{@link #getOrIssueQr(UUID)} and {@link #rotate(UUID)} share the
 * same persistence path on purpose: the only difference is the
 * exposed API surface (and, in BE-6.4, the audit event type). Keeping
 * them aligned avoids subtle drift between "how the FE refreshes a
 * credential" and "how the admin invalidates a lost credential".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceQrServiceImpl implements AttendanceQrService {

	private final StudentRepository studentRepository;
	private final StudentAttendanceQrRepository qrRepository;
	private final QrTokenService qrTokenService;
	private final CurrentUserProvider currentUserProvider;
	private final AttendanceAuditLogger auditLogger;

	@Override
	@Transactional
	public IssuedQr getOrIssueQr(UUID studentPublicUuid) {
		return issueAndRevokePrevious(studentPublicUuid, QrRevokedReason.ROTATED);
	}

	@Override
	@Transactional
	public IssuedQr rotate(UUID studentPublicUuid) {
		return issueAndRevokePrevious(studentPublicUuid, QrRevokedReason.ROTATED);
	}

	@Override
	@Transactional(readOnly = true)
	public AttendanceQrInfo getInfo(UUID studentPublicUuid) {
		Student student = loadStudent(studentPublicUuid);
		Optional<StudentAttendanceQr> active = qrRepository.findActiveByStudent(student);
		return active.map(qr -> new AttendanceQrInfo(
						student.getPublicUuid(),
						qr.getIssuedAt(),
						null,
						null))
				.orElse(null);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/**
	 * Persist a fresh QR row and revoke the previous active one (if
	 * any). Returns the raw JWT plus the post-mutation info envelope.
	 */
	private IssuedQr issueAndRevokePrevious(
			UUID studentPublicUuid, QrRevokedReason reason) {
		// Force-resolve the bearer up-front. We don't propagate it down
		// to the QR row (the audit FK lives in audit_logs, not in
		// student_attendance_qr) but a missing principal surfaces as a
		// clean 401 before we touch the DB.
		currentUserProvider.currentUserId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated user is required to manage QR credentials"));
		UUID currentTenantId = currentUserProvider.currentTenantId().orElseThrow(
				() -> new UnauthorizedException(
						"Authenticated tenant is required to manage QR credentials"));

		Student student = loadStudent(studentPublicUuid);

		Optional<StudentAttendanceQr> previousOpt =
				qrRepository.findActiveByStudent(student);
		Instant previousRevokedAt = null;
		QrRevokedReason previousRevokedReason = null;
		if (previousOpt.isPresent()) {
			StudentAttendanceQr previous = previousOpt.get();
			Instant revokedAt = Instant.now();
			previous.setRevokedAt(revokedAt);
			previous.setRevokedReason(reason);
			qrRepository.saveAndFlush(previous);
			previousRevokedAt = revokedAt;
			previousRevokedReason = reason;
			log.info("[attendance-qr] revoked previous -- student={} reason={} qr={}",
					student.getPublicUuid(), reason, previous.getId());
		}

		QrTokenService.IssuedQrToken issued = qrTokenService.issue(
				student.getPublicUuid(), currentTenantId);

		StudentAttendanceQr fresh = new StudentAttendanceQr();
		fresh.setStudent(student);
		fresh.setTokenHash(issued.tokenHash());
		fresh.setIssuedAt(Instant.now());
		StudentAttendanceQr saved = qrRepository.saveAndFlush(fresh);

		log.info("[attendance-qr] issued -- student={} qr={} previousRevoked={}",
				student.getPublicUuid(), saved.getId(),
				previousRevokedAt != null);

		// Audit trail: distinguish "first issuance" (CREATE) from
		// "rotation" (UPDATE) by inspecting whether a previous active
		// row was revoked above. Both paths go through the same DB
		// effect but produce different timeline entries so security
		// and FE can react differently (e.g. send a "Tu credencial fue
		// rotada" notification only on UPDATE).
		if (previousRevokedAt == null) {
			auditLogger.logQrIssued(saved);
		}
		else {
			auditLogger.logQrRotated(saved, previousRevokedReason);
		}

		AttendanceQrInfo info = new AttendanceQrInfo(
				student.getPublicUuid(),
				saved.getIssuedAt(),
				previousRevokedAt,
				previousRevokedReason);
		return new IssuedQr(issued.token(), info);
	}

	private Student loadStudent(UUID publicUuid) {
		return studentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Student", publicUuid));
	}
}
