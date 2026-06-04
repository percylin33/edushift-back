package com.edushift.modules.tenants.repository;

import com.edushift.modules.tenants.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link Tenant}.
 * <p>
 * Soft-deleted rows are hidden by the global {@code @SQLRestriction} declared
 * in {@code BaseEntity}. Slug lookups are case-insensitive to mirror the
 * partial unique index on {@code lower(slug)}.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	@Query("select t from Tenant t where lower(t.slug) = lower(:slug)")
	Optional<Tenant> findBySlugIgnoreCase(@Param("slug") String slug);

	Optional<Tenant> findByPublicUuid(UUID publicUuid);

}
