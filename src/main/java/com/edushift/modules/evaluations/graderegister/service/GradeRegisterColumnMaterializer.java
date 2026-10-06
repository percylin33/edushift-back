package com.edushift.modules.evaluations.graderegister.service;

import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;

/**
 * Ensures SERIES/SINGLE template slots exist as DRAFT evaluations on an assignment.
 */
public interface GradeRegisterColumnMaterializer {

	/**
	 * Idempotent: creates missing DRAFT {@code SCORE_0_20} evaluations for SERIES/SINGLE
	 * categories of the template applicable to the assignment.
	 */
	void ensureColumns(TeacherAssignment assignment);
}
