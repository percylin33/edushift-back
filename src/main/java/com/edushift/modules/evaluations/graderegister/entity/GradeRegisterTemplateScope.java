package com.edushift.modules.evaluations.graderegister.entity;

/**
 * Scope of a {@link GradeRegisterTemplate} (F1 formal register).
 */
public enum GradeRegisterTemplateScope {

	/** Applies to all courses of the academic year (default template). */
	TENANT,

	/** Applies only to one course within the year. */
	COURSE,

	/** Optional override for every section of one academic level in the year. */
	LEVEL
}
