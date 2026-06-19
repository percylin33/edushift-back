package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
