package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	/**
	 * Pending short answers of one quiz in a single select.
	 * Question and attempt are fetched so the queue does not
	 * initialize those proxies one row at a time.
	 */
	@Query("""
			select ans from QuizAnswer ans
			join fetch ans.question q
			join fetch ans.attempt a
			where a.quiz = :quiz
			  and ans.textAnswer is not null
			  and ans.gradedAt is null
			order by a.attemptNumber asc, q.position asc
			""")
	List<QuizAnswer> findPendingShortAnswersByQuiz(@Param("quiz") Quiz quiz);

	/**
	 * Pending SHORT_ANSWER counts for a page of attempts.
	 * Callers must not pass an empty id collection.
	 */
	@Query("""
			select new com.edushift.modules.quizzes.repository.AttemptPendingCount(
					a.attempt.id, count(a))
			from QuizAnswer a
			join a.question q
			where a.attempt.id in :attemptIds
			  and q.questionType = com.edushift.modules.quizzes.entity.QuestionType.SHORT_ANSWER
			  and a.gradedAt is null
			group by a.attempt.id
			""")
	List<AttemptPendingCount> countPendingShortAnswersByAttemptIds(
			@Param("attemptIds") Collection<UUID> attemptIds);
}
