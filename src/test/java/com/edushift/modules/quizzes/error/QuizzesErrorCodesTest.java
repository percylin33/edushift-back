package com.edushift.modules.quizzes.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuizzesErrorCodes — stable error codes for the LMS Quizzes module")
class QuizzesErrorCodesTest {

    @Test
    @DisplayName("cross-module: SECTION_NOT_FOUND mirrors tasks module")
    void sectionNotFound() {
        assertThat(QuizzesErrorCodes.SECTION_NOT_FOUND).isEqualTo("SECTION_NOT_FOUND");
    }

    @Test
    @DisplayName("quiz-level codes")
    void quizLevelCodes() {
        assertThat(QuizzesErrorCodes.QUIZ_NOT_FOUND).isEqualTo("QUIZ_NOT_FOUND");
        assertThat(QuizzesErrorCodes.QUIZ_NOT_DRAFT).isEqualTo("QUIZ_NOT_DRAFT");
        assertThat(QuizzesErrorCodes.QUIZ_NOT_PUBLISHED).isEqualTo("QUIZ_NOT_PUBLISHED");
        assertThat(QuizzesErrorCodes.QUIZ_ALREADY_CLOSED).isEqualTo("QUIZ_ALREADY_CLOSED");
        assertThat(QuizzesErrorCodes.QUIZ_HAS_NO_QUESTIONS).isEqualTo("QUIZ_HAS_NO_QUESTIONS");
        assertThat(QuizzesErrorCodes.QUIZ_PAST_DUE).isEqualTo("QUIZ_PAST_DUE");
        assertThat(QuizzesErrorCodes.QUIZ_TIME_LIMIT_OUT_OF_RANGE).isEqualTo("QUIZ_TIME_LIMIT_OUT_OF_RANGE");
        assertThat(QuizzesErrorCodes.QUIZ_ATTEMPTS_ALLOWED_OUT_OF_RANGE).isEqualTo("QUIZ_ATTEMPTS_ALLOWED_OUT_OF_RANGE");
        assertThat(QuizzesErrorCodes.QUIZ_MAX_SCORE_OUT_OF_RANGE).isEqualTo("QUIZ_MAX_SCORE_OUT_OF_RANGE");
        assertThat(QuizzesErrorCodes.QUIZ_RECORD_EMPTY_PATCH).isEqualTo("QUIZ_RECORD_EMPTY_PATCH");
    }

    @Test
    @DisplayName("question-level codes")
    void questionLevelCodes() {
        assertThat(QuizzesErrorCodes.QUESTION_NOT_FOUND).isEqualTo("QUESTION_NOT_FOUND");
        assertThat(QuizzesErrorCodes.QUESTION_TYPE_INCOMPATIBLE).isEqualTo("QUESTION_TYPE_INCOMPATIBLE");
        assertThat(QuizzesErrorCodes.MC_QUESTION_NEEDS_2_TO_6_OPTIONS).isEqualTo("MC_QUESTION_NEEDS_2_TO_6_OPTIONS");
        assertThat(QuizzesErrorCodes.TF_QUESTION_HAS_OPTIONS).isEqualTo("TF_QUESTION_HAS_OPTIONS");
        assertThat(QuizzesErrorCodes.SHORT_ANSWER_HAS_OPTIONS).isEqualTo("SHORT_ANSWER_HAS_OPTIONS");
        assertThat(QuizzesErrorCodes.QUESTION_PROMPT_BLANK).isEqualTo("QUESTION_PROMPT_BLANK");
        assertThat(QuizzesErrorCodes.QUESTION_POINTS_OUT_OF_RANGE).isEqualTo("QUESTION_POINTS_OUT_OF_RANGE");
        assertThat(QuizzesErrorCodes.QUESTION_POSITION_CONFLICT).isEqualTo("QUESTION_POSITION_CONFLICT");
    }

    @Test
    @DisplayName("option-level codes")
    void optionLevelCodes() {
        assertThat(QuizzesErrorCodes.OPTION_NOT_FOUND).isEqualTo("OPTION_NOT_FOUND");
        assertThat(QuizzesErrorCodes.OPTION_LABEL_BLANK).isEqualTo("OPTION_LABEL_BLANK");
        assertThat(QuizzesErrorCodes.OPTION_BELONGS_TO_DIFFERENT_QUESTION).isEqualTo("OPTION_BELONGS_TO_DIFFERENT_QUESTION");
        assertThat(QuizzesErrorCodes.MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT).isEqualTo("MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT");
    }

    @Test
    @DisplayName("attempt-level codes")
    void attemptLevelCodes() {
        assertThat(QuizzesErrorCodes.ATTEMPT_NOT_FOUND).isEqualTo("ATTEMPT_NOT_FOUND");
        assertThat(QuizzesErrorCodes.ATTEMPT_NOT_IN_PROGRESS).isEqualTo("ATTEMPT_NOT_IN_PROGRESS");
        assertThat(QuizzesErrorCodes.ATTEMPT_NOT_SUBMITTED).isEqualTo("ATTEMPT_NOT_SUBMITTED");
        assertThat(QuizzesErrorCodes.ATTEMPT_EXPIRED).isEqualTo("ATTEMPT_EXPIRED");
        assertThat(QuizzesErrorCodes.ATTEMPTS_EXHAUSTED).isEqualTo("ATTEMPTS_EXHAUSTED");
        assertThat(QuizzesErrorCodes.ANSWER_NOT_FOUND).isEqualTo("ANSWER_NOT_FOUND");
    }

    @Test
    @DisplayName("auto-grading codes")
    void autoGradingCodes() {
        assertThat(QuizzesErrorCodes.GRADE_OUT_OF_RANGE).isEqualTo("GRADE_OUT_OF_RANGE");
        assertThat(QuizzesErrorCodes.SHORT_ANSWER_GRADING_REQUIRED).isEqualTo("SHORT_ANSWER_GRADING_REQUIRED");
        assertThat(QuizzesErrorCodes.INCONSISTENT_PAYLOAD).isEqualTo("INCONSISTENT_PAYLOAD");
    }

    @Test
    @DisplayName("rubric bridge codes (BE-7b.3)")
    void rubricBridgeCodes() {
        assertThat(QuizzesErrorCodes.RUBRIC_NOT_FOUND).isEqualTo("RUBRIC_NOT_FOUND");
        assertThat(QuizzesErrorCodes.QUIZ_HAS_NO_RUBRIC).isEqualTo("QUIZ_HAS_NO_RUBRIC");
        assertThat(QuizzesErrorCodes.TEACHER_NOT_ASSIGNED_TO_SECTION).isEqualTo("TEACHER_NOT_ASSIGNED_TO_SECTION");
        assertThat(QuizzesErrorCodes.RUBRIC_LEVEL_INVALID).isEqualTo("RUBRIC_LEVEL_INVALID");
    }

    @Test
    @DisplayName("utility class — cannot be instantiated via reflection")
    void utilityClass() throws Exception {
        var ctor = QuizzesErrorCodes.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var instance = ctor.newInstance();
        assertThat(instance).isNotNull();
    }
}