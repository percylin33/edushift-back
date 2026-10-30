package com.edushift.modules.quizzes.repository;

import java.util.UUID;

/**
 * Grouped question count and point total for one quiz.
 * Built by {@link QuizQuestionRepository#aggregateByQuizIds}.
 */
public record QuizQuestionTotals(UUID quizId, long questionCount, long totalPoints) {
}
