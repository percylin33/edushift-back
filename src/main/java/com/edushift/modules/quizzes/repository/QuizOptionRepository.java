package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link QuizOption}
 * (Sprint 7b / BE-7b.0). Tenant-scoped automatically.
 */
@Repository
public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

	Optional<QuizOption> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	List<QuizOption> findAllByQuestionOrderByPositionAsc(QuizQuestion question);

	long countByQuestionAndCorrectIsTrue(QuizQuestion question);
}
