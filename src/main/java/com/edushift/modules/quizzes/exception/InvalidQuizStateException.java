package com.edushift.modules.quizzes.exception;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.ConflictException;

/**
 * Thrown when an operation is not valid in the quiz's current
 * lifecycle state (Sprint 7b / BE-7b.1). E.g. trying to add a
 * question to a {@code PUBLISHED} quiz, or publishing a quiz
 * with zero questions.
 *
 * <p>Maps to HTTP 409.
 */
public class InvalidQuizStateException extends ConflictException {

	public InvalidQuizStateException(String errorCode, String message) {
		super(errorCode, message);
	}

	/** Convenience: "this action requires DRAFT" wrapper. */
	public static InvalidQuizStateException notDraft(String currentState) {
		return new InvalidQuizStateException(
				QuizzesErrorCodes.QUIZ_NOT_DRAFT,
				"This action requires the quiz to be in DRAFT state (current: "
						+ currentState + ").");
	}

	/** Convenience: "this action requires PUBLISHED" wrapper. */
	public static InvalidQuizStateException notPublished(String currentState) {
		return new InvalidQuizStateException(
				QuizzesErrorCodes.QUIZ_NOT_PUBLISHED,
				"This action requires the quiz to be in PUBLISHED state (current: "
						+ currentState + ").");
	}

	/** Convenience: "already CLOSED" wrapper. */
	public static InvalidQuizStateException alreadyClosed() {
		return new InvalidQuizStateException(
				QuizzesErrorCodes.QUIZ_ALREADY_CLOSED,
				"Quiz is already CLOSED.");
	}

	/** Convenience: "publish requires at least one question" wrapper. */
	public static InvalidQuizStateException noQuestions() {
		return new InvalidQuizStateException(
				QuizzesErrorCodes.QUIZ_HAS_NO_QUESTIONS,
				"Cannot publish a quiz with zero questions.");
	}
}
