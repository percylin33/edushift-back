package com.edushift.modules.evaluations.rubric.repository;

import com.edushift.modules.evaluations.rubric.entity.Rubric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for
 * {@link com.edushift.modules.evaluations.rubric.entity.Rubric}
 * (Sprint 5B / BE-5B.2). Tenant-scoped via Hibernate's {@code @TenantId}
 * discriminator.
 *
 * <p>The service layer wraps every load in a "current tenant" check, so
 * cross-tenant access is impossible even if the caller knows a UUID
 * from another tenant. The
 * {@link #findByPublicUuidAcrossTenants(UUID)} escape hatch is reserved
 * for system-rubric materialization (the seed) and is guarded by
 * {@code is_system = true} check inside the service.</p>
 */
@Repository
public interface RubricRepository extends JpaRepository<Rubric, UUID> {

	Optional<Rubric> findByPublicUuid(UUID publicUuid);

	/**
	 * Cross-tenant lookup of a rubric by public UUID, bypassing the
	 * {@code @TenantId} filter. Reserved for the seed materialization
	 * (which is per-tenant but the system row source is global) and for
	 * admin / audit flows; the service guards it with a current-tenant
	 * check before returning to the caller.
	 */
	@Query("""
			select r from Rubric r
			where r.publicUuid = :publicUuid
			""")
	Optional<Rubric> findByPublicUuidAcrossTenants(@Param("publicUuid") UUID publicUuid);

	/**
	 * Per-tenant listing with optional filters. Filters are
	 * AND-combined and skip-on-null.
	 *
	 * <ul>
	 *   <li>{@code systemOnly = true} → only {@code is_system = true}.</li>
	 *   <li>{@code systemOnly = false} → only {@code is_system = false}.</li>
	 *   <li>{@code systemOnly = null} → both, ordered system-first.</li>
	 *   <li>{@code isActive} → filter by activation flag (skip-on-null).</li>
	 *   <li>{@code nameQuery} → case-insensitive {@code LIKE %q%} on
	 *       name and description (skip-on-null/blank).</li>
	 * </ul>
	 */
	@Query("""
			select r from Rubric r
			where (:systemOnly is null or r.isSystem = :systemOnly)
			  and (:isActive   is null or r.isActive = :isActive)
			  and (:nameQuery  is null
			       or lower(r.name)        like lower(concat('%', :nameQuery, '%'))
			       or lower(coalesce(r.description, ''))
			                                like lower(concat('%', :nameQuery, '%')))
			order by r.isSystem desc, r.name asc
			""")
	List<Rubric> findFiltered(
			@Param("systemOnly") Boolean systemOnly,
			@Param("isActive") Boolean isActive,
			@Param("nameQuery") String nameQuery);

	/**
	 * Case-insensitive uniqueness probe scoped to the current tenant.
	 * Used by the service to surface {@code RUB_NAME_EXISTS} (409)
	 * instead of a generic constraint violation.
	 *
	 * <p>Because Hibernate's {@code @TenantId} filter is active, this
	 * query naturally cannot see rows from other tenants — no extra
	 * guard is required.</p>
	 */
	@Query("""
			select r from Rubric r
			where lower(r.name) = lower(:name)
			""")
	Optional<Rubric> findByNameIgnoreCase(@Param("name") String name);

	/**
	 * Existence probe: does the current tenant already have a rubric
	 * with {@code is_system = true}? Used by the seed to make
	 * materialization idempotent (ADR-5B.10).
	 */
	@Query("""
			select count(r) > 0 from Rubric r
			where r.isSystem = true
			""")
	boolean existsAnySystem();

	/**
	 * Reverse lookup: "is this rubric used as a parent by any tenant-owned
	 * fork?". Useful for fork-list display and for protecting the
	 * system rubric from accidental deletion (the FK already does this
	 * with {@code ON DELETE RESTRICT}, but this is friendlier to the FE).
	 */
	@Query("""
			select count(r) > 0 from Rubric r
			where r.parentRubric = :rubric
			""")
	boolean hasForks(@Param("rubric") Rubric rubric);
}
