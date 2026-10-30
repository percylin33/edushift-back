package com.edushift.modules.quizzes.repository;

import java.util.UUID;

/**
 * Ungraded SHORT_ANSWER count for one attempt.
 * Built by {@link QuizAnswerRepository#countPendingShortAnswersByAttemptIds}.
 */
public record AttemptPendingCount(UUID attemptId, long pendingCount) {
}
