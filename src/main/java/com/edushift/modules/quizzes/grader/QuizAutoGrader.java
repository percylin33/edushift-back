package com.edushift.modules.quizzes.grader;

import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Auto-grader for {@code MC}, {@code TF} and {@code SHORT_ANSWER}
 * question types (Sprint 7b / BE-7b.1 + BE-7b.2).
 *
 * <h3>Algorithm</h3>
 * <ul>
 *   <li><strong>MC</strong>: an answer is correct iff
 *       {@code answer.selectedOptionId} matches the public UUID
 *       of exactly the option flagged {@code isCorrect = true}
 *       (resolved through the supplied option lookup). Awarded
 *       points: {@code question.points} on hit, {@code 0}
 *       otherwise.</li>
 *   <li><strong>TF</strong>: an answer is correct iff
 *       {@code answer.selectedBoolean == question.correctBoolean}.
 *       Awarded points: {@code question.points} on hit,
 *       {@code 0} otherwise.</li>
 *   <li><strong>SHORT_ANSWER</strong> (BE-7b.2): an answer is
 *       correct iff the case-insensitive {@code text_answer}
 *       contains <em>every</em> token in
 *       {@code question.expectedKeywords} as a substring. Empty
 *       {@code expectedKeywords} → any non-blank text counts as
 *       correct (so teachers can use it as a hint while still
 *       routing through the manual queue). The full grading is
 *       still <strong>manual</strong> in MVP; this auto-grade
 *       only seeds {@code points_awarded} for the row so the
 *       service can show "match: 3/3 keywords" in the FE
 *       (DEBT-BE-7B-3 covers eventual fuzzy/semantic matching).</li>
 * </ul>
 *
 * <h3>Why a lookup callback?</h3>
 * The {@link QuizQuestion} entity intentionally has no JPA
 * relationship with {@link QuizOption} (it would require a
 * bidirectional mapping and cascade rules we don't need at the
 * DB level &mdash; {@code lms_quiz_options.question_id} is enough).
 * To keep the grader a pure function, callers pass a function
 * that resolves {@code publicUuid → QuizOption} from the
 * appropriate scope (section repository, in-memory list, test
 * fixture, ...). This also keeps the grader testable without a
 * full JPA context.
 *
 * <h3>Side effects</h3>
 * The grader mutates the {@link QuizAnswer} instance in place
 * (sets {@code isCorrect} and {@code pointsAwarded}). Callers
 * are responsible for persisting the change.
 */
public final class QuizAutoGrader {

	private QuizAutoGrader() {
		// utility class
	}

	/**
	 * Grade a single MC answer. Returns the same answer (mutated)
	 * so callers can chain.
	 *
	 * @param answer           the answer being graded (must have
	 *                         {@code selectedOptionId} set).
	 * @param question         the question the answer belongs to.
	 * @param optionResolver   function resolving a public option
	 *                         UUID → its {@link QuizOption}. Used
	 *                         to look up the selected option and
	 *                         the canonical correct option. In
	 *                         production this is typically
	 *                         {@code id → optionRepository.findByPublicUuid(id)}.
	 * @return the same {@link QuizAnswer} with {@code isCorrect}
	 *         and {@code pointsAwarded} set, or {@code null} if
	 *         the question type is not auto-gradable.
	 */
	public static QuizAnswer grade(QuizAnswer answer, QuizQuestion question,
			Function<UUID, QuizOption> optionResolver) {
		if (answer == null || question == null) {
			return null;
		}
		return switch (question.getQuestionType()) {
			case MC -> gradeMc(answer, question, optionResolver);
			case TF -> gradeTf(answer, question);
			case SHORT_ANSWER -> gradeShortAnswer(answer, question);
		};
	}

	/**
	 * Convenience overload: grade an MC answer when the caller
	 * already has the full options list in memory (e.g. a unit
	 * test or a service that did the lookup eagerly).
	 */
	public static QuizAnswer grade(QuizAnswer answer, QuizQuestion question,
			List<QuizOption> options) {
		return grade(answer, question, id -> findById(options, id));
	}

