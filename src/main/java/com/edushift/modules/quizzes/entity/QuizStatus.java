package com.edushift.modules.quizzes.entity;

/**
 * Quiz lifecycle (Sprint 7b / BE-7b.0).
 *
 * <h3>States</h3>
 * <ul>
 *   <li>{@link #DRAFT} — created, editable, not visible to students.</li>
 *   <li>{@link #PUBLISHED} — frozen, visible to students, accepts
 *       attempts (subject to {@code dueAt} and {@code timeLimitMinutes}).</li>
 *   <li>{@link #CLOSED} — no new attempts, results viewable. Reached
 *       either manually (teacher closes) or automatically
 *       (due_at + time limit job, BE-7b.4).</li>
 * </ul>
 *
 * <p>Persisted as {@code VARCHAR(16)} via {@code @Enumerated(STRING)}.
 * DB-level valid values enforced by
 * {@code chk_lms_quizzes_status} in {@code V35__create_lms_quizzes.sql}.
 */
public enum QuizStatus {
	DRAFT,
	PUBLISHED,
	CLOSED
}
