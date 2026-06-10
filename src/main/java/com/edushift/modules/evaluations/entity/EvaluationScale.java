package com.edushift.modules.evaluations.entity;

/**
 * Grading scale of an {@link com.edushift.modules.evaluations.entity.Evaluation}
 * (Sprint 5B / BE-5B.1). Determines the shape of
 * {@code GradeRecord.score} vs {@code GradeRecord.literal} in BE-5B.3.
 *
 * <h3>Numeric</h3>
 * <ul>
 *   <li>{@link #SCORE_0_20} — numeric score in [0, 20]. Most common in
 *       the LATAM school system. Two decimals.</li>
 * </ul>
 *
 * <h3>Literal</h3>
 * <ul>
 *   <li>{@link #LITERAL_AD} — binary "A logrado" / "D en proceso" (or
 *       "AD" = muy logrado).</li>
 *   <li>{@link #LITERAL_NA} — "A" / "NA" (logrado / no logrado) — used
 *       for competency tracking.</li>
 *   <li>{@link #LITERAL_A_B_C_D} — four-level literal grading, the
 *       classic qualitative scale.</li>
 * </ul>
 */
public enum EvaluationScale {

	/** Numeric scale [0, 20] with up to 2 decimals. */
	SCORE_0_20,

	/** Binary literal: "AD" (logrado destacado) or "A" (logrado). */
	LITERAL_AD,

	/** Binary literal: "A" (logrado) or "NA" (no logrado). */
	LITERAL_NA,

	/** Four-level literal: "A", "B", "C", "D". */
	LITERAL_A_B_C_D
}
