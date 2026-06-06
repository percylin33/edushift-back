package com.edushift.modules.students.enrollments.entity;

/**
 * Per-year lifecycle of a {@link StudentEnrollment}.
 *
 * <p>Distinct from {@code Student.enrollmentStatus}, which tracks the
 * student's institution-wide lifecycle (PENDING / ENROLLED / GRADUATED /
 * TRANSFERRED / WITHDRAWN). A single student may have several enrollment
 * rows across multiple academic years; this enum models the lifecycle of
 * each individual row.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — currently studying in the linked section.
 *       The {@code uk_student_enrollments_active} partial unique index
 *       limits this state to a single row per (student, academic year).</li>
 *   <li>{@link #WITHDRAWN} — student left the institution.</li>
 *   <li>{@link #TRANSFERRED} — student moved to another section or
 *       another school.</li>
 *   <li>{@link #GRADUATED} — student completed the cycle (terminal).</li>
 * </ul>
 */
public enum StudentEnrollmentStatus {
	ACTIVE,
	WITHDRAWN,
	TRANSFERRED,
	GRADUATED;

	/**
	 * @return {@code true} when the value represents a row that has been
	 *         soft-ended (any non-{@link #ACTIVE} value).
	 */
	public boolean isTerminal() {
		return this != ACTIVE;
	}
}
