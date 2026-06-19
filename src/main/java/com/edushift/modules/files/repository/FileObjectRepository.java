package com.edushift.modules.files.repository;

import com.edushift.modules.files.entity.FileObject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link FileObject} (Sprint 7a / BE-7a.0).
 *
 * <p>All methods are tenant-scoped via Hibernate's {@code @TenantId}
 * discriminator. The service layer is responsible for translating
 * public UUIDs into rows; cross-tenant access is structurally impossible.
 */
@Repository
public interface FileObjectRepository extends JpaRepository<FileObject, UUID> {

	Optional<FileObject> findByPublicUuid(UUID publicUuid);

	/**
	 * De-duplication probe (DEBT-7A-4). When a tenant uploads the
	 * exact same bytes twice (same {@code sha256} + size), we may
	 * choose to reuse the existing row instead of storing a second
	 * copy. This sprint does not act on the probe result yet — it
	 * simply records the lookup so the wiring is in place.
	 */
	@Query("""
			select f from FileObject f
			where f.checksumSha256 = :sha256
			""")
	Optional<FileObject> findFirstByChecksum(@Param("sha256") String sha256);
}
