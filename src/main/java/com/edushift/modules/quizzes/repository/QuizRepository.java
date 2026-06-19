package com.edushift.modules.quizzes.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Quiz} (Sprint 7b / BE-7b.0).
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator; cross-tenant lookups return empty by construction.
 *
 * <p>The {@code findAllBy*} methods accept the {@link Section}
 * entity directly (not its {@code publicUuid}) so the
 * tenant-scoped {@code section_id} comparison participates in the
 * tenant filter. Passing the section {@code publicUuid} would
 * generate SQL that hits {@code sections(public_uuid)} and bypass
 * the {@code @TenantId} on the join path.
 */
@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

	Optional<Quiz> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	Page<Quiz> findAllBySectionOrderByDueAtDesc(Section section, Pageable pageable);

	Page<Quiz> findAllBySectionAndStatusOrderByDueAtDesc(
			Section section, QuizStatus status, Pageable pageable);
}