	private static QuizOption findById(List<QuizOption> options, UUID id) {
		if (options == null || id == null) {
			return null;
		}
		for (QuizOption o : options) {
			if (id.equals(o.getPublicUuid())) {
				return o;
			}
		}
		return null;
	}

	private static QuizAnswer gradeMc(QuizAnswer answer, QuizQuestion q,
			Function<UUID, QuizOption> optionResolver) {
		UUID selectedId = answer.getSelectedOptionId();
		if (selectedId == null) {
			answer.setCorrect(false);
			answer.setPointsAwarded((short) 0);
			return answer;
		}
		QuizOption selected = optionResolver.apply(selectedId);
		boolean correct = selected != null && selected.isCorrect();
		applyVerdict(answer, q, correct);
		return answer;
	}

	private static QuizAnswer gradeTf(QuizAnswer answer, QuizQuestion q) {
		Boolean selected = answer.getSelectedBoolean();
		Boolean correct = q.getCorrectBoolean();
		boolean verdict = selected != null
				&& correct != null
				&& selected.equals(correct);
		applyVerdict(answer, q, verdict);
		return answer;
	}

	/**
	 * SHORT_ANSWER auto-grading: every non-blank token in
	 * {@code question.expectedKeywords} must appear as a
	 * case-insensitive substring in {@code answer.textAnswer}.
	 *
	 * <p>Semantics:
	 * <ul>
	 *   <li>Empty {@code text_answer} → not correct, 0 points.</li>
	 *   <li>Empty / null {@code expectedKeywords} → any non-blank
	 *       {@code text_answer} counts as correct (full points)
	 *       so the manual queue still gets a useful seed value.</li>
	 *   <li>Otherwise: every keyword must be present (case-insensitive,
	 *       substring match). All → correct, full points. Any missing
	 *       → not correct, 0 points.</li>
	 * </ul>
	 *
	 * <p>The full grading is still <em>manual</em> in MVP: the
	 * service uses this auto-grade to seed
	 * {@code pointsAwarded} and the teacher can override it via
	 * the grading queue (BE-7b.2 manual grade endpoint).
	 */
	static QuizAnswer gradeShortAnswer(QuizAnswer answer, QuizQuestion q) {
		String text = answer.getTextAnswer();
		String[] kws = q.getExpectedKeywords();
		boolean verdict = isShortAnswerMatch(text, kws);
		applyVerdict(answer, q, verdict);
		return answer;
	}

	/**
	 * Pure matcher exposed for unit testing: returns true iff
	 * every keyword in {@code keywords} is a case-insensitive
	 * substring of {@code text}. Empty / null {@code text} never
	 * matches. Empty / null {@code keywords} matches any non-blank
	 * text.
	 */
	static boolean isShortAnswerMatch(String text, String[] keywords) {
		if (text == null || text.isBlank()) {
			return false;
		}
		if (keywords == null || keywords.length == 0) {
			return true;
		}
		String lower = text.toLowerCase();
		for (String kw : keywords) {
			if (kw == null || kw.isBlank()) {
				continue;
			}
			if (lower.indexOf(kw.trim().toLowerCase()) < 0) {
				return false;
			}
		}
		return true;
	}

	private static void applyVerdict(QuizAnswer answer, QuizQuestion q,
			boolean correct) {
		answer.setCorrect(correct);
		answer.setPointsAwarded(correct
				? (q.getPoints() == null ? (short) 0 : q.getPoints())
				: (short) 0);
	}

	/**
	 * Returns true when the supplied type is handled by this grader.
	 * Since BE-7b.2 all three question types (MC, TF, SHORT_ANSWER)
	 * are auto-gradable. For SHORT_ANSWER the auto-grade is only
	 * a seed value; the final verdict comes from the teacher via
	 * the manual grading queue.
	 */
	public static boolean isAutoGradable(QuestionType type) {
		return type == QuestionType.MC
				|| type == QuestionType.TF
				|| type == QuestionType.SHORT_ANSWER;
	}
}
