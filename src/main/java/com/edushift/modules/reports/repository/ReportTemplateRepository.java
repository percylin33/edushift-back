package com.edushift.modules.reports.repository;

import com.edushift.modules.reports.entity.ReportTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, UUID> {

	Optional<ReportTemplate> findByPublicUuidAndDeletedFalse(UUID publicUuid);

	Page<ReportTemplate> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

	/**
	 * Scheduler tick query (Sprint cierre-C / B12). Returns the templates
	 * that are due to run right now (their {@code next_run_at} has passed
	 * and they're still active). Ordered by id so the runner processes
	 * each template at most once per tick.
	 */
	@Query("""
			SELECT t FROM ReportTemplate t
			WHERE t.deleted = false
			  AND t.active = true
			  AND t.nextRunAt IS NOT NULL
			  AND t.nextRunAt <= :asOf
			ORDER BY t.id ASC
			""")
	List<ReportTemplate> findDueTemplates(@Param("asOf") Instant asOf, Pageable pageable);

	boolean existsByNameAndDeletedFalse(String name);
}