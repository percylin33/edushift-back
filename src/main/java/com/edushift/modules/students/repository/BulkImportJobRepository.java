package com.edushift.modules.students.repository;

import com.edushift.modules.students.entity.BulkImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link BulkImportJob}.
 *
 * <p>Tenant-scoped automatically by Hibernate's {@code @TenantId}
 * machinery; admins can only see jobs from their own tenant.
 */
@Repository
public interface BulkImportJobRepository extends JpaRepository<BulkImportJob, UUID> {

	Optional<BulkImportJob> findByPublicUuid(UUID publicUuid);
}
