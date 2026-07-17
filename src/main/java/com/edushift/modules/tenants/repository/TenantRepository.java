package com.edushift.modules.tenants.repository;

import com.edushift.modules.tenants.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	/**
	 * Sprint 15 admin-console search (F-05 follow-up). NULL-tolerant
	 * filters so a single query handles every combination of
	 * {@code search} / {@code status} / {@code plan}. Search is matched
	 * case-insensitively against {@code name} OR {@code slug} via
	 * {@code LIKE '%' || lower(:search) || '%'}.
	 */
	@Query(
			value = """
			SELECT * FROM edushift.tenants t
			WHERE (:search = ''
			       OR lower(t.name) LIKE lower('%' || :search || '%')
			       OR lower(t.slug) LIKE lower('%' || :search || '%'))
			  AND (:status = '' OR t.status = :status)
			  AND (:plan   = '' OR t.plan   = :plan)
			ORDER BY t.created_at DESC
			""",
			countQuery = """
			SELECT count(*) FROM edushift.tenants t
			WHERE (:search = ''
			       OR lower(t.name) LIKE lower('%' || :search || '%')
			       OR lower(t.slug) LIKE lower('%' || :search || '%'))
			  AND (:status = '' OR t.status = :status)
			  AND (:plan   = '' OR t.plan   = :plan)
			""",
			nativeQuery = true)
	Page<Tenant> adminSearch(
			@Param("search") String search,
			@Param("status") String status,
			@Param("plan") String plan,
			Pageable pageable);

}
