package com.edushift.modules.attendance.service;

import com.edushift.modules.attendance.dto.AttendanceStudentLookupItem;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Backs {@code GET /api/v1/attendance/students/lookup}
 * (Sprint 6 / BE-6.8 — manual fallback picker).
 *
 * <p>Returns a tenant-scoped, paginated list of students matching the
 * supplied filters, restricted to those with a current ACTIVE
 * {@code StudentEnrollment} (so the result is always actionable on
 * {@code POST /attendance/manual-check-in}).
 *
 * <p>Separate from {@link com.edushift.modules.students.service.StudentService}
 * on purpose: this lookup is TEACHER-accessible and returns a lean
 * projection without PII (email, address, birthDate). Keeping it on
 * its own service avoids relaxing the @PreAuthorize on the broader
 * student admin endpoints.
 */
public interface AttendanceStudentLookupService {

	/**
	 * @param filter optional filter shape; pass {@link Filter#empty()}
	 *               when no filter is needed.
	 * @param pageable standard Spring Data pagination. Service enforces
	 *                 a hard upper bound on {@code size}.
	 */
	Page<AttendanceStudentLookupItem> lookup(Filter filter, Pageable pageable);

	/**
	 * Optional filter shape. All fields are nullable and AND-combined.
	 *
	 * @param q                 case-insensitive substring against
	 *                          {@code firstName}, {@code lastName} and
	 *                          {@code documentNumber}.
	 * @param levelPublicUuid   filter by the level of the student's
	 *                          ACTIVE enrollment section.
	 * @param gradePublicUuid   filter by the grade of the student's
	 *                          ACTIVE enrollment section.
	 * @param sectionPublicUuid filter by the section of the student's
	 *                          ACTIVE enrollment.
	 */
	record Filter(
			String q,
			UUID levelPublicUuid,
			UUID gradePublicUuid,
			UUID sectionPublicUuid
	) {
		public static Filter empty() {
			return new Filter(null, null, null, null);
		}
	}
}
