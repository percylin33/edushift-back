package com.edushift.modules.evaluations.entity;

/**
 * Pedagogical kind of an {@link com.edushift.modules.evaluations.entity.Evaluation}
 * (Sprint 5B / BE-5B.1).
 *
 * <p>Each kind has an expected default {@link EvaluationScale}, although
 * the service lets the caller override within a coherent subset
 * (e.g. {@code EXAM} can only be {@code SCORE_0_20}; {@code RUBRIC} can
 * only be one of the literal scales).</p>
 */
public enum EvaluationKind {

	/** Generic homework / task. Default scale: {@code SCORE_0_20}. */
	TASK,

	/** Quick check (e.g. 5-question quiz). Default scale: {@code SCORE_0_20}. */
	QUIZ,

	/** Formal exam. Default scale: {@code SCORE_0_20}. */
	EXAM,

	/** Rubric-driven assessment. Default scale: {@code LITERAL_A_B_C_D}. */
	RUBRIC,

	/**
	 * Competency-tracking assessment (the score is a literal promotion
	 * of whether the competency was achieved). Default scale:
	 * {@code LITERAL_NA} (logrado / en proceso / no lograble).
	 */
	COMPETENCY
}
