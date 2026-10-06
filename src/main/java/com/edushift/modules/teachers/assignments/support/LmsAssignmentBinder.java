package com.edushift.modules.teachers.assignments.support;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves and validates a {@link TeacherAssignment} for LMS create/list
 * flows (V105 — course-scoped tasks / materials / quizzes).
 *
 * <p>Rules:
 * <ul>
 *   <li>Assignment must exist in the current tenant (404 otherwise).</li>
 *   <li>Assignment must be active ({@code unassignedAt == null}).</li>
 *   <li>Assignment section must match the path section.</li>
 *   <li>TEACHER callers must own the assignment; TENANT_ADMIN may use any
 *       assignment in the section.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class LmsAssignmentBinder {

	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final TeacherRepository teacherRepository;
	private final CurrentUserProvider currentUserProvider;

	/**
	 * Resolves an active assignment for creating LMS content under
	 * {@code section}. Throws when the UUID is missing or invalid.
	 */
	public TeacherAssignment requireForCreate(UUID assignmentPublicUuid, Section section) {
		if (assignmentPublicUuid == null) {
			throw new BadRequestException(
					"TEACHER_ASSIGNMENT_REQUIRED",
					"teacherAssignmentPublicUuid is required to create LMS content");
		}
		TeacherAssignment assignment = teacherAssignmentRepository
				.findByPublicUuid(assignmentPublicUuid)
				.orElseThrow(() -> new NotFoundException(
						"TEACHER_ASSIGNMENT_NOT_FOUND",
						"Teacher assignment not found in the current tenant"));

		if (!assignment.isActive()) {
			throw new BadRequestException(
					"ASSIGNMENT_NOT_ACTIVE",
					"Teacher assignment is no longer active");
		}

		if (section == null
				|| assignment.getSection() == null
				|| !section.getId().equals(assignment.getSection().getId())) {
			throw new BadRequestException(
					"ASSIGNMENT_SECTION_MISMATCH",
					"Teacher assignment does not belong to the target section");
		}

		assertCallerMayUse(assignment);
		return assignment;
	}

	/**
	 * Resolves an assignment for list filtering. Returns empty-safe 404
	 * when the UUID does not exist; does not require ownership for
	 * TENANT_ADMIN list views, but TEACHER must own it.
	 */
	public TeacherAssignment requireForFilter(UUID assignmentPublicUuid, Section section) {
		TeacherAssignment assignment = teacherAssignmentRepository
				.findByPublicUuid(assignmentPublicUuid)
				.orElseThrow(() -> new NotFoundException(
						"TEACHER_ASSIGNMENT_NOT_FOUND",
						"Teacher assignment not found in the current tenant"));
		if (section != null
				&& assignment.getSection() != null
				&& !section.getId().equals(assignment.getSection().getId())) {
			throw new NotFoundException(
					"TEACHER_ASSIGNMENT_NOT_FOUND",
					"Teacher assignment not found in the current tenant");
		}
		assertCallerMayUse(assignment);
		return assignment;
	}

	/**
	 * Resolves an assignment by public UUID and asserts the caller may use it.
	 * Used by learning-session flows that key directly on assignment (no
	 * section path param).
	 */
	public TeacherAssignment requireByPublicUuid(UUID assignmentPublicUuid) {
		TeacherAssignment assignment = teacherAssignmentRepository
				.findByPublicUuid(assignmentPublicUuid)
				.orElseThrow(() -> new NotFoundException(
						"TEACHER_ASSIGNMENT_NOT_FOUND",
						"Teacher assignment not found in the current tenant"));
		assertCallerMayUse(assignment);
		return assignment;
	}

	/**
	 * Ensures the caller may read or mutate content bound to {@code assignment}.
	 */
	public void assertCallerMayUseAssignment(TeacherAssignment assignment) {
		assertCallerMayUse(assignment);
	}

	/**
	 * For filtered lists: TEACHER is always scoped to self; TENANT_ADMIN may
	 * pass an optional teacher filter. Anti-enumeration: a TEACHER asking for
	 * another teacher's UUID gets 404.
	 */
	public UUID resolveTeacherFilterForList(UUID requestedTeacherUuid) {
		if (isTenantAdmin()) {
			return requestedTeacherUuid;
		}
		UUID callerUserPublicUuid = currentUserProvider.currentUserId()
				.orElseThrow(() -> new NotFoundException(
						"AUTH_REQUIRED",
						"Authenticated user identity is required"));
		UUID myTeacherPublicUuid = teacherRepository.findByUserId(callerUserPublicUuid)
				.map(Teacher::getPublicUuid)
				.orElseThrow(() -> new NotFoundException(
						"TEACHER_NOT_FOUND",
						"No teacher profile linked to the current user"));
		if (requestedTeacherUuid != null
				&& !requestedTeacherUuid.equals(myTeacherPublicUuid)) {
			throw new NotFoundException(
					"TEACHER_NOT_FOUND",
					"Teacher not found in the current tenant");
		}
		return myTeacherPublicUuid;
	}

	public boolean isCurrentUserTenantAdmin() {
		return isTenantAdmin();
	}

	private void assertCallerMayUse(TeacherAssignment assignment) {
		if (isTenantAdmin()) {
			return;
		}
		UUID callerId = currentUserProvider.currentUserId().orElse(null);
		Teacher teacher = assignment.getTeacher();
		if (callerId == null
				|| teacher == null
				|| teacher.getUserId() == null
				|| !callerId.equals(teacher.getUserId())) {
			throw new ForbiddenException(
					"ASSIGNMENT_NOT_OWNED",
					"You can only use your own teacher assignments");
		}
	}

	private static boolean isTenantAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return false;
		}
		return auth.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(a -> "TENANT_ADMIN".equals(a)
						|| "ROLE_TENANT_ADMIN".equals(a));
	}
}
