package com.edushift.modules.quizzes.entity;

/**
 * Quiz attempt lifecycle (Sprint 7b / BE-7b.0).
 *
 * <h3>States</h3>
 * <ul>
 *   <li>{@link #IN_PROGRESS} — student opened the attempt, saving
 *       answers (autosave). Not yet submitted. {@code submitted_at}
 *       is NULL.</li>
 *   <li>{@link #SUBMITTED} — student clicked "Submit final".
 *       {@code submitted_at} is set. Awaiting auto-grading.</li>
 *   <li>{@link #AUTO_GRADED} — BE-7b.1 has graded all MC + TF
 *       questions. SHORT_ANSWER questions remain pending manual
 *       grading. {@code auto_score} is set; {@code manual_score}
 *       and {@code score} are NULL.</li>
 *   <li>{@link #GRADED} — all questions graded, final
 *       {@code score} + {@code feedback} set, {@code graded_at} +
 *       {@code graded_by_user_id} populated.</li>
 *   <li>{@link #EXPIRED} — time limit hit before submission
 *       (BE-7b.4 job scheduler). The attempt is closed without
 *       being submitted; answers are auto-graded up to the
 *       moment of expiry.</li>
 * </ul>
 *
 * <p>Persisted as {@code VARCHAR(16)} via {@code @Enumerated(STRING)}.
 * DB-level valid values enforced by
 * {@code chk_lms_quiz_attempts_status} in
 * {@code V35__create_lms_quizzes.sql}.
 */
public enum AttemptStatus {
	IN_PROGRESS,
	SUBMITTED,
	AUTO_GRADED,
	GRADED,
	EXPIRED
}
