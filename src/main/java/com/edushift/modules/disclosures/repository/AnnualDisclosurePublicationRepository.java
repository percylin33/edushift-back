package com.edushift.modules.disclosures.repository;

import com.edushift.modules.disclosures.entity.AnnualDisclosurePublication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only access to the append-only, tenant-scoped publication history. */
public interface AnnualDisclosurePublicationRepository extends JpaRepository<AnnualDisclosurePublication, UUID> {
	List<AnnualDisclosurePublication> findAllByPackageIdOrderByVersionNumberDesc(UUID packageId);
	Optional<AnnualDisclosurePublication> findFirstByPackageIdOrderByVersionNumberDesc(UUID packageId);
	List<AnnualDisclosurePublication> findAllByOrderByPublishedAtDesc();
}
