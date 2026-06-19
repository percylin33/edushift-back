package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.BadRequestException;

/**
 * Thrown when a quiz-question payload violates structural
 * invariants that the DB CHECK constraints cannot express
 * (Sprint 7b / BE-7b.1).
 *
 * <p>Examples:
 * <ul>
 *   <li>MC question with fewer than 2 or more than 6 options.</li>
 *   <li>TF question with rows in {@code lms_quiz_options}.</li>
 *   <li>SHORT_ANSWER question with rows in {@code lms_quiz_options}.</li>
 *   <li>MC question with 0 or 2+ options flagged {@code is_correct=true}.</li>
 *   <li>Empty {@code prompt}.</li>
 *   <li>{@code points} outside [1, 100].</li>
 * </ul>
 *
 * <p>Maps to HTTP 400.
 */
public class QuestionValidationException extends BadRequestException {

	public QuestionValidationException(String errorCode, String message) {
		super(errorCode, message);
	}

	public static QuestionValidationException mcNeeds2To6Options(int actual) {
		return new QuestionValidationException(
				QuizzesErrorCodes.MC_QUESTION_NEEDS_2_TO_6_OPTIONS,
				"MC questions must have 2 to 6 options (current: " + actual + ").");
	}

	public static QuestionValidationException tfHasOptions() {
		return new QuestionValidationException(
				QuizzesErrorCodes.TF_QUESTION_HAS_OPTIONS,
				"TF questions cannot have options rows; use the correct_boolean field.");
	}

	public static QuestionValidationException shortAnswerHasOptions() {
		return new QuestionValidationException(
				QuizzesErrorCodes.SHORT_ANSWER_HAS_OPTIONS,
				"SHORT_ANSWER questions cannot have options rows; use expected_keywords.");
	}

	public static QuestionValidationException mcNeedsExactlyOneCorrect(int actual) {
		return new QuestionValidationException(
				QuizzesErrorCodes.MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT,
				"MC questions must have exactly one option flagged is_correct=true (current: "
						+ actual + ").");
	}

	public static QuestionValidationException blankPrompt() {
		return new QuestionValidationException(
				QuizzesErrorCodes.QUESTION_PROMPT_BLANK,
				"Question prompt cannot be blank.");
	}

	public static QuestionValidationException pointsOutOfRange(int actual) {
		return new QuestionValidationException(
				QuizzesErrorCodes.QUESTION_POINTS_OUT_OF_RANGE,
				"Question points must be in [1, 100] (current: " + actual + ").");
	}

	public static QuestionValidationException questionTypeIncompatible() {
		return new QuestionValidationException(
				QuizzesErrorCodes.QUESTION_TYPE_INCOMPATIBLE,
				"Payload fields do not match the question type.");
	}
}
