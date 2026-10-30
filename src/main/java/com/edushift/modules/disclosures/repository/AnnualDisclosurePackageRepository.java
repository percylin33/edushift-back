package com.edushift.modules.disclosures.repository;

import com.edushift.modules.disclosures.entity.AnnualDisclosurePackage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

/** Queries rely on Hibernate {@code @TenantId}; identifiers are public UUIDs at the API boundary. */
public interface AnnualDisclosurePackageRepository extends JpaRepository<AnnualDisclosurePackage, UUID> {
	Optional<AnnualDisclosurePackage> findByAcademicYear_PublicUuid(UUID academicYearPublicUuid);
	Optional<AnnualDisclosurePackage> findByPublicUuid(UUID publicUuid);
	@EntityGraph(attributePaths = "academicYear")
	List<AnnualDisclosurePackage> findAllByIdIn(Collection<UUID> ids);
}
