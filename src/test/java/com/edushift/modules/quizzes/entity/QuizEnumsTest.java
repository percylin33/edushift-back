package com.edushift.modules.quizzes.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Quizzes enums — membership + iteration order")
class QuizEnumsTest {

    @Test
    @DisplayName("QuizStatus — DRAFT / PUBLISHED / CLOSED")
    void quizStatus() {
        assertThat(QuizStatus.values()).containsExactly(
                QuizStatus.DRAFT, QuizStatus.PUBLISHED, QuizStatus.CLOSED);
        assertThat(QuizStatus.valueOf("DRAFT")).isEqualTo(QuizStatus.DRAFT);
        assertThat(QuizStatus.PUBLISHED.name()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("AttemptStatus — IN_PROGRESS / SUBMITTED / AUTO_GRADED / GRADED / EXPIRED")
    void attemptStatus() {
        assertThat(AttemptStatus.values()).containsExactly(
                AttemptStatus.IN_PROGRESS,
                AttemptStatus.SUBMITTED,
                AttemptStatus.AUTO_GRADED,
                AttemptStatus.GRADED,
                AttemptStatus.EXPIRED);
        assertThat(AttemptStatus.valueOf("GRADED")).isEqualTo(AttemptStatus.GRADED);
    }

    @Test
    @DisplayName("QuestionType — MC / TF / SHORT_ANSWER")
    void questionType() {
        assertThat(QuestionType.values()).containsExactly(
                QuestionType.MC, QuestionType.TF, QuestionType.SHORT_ANSWER);
        assertThat(QuestionType.valueOf("MC")).isEqualTo(QuestionType.MC);
    }
}