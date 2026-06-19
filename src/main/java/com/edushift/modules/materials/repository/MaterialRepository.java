package com.edushift.modules.materials.repository;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.materials.entity.Material;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Material} (Sprint 7a / BE-7a.1).
 * Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * discriminator; cross-tenant lookups return empty by construction.
 */
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

	Optional<Material> findByPublicUuid(UUID publicUuid);

	boolean existsByPublicUuid(UUID publicUuid);

	Page<Material> findAllBySectionOrderByCreatedAtDesc(Section section, Pageable pageable);

	List<Material> findAllByFilePublicUuid(UUID filePublicUuid);
}
