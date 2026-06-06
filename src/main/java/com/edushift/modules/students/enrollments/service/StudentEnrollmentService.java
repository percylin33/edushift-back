package com.edushift.modules.students.enrollments.service;

import com.edushift.modules.students.enrollments.dto.CreateEnrollmentRequest;
import com.edushift.modules.students.enrollments.dto.EnrollmentListItem;
import com.edushift.modules.students.enrollments.dto.EnrollmentResponse;
import com.edushift.modules.students.enrollments.dto.SectionStudentRosterItem;
import com.edushift.modules.students.enrollments.dto.WithdrawEnrollmentRequest;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped CRUD over {@code student_enrollments} (Sprint 4 / BE-4.8).
 *
 * <h3>Authorization</h3>
 * Every endpoint is {@code @PreAuthorize("hasRole('TENANT_ADMIN')")} for
 * Sprint 4. Permission-based gates ({@code STUDENTS:WRITE}, etc.) are
 * registered as deferred technical debt; see {@code SPRINT-04} doc.
 *
 * <h3>Error contract</h3>
 * <ul>
 *   <li>{@code 404 RESOURCE_NOT_FOUND} — student / section / year /
 *       enrollment not found in the current tenant. Cross-tenant ids
 *       collapse to the same code (Hibernate filters them out).</li>
 *   <li>{@code 409 STUDENT_ALREADY_ENROLLED} — there is already an
 *       ACTIVE enrollment for the same {@code (student, year)}.</li>
 *   <li>{@code 409 ENROLLMENT_DATE_OUT_OF_YEAR} — {@code enrolledAt}
 *       falls outside the academic year window.</li>
 *   <li>{@code 409 ENROLLMENT_YEAR_MISMATCH} —
 *       {@code section.academicYear != request.academicYear}.</li>
 *   <li>{@code 400 INVALID_WITHDRAW_STATUS} — caller passed
 *       {@code ACTIVE} as the withdraw target.</li>
 *   <li>{@code 400 VALIDATION_ERROR} — {@code withdrawnAt < enrolledAt}.</li>
 * </ul>
 */
public interface StudentEnrollmentService {

	/** Returns the full enrollment timeline of a student. */
	List<EnrollmentListItem> listForStudent(UUID studentPublicUuid);

	/** Returns the active roster of a section. */
	List<SectionStudentRosterItem> listRoster(UUID sectionPublicUuid);

	/** Creates a new ACTIVE enrollment for the student. */
	EnrollmentResponse createEnrollment(UUID studentPublicUuid,
			CreateEnrollmentRequest request);

	/** Soft-ends an enrollment, transitioning it to a terminal status. */
	EnrollmentResponse withdrawEnrollment(UUID enrollmentPublicUuid,
			WithdrawEnrollmentRequest request);
}
