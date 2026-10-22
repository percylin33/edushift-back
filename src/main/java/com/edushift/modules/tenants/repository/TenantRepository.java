package com.edushift.modules.tenants.repository;

import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import java.util.List;
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

	List<Tenant> findAllByStatus(TenantStatus status);

	@Query("select t from Tenant t where lower(t.slug) = lower(:slug)")
	Optional<Tenant> findBySlugIgnoreCase(@Param("slug") String slug);

	/**
	 * Resolve a compact hostname label to its canonical tenant slug. Legacy
	 * slugs may contain dashes, while local school hostnames omit them.
	 */
	@Query(value = """
			SELECT * FROM edushift.tenants t
			WHERE lower(translate(t.slug, '-', '')) = lower(:hostnameAlias)
			  AND t.deleted = false
			""", nativeQuery = true)
	Optional<Tenant> findByHostnameAliasIgnoreCase(@Param("hostnameAlias") String hostnameAlias);

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
			ORDER BY
			  CASE WHEN :sort = 'students' THEN (
			    SELECT COUNT(*) FROM edushift.students s
			    WHERE s.tenant_id = t.id AND s.enrollment_status = 'ENROLLED' AND s.deleted = false
			  ) END DESC NULLS LAST,
			  CASE WHEN :sort = 'billing' THEN (
			    SELECT MIN(bs.next_billing_at) FROM edushift.b2b_subscriptions bs
			    WHERE bs.tenant_id = t.id AND bs.status <> 'CANCELED' AND bs.deleted = false
			  ) END ASC NULLS LAST,
			  CASE WHEN :sort = 'name' THEN lower(t.name) END ASC NULLS LAST,
			  t.created_at DESC
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
			@Param("sort") String sort,
			Pageable pageable);

}
