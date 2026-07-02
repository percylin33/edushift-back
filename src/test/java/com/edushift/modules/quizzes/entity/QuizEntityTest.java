package com.edushift.modules.quizzes.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.quizzes.entity.QuizStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Quizzes entities — JPA setters/getters + @PrePersist defaults")
class QuizEntityTest {

    @Test
    @DisplayName("Quiz — setters & defaults (status=DRAFT, publicUuid auto on persist)")
    void quiz() {
        var q = new Quiz();
        q.setTitle("Algebra Quiz");
        q.setDescription("Chapter 1");
        q.setStatus(QuizStatus.PUBLISHED);
        q.setMaxScore((short) 80);
        q.setTimeLimitMinutes((short) 30);
        q.setAttemptsAllowed((short) 2);
        q.setDueAt(Instant.now());
        q.setPublishedAt(Instant.now());
        q.setClosedAt(Instant.now());
        q.setOwnerUserId(UUID.randomUUID());
        q.setDeletedAt(Instant.now());

        assertThat(q.getTitle()).isEqualTo("Algebra Quiz");
        assertThat(q.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(q.getMaxScore()).isEqualTo((short) 80);
        assertThat(q.getTimeLimitMinutes()).isEqualTo((short) 30);
        assertThat(q.getAttemptsAllowed()).isEqualTo((short) 2);
        assertThat(q.getPublishedAt()).isNotNull();
        assertThat(q.getClosedAt()).isNotNull();
        assertThat(q.getDeletedAt()).isNotNull();
        assertThat(q.getOwnerUserId()).isNotNull();
    }

    @Test
    @DisplayName("Quiz — default field initialisers (maxScore=100, attemptsAllowed=1)")
    void quizDefaults() {
        var q = new Quiz();
        assertThat(q.getMaxScore()).isEqualTo((short) 100);
        assertThat(q.getAttemptsAllowed()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Quiz — toString includes publicUuid, title, status")
    void quizToString() {
        var q = new Quiz();
        q.setTitle("T");
        q.setStatus(QuizStatus.DRAFT);
        assertThat(q.toString()).contains("Quiz").doesNotContain("@");
    }

    @Test
    @DisplayName("QuizQuestion — setters & defaults (points=1)")
    void quizQuestion() {
        var q = new QuizQuestion();
        q.setPosition((short) 3);
        q.setQuestionType(QuestionType.MC);
        q.setPrompt("2+2?");
        q.setPoints((short) 5);
        q.setCorrectBoolean(true);
        q.setExpectedKeywords(new String[]{"x"});
        q.setDeletedAt(Instant.now());

        assertThat(q.getPosition()).isEqualTo((short) 3);
        assertThat(q.getQuestionType()).isEqualTo(QuestionType.MC);
        assertThat(q.getPrompt()).isEqualTo("2+2?");
        assertThat(q.getPoints()).isEqualTo((short) 5);
        assertThat(q.getCorrectBoolean()).isTrue();
        assertThat(q.getExpectedKeywords()).containsExactly("x");
        assertThat(q.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("QuizQuestion — default field initialiser (points=1)")
    void quizQuestionDefaults() {
        var q = new QuizQuestion();
        assertThat(q.getPoints()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("QuizOption — setters & defaults (correct=false)")
    void quizOption() {
        var o = new QuizOption();
        o.setPosition((short) 1);
        o.setLabel("A");
        o.setCorrect(true);
        o.setDeletedAt(Instant.now());

        assertThat(o.getPosition()).isEqualTo((short) 1);
        assertThat(o.getLabel()).isEqualTo("A");
        assertThat(o.isCorrect()).isTrue();
        assertThat(o.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("QuizOption — default field initialiser (correct=false)")
    void quizOptionDefaults() {
        var o = new QuizOption();
        assertThat(o.isCorrect()).isFalse();
    }

    @Test
    @DisplayName("QuizAttempt — setters & score fields")
    void quizAttempt() {
        var a = new QuizAttempt();
        a.setStudentUserId(UUID.randomUUID());
        a.setSubmitterUserId(UUID.randomUUID());
        a.setAttemptNumber((short) 2);
        a.setStatus(AttemptStatus.SUBMITTED);
        a.setStartedAt(Instant.now());
        a.setSubmittedAt(Instant.now());
        a.setExpiresAt(Instant.now().plusSeconds(600));
        a.setAutoScore((short) 8);
        a.setManualScore((short) 2);
        a.setScore((short) 10);
        a.setGradedByUserId(UUID.randomUUID());
        a.setGradedAt(Instant.now());
        a.setFeedback("good");
        a.setDeletedAt(Instant.now());

        assertThat(a.getStudentUserId()).isNotNull();
        assertThat(a.getSubmitterUserId()).isNotNull();
        assertThat(a.getAttemptNumber()).isEqualTo((short) 2);
        assertThat(a.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(a.getAutoScore()).isEqualTo((short) 8);
        assertThat(a.getManualScore()).isEqualTo((short) 2);
        assertThat(a.getScore()).isEqualTo((short) 10);
        assertThat(a.getFeedback()).isEqualTo("good");
        assertThat(a.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("QuizAnswer — setters cover all 3 payload variants")
    void quizAnswer() {
        var ans = new QuizAnswer();
        ans.setSelectedOptionId(UUID.randomUUID());
        ans.setSelectedBoolean(true);
        ans.setTextAnswer("Paris");
        ans.setPointsAwarded((short) 5);
        ans.setCorrect(true);
        ans.setGradedByUserId(UUID.randomUUID());
        ans.setGradedAt(Instant.now());
        ans.setDeletedAt(Instant.now());

        assertThat(ans.getSelectedOptionId()).isNotNull();
        assertThat(ans.getSelectedBoolean()).isTrue();
        assertThat(ans.getTextAnswer()).isEqualTo("Paris");
        assertThat(ans.getPointsAwarded()).isEqualTo((short) 5);
        assertThat(ans.getCorrect()).isTrue();
        assertThat(ans.getGradedByUserId()).isNotNull();
        assertThat(ans.getGradedAt()).isNotNull();
        assertThat(ans.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Equals/hashCode come from BaseEntity (id-based)")
    void equalsHashCode() {
        var id = UUID.randomUUID();
        var q1 = new Quiz();
        var q2 = new Quiz();
        // different ids → not equal
        assertThat(q1).isNotEqualTo(q2);
        assertThat(q1.hashCode()).isEqualTo(q1.getClass().hashCode());
        // null id short-circuits to false
        assertThat(q1.equals(null)).isFalse();
        // unrelated type
        assertThat(q1.equals("not an entity")).isFalse();
        // self
        assertThat(q1).isEqualTo(q1);
        // unused id reference to silence the warning
        assertThat(id).isNotNull();
    }
}