package com.edushift.modules.quizzes.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * JSON body for {@code POST /v1/lms/attempts/{uuid}/grade-with-rubric}
 * (Sprint 7b / BE-7b.3).
 *
 * <p>For each criterion in the attached rubric, the teacher picks
 * the achievement level (one of the rubric's own
 * {@code level.code}s — typically
 * {@code EN_INICIO | EN_PROCESO | ESPERADO | SOBRESALIENTE}). The
 * service converts the level picks into a single qualitative
 * {@code literal} for the new {@code GradeRecord} (using the best
 * level chosen, lower-cased) and writes an additional
 * {@code grade_records} row anchored to the quiz's derived
 * evaluation.
 *
 * <p>{@code comments} is optional (max 1000 chars; mirrors the
 * {@code grade_records.comments} column).
 */
public record GradeWithRubricRequest(
		@NotEmpty List<CriterionLevelPick> picks,
		@Size(max = 1000) String comments
) {
	/**
	 * Single criterion pick — {@code criterionKey} is the
	 * {@code key} field of the rubric's criterion (the service
	 * re-derives the descriptor and weight from the rubric row;
	 * the FE sends only the key + chosen level code).
	 */
	public record CriterionLevelPick(
			@NotNull String criterionKey,
			@NotNull String levelCode
	) {
	}
}
