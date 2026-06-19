package com.edushift.modules.quizzes.error;

/**
 * Stable error codes for the LMS Quizzes module
 * (Sprint 7b / BE-7b.1). See {@code docs/modules/lms-quizzes.md} (TBD)
 * and the endpoint table in
 * {@link com.edushift.modules.quizzes.controller.QuizController}.
 *
 * <p>Conventions:
 * <ul>
 *   <li>SCREAMING_SNAKE_CASE, verb-noun, ASCII.</li>
 *   <li>Stable across releases (clients match on the string).</li>
 *   <li>Reused from {@code lms_tasks} where the same domain concept
 *       applies (e.g. {@link #SECTION_NOT_FOUND} mirrors
 *       {@code TasksErrorCodes.SECTION_NOT_FOUND} to keep the FE
 *       DRY).</li>
 * </ul>
 */
public final class QuizzesErrorCodes {

    // Reused cross-module (kept here so the FE error mapper has a
    // single import surface for LMS endpoints).
    public static final String SECTION_NOT_FOUND = "SECTION_NOT_FOUND";

    // Quiz-level
    public static final String QUIZ_NOT_FOUND = "QUIZ_NOT_FOUND";
    public static final String QUIZ_NOT_DRAFT = "QUIZ_NOT_DRAFT";
    public static final String QUIZ_NOT_PUBLISHED = "QUIZ_NOT_PUBLISHED";
    public static final String QUIZ_ALREADY_CLOSED = "QUIZ_ALREADY_CLOSED";
    public static final String QUIZ_HAS_NO_QUESTIONS = "QUIZ_HAS_NO_QUESTIONS";
    public static final String QUIZ_PAST_DUE = "QUIZ_PAST_DUE";
    public static final String QUIZ_TIME_LIMIT_OUT_OF_RANGE = "QUIZ_TIME_LIMIT_OUT_OF_RANGE";
    public static final String QUIZ_ATTEMPTS_ALLOWED_OUT_OF_RANGE = "QUIZ_ATTEMPTS_ALLOWED_OUT_OF_RANGE";
    public static final String QUIZ_MAX_SCORE_OUT_OF_RANGE = "QUIZ_MAX_SCORE_OUT_OF_RANGE";
    public static final String QUIZ_RECORD_EMPTY_PATCH = "QUIZ_RECORD_EMPTY_PATCH";

    // Question-level
    public static final String QUESTION_NOT_FOUND = "QUESTION_NOT_FOUND";
    public static final String QUESTION_TYPE_INCOMPATIBLE = "QUESTION_TYPE_INCOMPATIBLE";
    public static final String MC_QUESTION_NEEDS_2_TO_6_OPTIONS = "MC_QUESTION_NEEDS_2_TO_6_OPTIONS";
    public static final String TF_QUESTION_HAS_OPTIONS = "TF_QUESTION_HAS_OPTIONS";
    public static final String SHORT_ANSWER_HAS_OPTIONS = "SHORT_ANSWER_HAS_OPTIONS";
    public static final String QUESTION_PROMPT_BLANK = "QUESTION_PROMPT_BLANK";
    public static final String QUESTION_POINTS_OUT_OF_RANGE = "QUESTION_POINTS_OUT_OF_RANGE";
    public static final String QUESTION_POSITION_CONFLICT = "QUESTION_POSITION_CONFLICT";

    // Option-level
    public static final String OPTION_NOT_FOUND = "OPTION_NOT_FOUND";
    public static final String OPTION_LABEL_BLANK = "OPTION_LABEL_BLANK";
    public static final String OPTION_BELONGS_TO_DIFFERENT_QUESTION = "OPTION_BELONGS_TO_DIFFERENT_QUESTION";
    public static final String MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT = "MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT";

    // Attempt-level (BE-7b.2 will use these; declared now to keep the
    // FE/BE contract stable from the start of FE-7b.1).
    public static final String ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    public static final String ATTEMPT_NOT_IN_PROGRESS = "ATTEMPT_NOT_IN_PROGRESS";
    public static final String ATTEMPT_NOT_SUBMITTED = "ATTEMPT_NOT_SUBMITTED";
    public static final String ATTEMPT_EXPIRED = "ATTEMPT_EXPIRED";
    public static final String ATTEMPTS_EXHAUSTED = "ATTEMPTS_EXHAUSTED";
    public static final String ANSWER_NOT_FOUND = "ANSWER_NOT_FOUND";

    // Auto-grading
    public static final String GRADE_OUT_OF_RANGE = "GRADE_OUT_OF_RANGE";
    public static final String SHORT_ANSWER_GRADING_REQUIRED = "SHORT_ANSWER_GRADING_REQUIRED";
    public static final String INCONSISTENT_PAYLOAD = "INCONSISTENT_PAYLOAD";

    // Rubric bridge (Sprint 7b / BE-7b.3)
    public static final String RUBRIC_NOT_FOUND = "RUBRIC_NOT_FOUND";
    public static final String QUIZ_HAS_NO_RUBRIC = "QUIZ_HAS_NO_RUBRIC";
    public static final String TEACHER_NOT_ASSIGNED_TO_SECTION = "TEACHER_NOT_ASSIGNED_TO_SECTION";
    public static final String RUBRIC_LEVEL_INVALID = "RUBRIC_LEVEL_INVALID";

    private QuizzesErrorCodes() {
        // utility class
    }
}
