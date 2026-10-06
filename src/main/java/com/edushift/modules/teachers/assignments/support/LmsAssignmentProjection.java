package com.edushift.modules.teachers.assignments.support;

import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.util.UUID;

/**
 * Flat projection of assignment fields embedded in LMS DTOs (V105).
 */
public final class LmsAssignmentProjection {

	private LmsAssignmentProjection() {
	}

	public static UUID publicUuid(TeacherAssignment assignment) {
		return assignment == null ? null : assignment.getPublicUuid();
	}

	public static UUID coursePublicUuid(TeacherAssignment assignment) {
		if (assignment == null || assignment.getCourse() == null) {
			return null;
		}
		return assignment.getCourse().getPublicUuid();
	}

	public static String courseName(TeacherAssignment assignment) {
		if (assignment == null || assignment.getCourse() == null) {
			return null;
		}
		return assignment.getCourse().getName();
	}

	public static String periodLabel(TeacherAssignment assignment) {
		if (assignment == null || assignment.getAcademicPeriod() == null) {
			return null;
		}
		return assignment.getAcademicPeriod().getName();
	}
}
