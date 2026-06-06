package com.edushift.modules.teachers.assignments.service;

import com.edushift.modules.teachers.assignments.dto.AssignmentListItem;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.CreateAssignmentRequest;
import com.edushift.modules.teachers.assignments.dto.SectionTeacherItem;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the {@code teachers.assignments} sub-module
 * (Sprint 4 / BE-4.7).
 *
 * <h3>Error contract</h3>
 * <ul>
 *   <li>{@code 404 RESOURCE_NOT_FOUND} — any of teacher / section /
 *       course / period not found in the current tenant.</li>
 *   <li>{@code 409 ASSIGNMENT_ALREADY_ACTIVE} — the 4-tuple already has
 *       an active row.</li>
 *   <li>{@code 409 ASSIGNMENT_YEAR_MISMATCH} — section's year ≠ period's
 *       year.</li>
 *   <li>{@code 409 COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL} — course is
 *       not associated to the section's level.</li>
 *   <li>{@code 409 TEACHER_NOT_ACTIVE} — the teacher's
 *       {@code employmentStatus} is {@code RESIGNED} or {@code RETIRED}
 *       (i.e. not assignable).</li>
 * </ul>
 */
public interface TeacherAssignmentService {

	/**
	 * @param teacherPublicUuid       teacher anchor (path variable).
	 * @param periodPublicUuid        optional filter — when null and
	 *                                {@code activeOnly} is true, the
	 *                                service narrows to the active
	 *                                academic year's periods on its own
	 *                                only when an explicit period
	 *                                filter is the more useful default.
	 *                                For now we keep the contract simple:
	 *                                null period => no period filter.
	 * @param activeOnly              when true (default at the
	 *                                controller), hides soft-ended rows.
	 */
	List<AssignmentListItem> listForTeacher(UUID teacherPublicUuid,
			UUID periodPublicUuid, boolean activeOnly);

	/**
	 * Reverse view used by FE Sections detail. Always returns active rows.
	 */
	List<SectionTeacherItem> listForSection(UUID sectionPublicUuid,
			UUID periodPublicUuid);

	AssignmentResponse createAssignment(UUID teacherPublicUuid,
			CreateAssignmentRequest request);

	/**
	 * Soft-end an active assignment. Sets {@code unassignedAt = NOW()} and
	 * preserves the row for grade reports / audit. Idempotent for
	 * already-ended rows: returns 204 / no-op.
	 */
	void softEnd(UUID assignmentPublicUuid);
}
