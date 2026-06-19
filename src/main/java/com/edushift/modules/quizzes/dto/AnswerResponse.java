package com.edushift.modules.quizzes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * One answer row in {@link AttemptResponse}
 * (Sprint 7b / BE-7b.2).
 *
 * <p>Mutation of payload (per question type) follows the same
 * mutually-exclusive rule as the entity:
 * <ul>
 *   <li>MC: {@code selectedOptionId} non-null.</li>
 *   <li>TF: {@code selectedBoolean} non-null.</li>
 *   <li>SHORT_ANSWER: {@code textAnswer} non-null.</li>
 * </ul>
 *
 * <p>Grading fields ({@code correct}, {@code pointsAwarded},
 * {@code gradedByUserId}, {@code gradedAt}) are only populated
 * after grading has happened. For taker responses (player UI) the
 * service flips {@code revealCorrectness=false} on the parent
 * {@link AttemptResponse} and the mapper drops the grade state
 * from the answer rows; the FE just shows "saved" / "graded".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerResponse(
		UUID publicUuid,
		UUID questionPublicUuid,
		UUID selectedOptionId,
		Boolean selectedBoolean,
		String textAnswer,
		Boolean correct,
		Integer pointsAwarded,
		UUID gradedByUserId,
		Instant gradedAt,
		Instant updatedAt
) {
}
