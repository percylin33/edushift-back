package com.edushift.modules.tasks.submission.entity;

/**
 * Lifecycle of a {@link Submission} (Sprint 7a / BE-7a.2). See
 * {@code docs/modules/lms-tasks.md} §4.2.
 *
 * <ul>
 *   <li>{@code SUBMITTED} - the initial state when a student (or
 *       parent) posts. The submission is ready to be graded.</li>
 *   <li>{@code GRADED} - the teacher has recorded a {@code grade}
 *       (0..100) and optional feedback. Immutable grade range
 *       enforced by DB CHECK.</li>
 *   <li>{@code SOFT_DELETED} - rare admin override. The row stays
 *       for audit but is hidden from the FE.</li>
 * </ul>
 *
 * <p>Future states (DEBT-7A-15) include {@code RETURNED} (teacher
 * returns without a grade) and {@code LATE} (bypassed grace
 * period). Not in v1.
 */
public enum SubmissionStatus {
	SUBMITTED,
	GRADED,
	SOFT_DELETED
}
