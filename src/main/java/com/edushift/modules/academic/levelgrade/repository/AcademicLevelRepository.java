package com.edushift.modules.academic.levelgrade.repository;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AcademicLevel}. Tenant-scoped
 * automatically by Hibernate's {@code @TenantId} discriminator.
 */
@Repository
public interface AcademicLevelRepository extends JpaRepository<AcademicLevel, UUID> {

	Optional<AcademicLevel> findByPublicUuid(UUID publicUuid);

	@Query("select l from AcademicLevel l where lower(l.code) = lower(:code)")
	Optional<AcademicLevel> findByCodeIgnoreCase(@Param("code") String code);

	List<AcademicLevel> findAllByOrderByOrdinalAsc();

	long countBy();
}
