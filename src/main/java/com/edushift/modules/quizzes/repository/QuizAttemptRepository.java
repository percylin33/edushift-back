package com.edushift.modules.quizzes.repository;

import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link QuizAttempt}
 * (Sprint 7b / BE-7b.0). Tenant-scoped automatically.
 */
@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

	Optional<QuizAttempt> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	/** All attempts for a quiz, ordered by attempt_number ascending. */
	Page<QuizAttempt> findAllByQuizOrderByAttemptNumberAsc(Quiz quiz, Pageable pageable);

	/** All attempts of one student for one quiz, ordered by attempt_number. */
	List<QuizAttempt> findAllByQuizAndStudentUserIdOrderByAttemptNumberAsc(
			Quiz quiz, UUID studentUserId);

	/**
	 * DEBT-STUDENT-PRIVACY (Fase 0.3): paginated variant used to
	 * serve a STUDENT's own attempts without leaking the rest of the
	 * class. Mirrors the unpaged variant above.
	 */
	Page<QuizAttempt> findAllByQuizAndStudentUserIdOrderByAttemptNumberAsc(
			Quiz quiz, UUID studentUserId, Pageable pageable);

	/** Latest attempt of one student for one quiz. */
	Optional<QuizAttempt> findFirstByQuizAndStudentUserIdOrderByAttemptNumberDesc(
			Quiz quiz, UUID studentUserId);

	/** Number of attempts already taken (active + soft-deleted). */
	long countByQuizAndStudentUserId(Quiz quiz, UUID studentUserId);

	/** In-progress attempts that have expired (drives the BE-7b.4 cleanup job). */
	List<QuizAttempt> findAllByQuizAndStatusAndExpiresAtBefore(
			Quiz quiz, AttemptStatus status, java.time.Instant cutoff);
}
