package com.edushift.modules.quizzes.entity;

/**
 * Question type discriminator (Sprint 7b / BE-7b.0).
 *
 * <h3>Storage implications</h3>
 * <ul>
 *   <li>{@link #MC} — multiple choice, 2-6 options in
 *       {@code lms_quiz_options}. Exactly one option has
 *       {@code is_correct=true} (enforced by DB trigger
 *       {@code enforce_one_correct_mc_option}).</li>
 *   <li>{@link #TF} — true/false. Options are implicit
 *       (no rows in {@code lms_quiz_options}). The correct
 *       answer is the boolean column {@code correct_boolean}
 *       on {@code lms_quiz_questions}.</li>
 *   <li>{@link #SHORT_ANSWER} — free-form text. The expected
 *       answer is the array column {@code expected_keywords}
 *       (case-insensitive substring match). Grading is
 *       <strong>manual</strong> in MVP (BE-7b.1); keyword match
 *       is a hint, not the source of truth.</li>
 * </ul>
 *
 * <p>Persisted as {@code VARCHAR(16)} via {@code @Enumerated(STRING)}.
 * DB-level valid values enforced by
 * {@code chk_lms_quiz_questions_type} in
 * {@code V35__create_lms_quizzes.sql}.
 */
public enum QuestionType {
	MC,
	TF,
	SHORT_ANSWER
}
