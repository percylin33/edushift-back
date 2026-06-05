package com.edushift.modules.students.repository;

import com.edushift.modules.students.entity.DocumentType;
import com.edushift.modules.students.entity.Guardian;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link Guardian}.
 *
 * <p>All paths are tenant-scoped via Hibernate's {@code @TenantId} discriminator.
 * The "find by document" lookup is the entry point used by the link
 * service to promote an add-guardian-by-document call into reusing
 * an existing guardian (sibling sharing).
 */
@Repository
public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

	Optional<Guardian> findByPublicUuid(UUID publicUuid);

	Optional<Guardian> findByDocumentTypeAndDocumentNumber(
			DocumentType documentType, String documentNumber);

	Optional<Guardian> findByEmailIgnoreCase(String email);
}
