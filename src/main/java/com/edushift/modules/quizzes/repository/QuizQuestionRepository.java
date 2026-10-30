package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.Quiz;
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
 * Spring Data repository for {@link QuizQuestion}
 * (Sprint 7b / BE-7b.0). Tenant-scoped automatically.
 */
@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

	Optional<QuizQuestion> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	List<QuizQuestion> findAllByQuizOrderByPositionAsc(Quiz quiz);

	long countByQuiz(Quiz quiz);

	/**
	 * One grouped read for a page of quizzes. {@code deleted = false}
	 * is already applied by {@code @SQLRestriction} on the entity.
	 */
	@Query("""
			select new com.edushift.modules.quizzes.repository.QuizQuestionTotals(
					q.quiz.id, count(q), coalesce(sum(q.points), 0))
			from QuizQuestion q
			where q.quiz.id in :quizIds
			group by q.quiz.id
			""")
	List<QuizQuestionTotals> aggregateByQuizIds(@Param("quizIds") Collection<UUID> quizIds);
}
