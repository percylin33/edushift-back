package com.edushift.modules.quizzes.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.QuizStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Quizzes DTOs — record accessors + Bean Validation constraints")
class QuizDtoTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    // ------------------------------------------------------------------
    // Quiz-level
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CreateQuizRequest — valid payload")
    void createQuizRequest_valid() {
        var r = new CreateQuizRequest("T", "D", Instant.now().plusSeconds(60),
                30, 2, 100, null);
        assertThat(VALIDATOR.validate(r)).isEmpty();
        assertThat(r.title()).isEqualTo("T");
        assertThat(r.maxAttempts()).isEqualTo(2);
        assertThat(r.questions()).isNull();
    }

    @Test
    @DisplayName("CreateQuizRequest — blank title violates @NotBlank")
    void createQuizRequest_blankTitle() {
        var r = new CreateQuizRequest("", "D", null, 30, 2, 100, null);
        assertThat(VALIDATOR.validate(r)).isNotEmpty();
    }

    @Test
    @DisplayName("CreateQuizRequest — maxAttempts must be 1..10")
    void createQuizRequest_maxAttemptsRange() {
        var tooLow = new CreateQuizRequest("T", null, null, null, 0, 100, null);
        var tooHigh = new CreateQuizRequest("T", null, null, null, 11, 100, null);
        assertThat(VALIDATOR.validate(tooLow)).isNotEmpty();
        assertThat(VALIDATOR.validate(tooHigh)).isNotEmpty();
    }

    @Test
    @DisplayName("UpdateQuizRequest — all-null is a valid record (service rejects with 400)")
    void updateQuizRequest_allNull() {
        var r = new UpdateQuizRequest(null, null, null, null, null, null);
        assertThat(VALIDATOR.validate(r)).isEmpty();
    }

    @Test
    @DisplayName("QuizResponse — round-trip accessors")
    void quizResponse() {
        var q = UUID.randomUUID();
        var r = new QuizResponse(q, q, "T", "D", QuizStatus.PUBLISHED,
                Instant.now(), 30, 2, 100, q, Instant.now(), Instant.now(),
                null, null, 3, 100, true, List.of(), Instant.now(), Instant.now());
        assertThat(r.publicUuid()).isEqualTo(q);
        assertThat(r.status()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(r.questionCount()).isEqualTo(3);
        assertThat(r.totalPoints()).isEqualTo(100);
        assertThat(r.revealCorrectness()).isTrue();
        assertThat(r.questions()).isEmpty();
    }

    @Test
    @DisplayName("QuizSummary — slim projection")
    void quizSummary() {
        var q = UUID.randomUUID();
        var s = new QuizSummary(q, "T", QuizStatus.DRAFT, Instant.now(),
                30, 1, 100, q, 5, 50, Instant.now());
        assertThat(s.publicUuid()).isEqualTo(q);
        assertThat(s.status()).isEqualTo(QuizStatus.DRAFT);
        assertThat(s.questionCount()).isEqualTo(5);
        assertThat(s.totalPoints()).isEqualTo(50);
    }

    @Test
    @DisplayName("PublishQuizRequest — empty record")
    void publishQuizRequest() {
        var r = new PublishQuizRequest();
        assertThat(r).isNotNull();
    }

    // ------------------------------------------------------------------
    // Question-level
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CreateQuestionRequest — MC happy path")
    void createQuestionRequest_mc() {
        var opts = List.of(
                new CreateOptionRequest("A", true, null),
                new CreateOptionRequest("B", false, null));
        var q = new CreateQuestionRequest(QuestionType.MC, "Q?", 5, 1,
                null, null, null, opts);
        assertThat(VALIDATOR.validate(q)).isEmpty();
        assertThat(q.options()).hasSize(2);
    }

    @Test
    @DisplayName("CreateQuestionRequest — blank prompt is rejected")
    void createQuestionRequest_blankPrompt() {
        var q = new CreateQuestionRequest(QuestionType.TF, "  ", 1, null,
                null, null, true, null);
        assertThat(VALIDATOR.validate(q)).isNotEmpty();
    }

    @Test
    @DisplayName("CreateQuestionRequest — points out of [1,100]")
    void createQuestionRequest_pointsRange() {
        var tooLow = new CreateQuestionRequest(QuestionType.MC, "Q", 0, null,
                null, null, null, List.of(
                        new CreateOptionRequest("A", true, null),
                        new CreateOptionRequest("B", false, null)));
        var tooHigh = new CreateQuestionRequest(QuestionType.MC, "Q", 101, null,
                null, null, null, List.of(
                        new CreateOptionRequest("A", true, null),
                        new CreateOptionRequest("B", false, null)));
        assertThat(VALIDATOR.validate(tooLow)).isNotEmpty();
        assertThat(VALIDATOR.validate(tooHigh)).isNotEmpty();
    }

    @Test
    @DisplayName("CreateOptionRequest — null isCorrect is rejected")
    void createOptionRequest_nullIsCorrect() {
        var o = new CreateOptionRequest("A", null, null);
        assertThat(VALIDATOR.validate(o)).isNotEmpty();
    }

    @Test
    @DisplayName("AddOptionRequest — wraps a non-null option")
    void addOptionRequest() {
        var o = new AddOptionRequest(new CreateOptionRequest("A", true, null));
        assertThat(o.option()).isNotNull();
        assertThat(VALIDATOR.validate(o)).isEmpty();
    }

    @Test
    @DisplayName("QuestionResponse — option list round-trip")
    void questionResponse() {
        var opts = List.of(
                new OptionResponse(UUID.randomUUID(), "A", true, null, 1),
                new OptionResponse(UUID.randomUUID(), "B", false, null, 2));
        var q = new QuestionResponse(UUID.randomUUID(), QuestionType.MC,
                "Q?", 5, 1, null, null, null, opts);
        assertThat(q.type()).isEqualTo(QuestionType.MC);
        assertThat(q.options()).hasSize(2);
        assertThat(q.points()).isEqualTo(5);
    }

    @Test
    @DisplayName("OptionResponse — Boolean isCorrect tri-state")
    void optionResponse() {
        var o = new OptionResponse(UUID.randomUUID(), "A", null, null, 1);
        assertThat(o.isCorrect()).isNull();
        assertThat(o.position()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Attempt-level
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AttemptResponse — reveal flag is preserved")
    void attemptResponse() {
        var ans = List.of(new AnswerResponse(UUID.randomUUID(), UUID.randomUUID(),
                null, null, "Paris", true, 5, UUID.randomUUID(), Instant.now(),
                Instant.now()));
        var r = new AttemptResponse(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                AttemptStatus.GRADED, Instant.now(), Instant.now(), Instant.now(),
                60, 5, 0, 5, 100, UUID.randomUUID(), Instant.now(), "fb",
                true, ans, Instant.now(), Instant.now());
        assertThat(r.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(r.attemptNumber()).isEqualTo((short) 1);
        assertThat(r.timeRemainingSeconds()).isEqualTo(60);
        assertThat(r.revealCorrectness()).isTrue();
        assertThat(r.answers()).hasSize(1);
    }

    @Test
    @DisplayName("AttemptSummary — pendingAnswerCount passthrough")
    void attemptSummary() {
        var s = new AttemptSummary(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), (short) 1, AttemptStatus.AUTO_GRADED,
                5, 0, 5, 100, 3, Instant.now(), Instant.now(), null, Instant.now());
        assertThat(s.status()).isEqualTo(AttemptStatus.AUTO_GRADED);
        assertThat(s.pendingAnswerCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("AnswerInput — textAnswer under 5000 chars")
    void answerInput() {
        var a = new AnswerInput(UUID.randomUUID(), QuestionType.SHORT_ANSWER,
                null, null, "x".repeat(5000));
        assertThat(VALIDATOR.validate(a)).isEmpty();
        var a2 = new AnswerInput(UUID.randomUUID(), QuestionType.SHORT_ANSWER,
                null, null, "x".repeat(5001));
        assertThat(VALIDATOR.validate(a2)).isNotEmpty();
    }

    @Test
    @DisplayName("SaveAnswersRequest — empty list rejected")
    void saveAnswersRequest_empty() {
        var r = new SaveAnswersRequest(List.of());
        assertThat(VALIDATOR.validate(r)).isNotEmpty();
    }

    @Test
    @DisplayName("SaveAnswersRequest — valid list passes")
    void saveAnswersRequest_ok() {
        var r = new SaveAnswersRequest(List.of(
                new AnswerInput(UUID.randomUUID(), QuestionType.SHORT_ANSWER,
                        null, null, "x")));
        assertThat(VALIDATOR.validate(r)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Grading
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GradeAnswerRequest — range [0,1000]")
    void gradeAnswerRequest() {
        var ok = new GradeAnswerRequest(50);
        var lo = new GradeAnswerRequest(-1);
        var hi = new GradeAnswerRequest(1001);
        assertThat(VALIDATOR.validate(ok)).isEmpty();
        assertThat(VALIDATOR.validate(lo)).isNotEmpty();
        assertThat(VALIDATOR.validate(hi)).isNotEmpty();
    }

    @Test
    @DisplayName("ManualGradeAnswerRequest — answerPublicUuid nullable, pointsAwarded required")
    void manualGradeAnswerRequest() {
        var ok = new ManualGradeAnswerRequest(UUID.randomUUID(), 5);
        var noPoints = new ManualGradeAnswerRequest(UUID.randomUUID(), null);
        assertThat(VALIDATOR.validate(ok)).isEmpty();
        assertThat(VALIDATOR.validate(noPoints)).isNotEmpty();
    }

    @Test
    @DisplayName("ManualGradeAttemptRequest — feedback <= 2000")
    void manualGradeAttemptRequest() {
        var ok = new ManualGradeAttemptRequest(List.of(
                new ManualGradeAnswerRequest(UUID.randomUUID(), 5)), "ok");
        var tooLong = new ManualGradeAttemptRequest(null, "x".repeat(2001));
        assertThat(VALIDATOR.validate(ok)).isEmpty();
        assertThat(VALIDATOR.validate(tooLong)).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // Rubric bridge
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AttachRubricRequest — null UUID rejected")
    void attachRubricRequest() {
        var ok = new AttachRubricRequest(UUID.randomUUID());
        var bad = new AttachRubricRequest(null);
        assertThat(VALIDATOR.validate(ok)).isEmpty();
        assertThat(VALIDATOR.validate(bad)).isNotEmpty();
    }

    @Test
    @DisplayName("GradeWithRubricRequest — picks required, comments optional")
    void gradeWithRubricRequest() {
        var ok = new GradeWithRubricRequest(
                List.of(new GradeWithRubricRequest.CriterionLevelPick("c1", "A")),
                "good");
        var emptyPicks = new GradeWithRubricRequest(List.of(), null);
        var tooLong = new GradeWithRubricRequest(
                List.of(new GradeWithRubricRequest.CriterionLevelPick("c1", "A")),
                "x".repeat(1001));
        assertThat(VALIDATOR.validate(ok)).isEmpty();
        assertThat(VALIDATOR.validate(emptyPicks)).isNotEmpty();
        assertThat(VALIDATOR.validate(tooLong)).isNotEmpty();
    }

    @Test
    @DisplayName("GradingQueueItem — fromRows projects a list of Rows")
    void gradingQueueItem_fromRows() {
        var rows = List.of(
                new GradingQueueItem.Row(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), "Quiz", "Q?", 5, "Paris"));
        var items = GradingQueueItem.fromRows(rows);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).quizTitle()).isEqualTo("Quiz");
        assertThat(items.get(0).textAnswer()).isEqualTo("Paris");
    }
}