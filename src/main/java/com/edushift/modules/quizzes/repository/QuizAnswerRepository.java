package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link QuizAnswer}
 * (Sprint 7b / BE-7b.0). Tenant-scoped automatically.
 */
@Repository
public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

	Optional<QuizAnswer> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	Optional<QuizAnswer> findByAttemptAndQuestion(QuizAttempt attempt, QuizQuestion question);

	List<QuizAnswer> findAllByAttemptOrderByQuestionPositionAsc(QuizAttempt attempt);

	/** Pending manual grading queue: SHORT_ANSWER answers with no grade yet. */
	List<QuizAnswer> findAllByAttemptAndTextAnswerIsNotNullAndGradedAtIsNull(
			QuizAttempt attempt);
}
