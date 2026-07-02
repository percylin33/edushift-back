package com.edushift.modules.quizzes.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.quizzes.error.QuizzesErrorCodes;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Quizzes module — custom exception types & factory messages")
class QuizzesExceptionsTest {

    // ------------------------------------------------------------------
    // NotFound family (HTTP 404)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("QuizNotFoundException — 404 + QUIZ_NOT_FOUND + UUID in message")
    void quizNotFound() {
        var ex = new QuizNotFoundException("abc-123");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_NOT_FOUND);
        assertThat(ex.getMessage()).contains("abc-123");
    }

    @Test
    @DisplayName("QuestionNotFoundException — 404 + QUESTION_NOT_FOUND")
    void questionNotFound() {
        var ex = new QuestionNotFoundException("q-1");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.QUESTION_NOT_FOUND);
        assertThat(ex.getMessage()).contains("q-1");
    }

    @Test
    @DisplayName("AttemptNotFoundException — 404 + ATTEMPT_NOT_FOUND")
    void attemptNotFound() {
        var ex = new AttemptNotFoundException("att-1");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.ATTEMPT_NOT_FOUND);
        assertThat(ex.getMessage()).contains("att-1");
    }

    @Test
    @DisplayName("AnswerNotFoundException — 404 + ANSWER_NOT_FOUND")
    void answerNotFound() {
        var ex = new AnswerNotFoundException("ans-1");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.ANSWER_NOT_FOUND);
        assertThat(ex.getMessage()).contains("ans-1");
    }

    @Test
    @DisplayName("SectionNotFoundException — 404 + SECTION_NOT_FOUND")
    void sectionNotFound() {
        var ex = new SectionNotFoundException("sec-1");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.SECTION_NOT_FOUND);
        assertThat(ex.getMessage()).contains("sec-1");
    }

    @Test
    @DisplayName("RubricNotFoundException — 404 + RUBRIC_NOT_FOUND")
    void rubricNotFound() {
        var ex = new RubricNotFoundException("rub-1");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getStatus().value()).isEqualTo(404);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.RUBRIC_NOT_FOUND);
        assertThat(ex.getMessage()).contains("rub-1");
    }

    // ------------------------------------------------------------------
    // Conflict family (HTTP 409)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("QuizNotPublishedException — 409 + QUIZ_NOT_PUBLISHED + state in message")
    void quizNotPublished() {
        var ex = new QuizNotPublishedException("DRAFT");
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getStatus().value()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_NOT_PUBLISHED);
        assertThat(ex.getMessage()).contains("DRAFT");
    }

    @Test
    @DisplayName("AttemptNotInProgressException — 409 + ATTEMPT_NOT_IN_PROGRESS")
    void attemptNotInProgress() {
        var ex = new AttemptNotInProgressException("SUBMITTED");
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getStatus().value()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.ATTEMPT_NOT_IN_PROGRESS);
        assertThat(ex.getMessage()).contains("SUBMITTED");
    }

    @Test
    @DisplayName("AttemptNotSubmittedException — 409 + ATTEMPT_NOT_SUBMITTED")
    void attemptNotSubmitted() {
        var ex = new AttemptNotSubmittedException("IN_PROGRESS");
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getStatus().value()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.ATTEMPT_NOT_SUBMITTED);
        assertThat(ex.getMessage()).contains("IN_PROGRESS");
    }

    @Test
    @DisplayName("AttemptsExhaustedException — 409 + ATTEMPTS_EXHAUSTED + quota in message")
    void attemptsExhausted() {
        var ex = new AttemptsExhaustedException(2, 2);
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getStatus().value()).isEqualTo(409);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.ATTEMPTS_EXHAUSTED);
        assertThat(ex.getMessage()).contains("2").contains("2");
    }

    @Test
    @DisplayName("InvalidQuizStateException — factories")
    void invalidQuizStateFactories() {
        var notDraft = InvalidQuizStateException.notDraft("PUBLISHED");
        assertThat(notDraft.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_NOT_DRAFT);
        assertThat(notDraft.getMessage()).contains("PUBLISHED");

        var notPublished = InvalidQuizStateException.notPublished("DRAFT");
        assertThat(notPublished.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_NOT_PUBLISHED);
        assertThat(notPublished.getMessage()).contains("DRAFT");

        var alreadyClosed = InvalidQuizStateException.alreadyClosed();
        assertThat(alreadyClosed.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_ALREADY_CLOSED);

        var noQuestions = InvalidQuizStateException.noQuestions();
        assertThat(noQuestions.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_HAS_NO_QUESTIONS);

        var raw = new InvalidQuizStateException("X", "y");
        assertThat(raw.getCode()).isEqualTo("X");
        assertThat(raw.getMessage()).isEqualTo("y");
    }

    // ------------------------------------------------------------------
    // BadRequest family (HTTP 400)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RecordEmptyPatchException — 400 + QUIZ_RECORD_EMPTY_PATCH")
    void recordEmptyPatch() {
        var ex = new RecordEmptyPatchException();
        assertThat(ex).isInstanceOf(BadRequestException.class);
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(ex.getCode()).isEqualTo(QuizzesErrorCodes.QUIZ_RECORD_EMPTY_PATCH);
    }

    @Test
    @DisplayName("QuestionValidationException — factories cover every code path")
    void questionValidationFactories() {
        var mc2to6 = QuestionValidationException.mcNeeds2To6Options(1);
        assertThat(mc2to6.getCode()).isEqualTo(QuizzesErrorCodes.MC_QUESTION_NEEDS_2_TO_6_OPTIONS);
        assertThat(mc2to6.getMessage()).contains("1");

        var tf = QuestionValidationException.tfHasOptions();
        assertThat(tf.getCode()).isEqualTo(QuizzesErrorCodes.TF_QUESTION_HAS_OPTIONS);

        var sa = QuestionValidationException.shortAnswerHasOptions();
        assertThat(sa.getCode()).isEqualTo(QuizzesErrorCodes.SHORT_ANSWER_HAS_OPTIONS);

        var oneCorrect = QuestionValidationException.mcNeedsExactlyOneCorrect(2);
        assertThat(oneCorrect.getCode()).isEqualTo(QuizzesErrorCodes.MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT);
        assertThat(oneCorrect.getMessage()).contains("2");

        var blank = QuestionValidationException.blankPrompt();
        assertThat(blank.getCode()).isEqualTo(QuizzesErrorCodes.QUESTION_PROMPT_BLANK);

        var points = QuestionValidationException.pointsOutOfRange(0);
        assertThat(points.getCode()).isEqualTo(QuizzesErrorCodes.QUESTION_POINTS_OUT_OF_RANGE);
        assertThat(points.getMessage()).contains("0");

        var incompatible = QuestionValidationException.questionTypeIncompatible();
        assertThat(incompatible.getCode()).isEqualTo(QuizzesErrorCodes.QUESTION_TYPE_INCOMPATIBLE);

        var raw = new QuestionValidationException("X", "y");
        assertThat(raw.getCode()).isEqualTo("X");
        assertThat(raw.getMessage()).isEqualTo("y");
    }

    // ------------------------------------------------------------------
    // Forbidden family (HTTP 403)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("StudentNotEnrolledException — 403 with section UUID in message")
    void studentNotEnrolled() {
        var ex = new StudentNotEnrolledException("sec-1");
        assertThat(ex).isInstanceOf(ForbiddenException.class);
        assertThat(ex.getStatus().value()).isEqualTo(403);
        assertThat(ex.getCode()).isEqualTo("STUDENT_NOT_ENROLLED_IN_SECTION");
        assertThat(ex.getMessage()).contains("sec-1");
    }
}