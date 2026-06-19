package com.edushift.modules.tasks.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.tasks.entity.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Task} (Sprint 7a / BE-7a.2).
 * Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

	Optional<Task> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	Page<Task> findAllBySectionOrderByDueAtDesc(Section section, Pageable pageable);
}
