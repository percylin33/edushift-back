package com.edushift.modules.quizzes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Row in the manual grading queue returned by
 * {@code GET /quizzes/{uuid}/grading-queue} (Sprint 7b / BE-7b.2).
 *
 * <p>Each item is a SHORT_ANSWER answer that still needs a
 * teacher's verdict. The {@code quizTitle} and {@code studentId}
 * are inlined for one-shot rendering in the queue UI (FE-7b.3
 * "results + grading panel" screen).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GradingQueueItem(
		UUID answerPublicUuid,
		UUID attemptPublicUuid,
		UUID questionPublicUuid,
		UUID studentUserId,
		String quizTitle,
		String questionPrompt,
		Integer questionPoints,
		String textAnswer
) {

	/**
	 * Convenience: project the queue from a flat list of
	 * {@link AttemptSummary} plus the corresponding answer rows.
	 * Used by the service when the queue is built from a single
	 * quiz's pending answers.
	 */
	public static List<GradingQueueItem> fromRows(
			List<Row> rows) {
		return rows.stream()
				.map(r -> new GradingQueueItem(
						r.answerPublicUuid(),
						r.attemptPublicUuid(),
						r.questionPublicUuid(),
						r.studentUserId(),
						r.quizTitle(),
						r.questionPrompt(),
						r.questionPoints(),
						r.textAnswer()))
				.toList();
	}

	/** Service-side DTO used to build {@link GradingQueueItem}. */
	public record Row(
			UUID answerPublicUuid,
			UUID attemptPublicUuid,
			UUID questionPublicUuid,
			UUID studentUserId,
			String quizTitle,
			String questionPrompt,
			Integer questionPoints,
			String textAnswer
	) {
	}
}
