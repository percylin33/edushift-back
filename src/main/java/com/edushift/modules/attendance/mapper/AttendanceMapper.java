package com.edushift.modules.attendance.mapper;

import com.edushift.modules.attendance.dto.AttendanceRecordResponse;
import com.edushift.modules.attendance.dto.AttendanceSessionResponse;
import com.edushift.modules.attendance.dto.UserRef;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.students.entity.Student;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between attendance JPA entities and the public
 * DTOs (Sprint 6 / BE-6.2.E).
 *
 * <h3>Why hand-written?</h3>
 * Same convention as the rest of the codebase
 * ({@code GradeRecordMapper}, {@code EvaluationMapper}): keeps the
 * service free of MapStruct-only magic, makes the {@code wasIdempotent}
 * / {@code studentFullName} / {@code scannedBy.fullName} branching
 * trivial to read, and lets us inline the {@code UserRef} resolution
 * with a tenant-scoped lookup map (avoids N+1 queries).
 */
@Component
public class AttendanceMapper {

	// ---------------------------------------------------------------------
	// Sessions
	// ---------------------------------------------------------------------

	public AttendanceSessionResponse toResponse(AttendanceSession session) {
		return toResponse(session, null, null, null, null, null,
				Map.of());
	}

	public AttendanceSessionResponse toResponseWithCounts(
			AttendanceSession session,
			long presentCount,
			long lateCount,
			long absentCount,
			long excusedCount,
			Map<UUID, User> usersByPublicUuid) {
		return toResponse(session,
				presentCount, lateCount, absentCount, excusedCount,
				null,
				usersByPublicUuid == null ? Map.of() : usersByPublicUuid);
	}

	public AttendanceSessionResponse toResponseIdempotent(
			AttendanceSession session,
			boolean wasIdempotent,
			Map<UUID, User> usersByPublicUuid) {
		return toResponse(session, null, null, null, null,
				wasIdempotent,
				usersByPublicUuid == null ? Map.of() : usersByPublicUuid);
	}

	private AttendanceSessionResponse toResponse(
			AttendanceSession session,
			Long presentCount,
			Long lateCount,
			Long absentCount,
			Long excusedCount,
			Boolean wasIdempotent,
			Map<UUID, User> usersByPublicUuid) {
		if (session == null) return null;
		return new AttendanceSessionResponse(
				session.getPublicUuid(),
				session.getSection() != null ? session.getSection().getPublicUuid() : null,
				session.getOccurredOn(),
				session.getSlot(),
				session.getStatus(),
				session.getStartsAt(),
				session.getClosedAt(),
				resolveUserRef(session.getClosedByUserId(), usersByPublicUuid),
				session.getNotes(),
				presentCount,
				lateCount,
				absentCount,
				excusedCount,
				wasIdempotent,
				session.getCreatedAt(),
				session.getUpdatedAt()
		);
	}

	// ---------------------------------------------------------------------
	// Records
	// ---------------------------------------------------------------------

	public AttendanceRecordResponse toResponse(
			AttendanceRecord record, Map<UUID, User> usersByPublicUuid) {
		return toResponse(record, null,
				usersByPublicUuid == null ? Map.of() : usersByPublicUuid);
	}

	public AttendanceRecordResponse toResponse(
			AttendanceRecord record,
			Boolean wasIdempotent,
			Map<UUID, User> usersByPublicUuid) {
		if (record == null) return null;
		Student student = record.getStudent();
		return new AttendanceRecordResponse(
				record.getPublicUuid(),
				record.getSession() != null ? record.getSession().getPublicUuid() : null,
				student != null ? student.getPublicUuid() : null,
				student != null ? student.fullName() : null,
				record.getStatus(),
				record.getOccurredAt(),
				resolveUserRef(record.getScannedByUserId(),
						usersByPublicUuid == null ? Map.of() : usersByPublicUuid),
				resolveUserRef(record.getEditedByUserId(),
						usersByPublicUuid == null ? Map.of() : usersByPublicUuid),
				record.getEditedAt(),
				record.getNotes(),
				wasIdempotent,
				record.getCreatedAt(),
				record.getUpdatedAt()
		);
	}

	/**
	 * Synthetic roster row for an enrolled student that still has no
	 * real record. {@code status} is {@code null} for ACTIVE sessions
	 * and {@code ABSENT} for CLOSED ones (the latter typically backs a
	 * materialized record, so this synthetic shape is only used as a
	 * fallback when materialization was skipped).
	 */
	public AttendanceRecordResponse virtualRow(
			AttendanceSession session,
			Student student,
			com.edushift.modules.attendance.entity.AttendanceRecordStatus status) {
		if (student == null) return null;
		return new AttendanceRecordResponse(
				null,
				session != null ? session.getPublicUuid() : null,
				student.getPublicUuid(),
				student.fullName(),
				status,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
		);
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private static UserRef resolveUserRef(UUID userPublicUuid, Map<UUID, User> users) {
		if (userPublicUuid == null) return null;
		User user = users.get(userPublicUuid);
		String fullName = user != null ? user.fullName() : null;
		return new UserRef(userPublicUuid, fullName);
	}
}
