package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link User}.
 * <p>
 * All <em>find/exists/count</em> methods rely on Hibernate's discriminator
 * multi-tenancy: when {@code TenantContext} is set, queries are auto-scoped
 * by {@code tenant_id} so most callers do not need to pass it explicitly.
 * The {@code ...AndTenantId} overloads remain for system / admin paths that
 * run with no tenant in context (e.g. background jobs, super-admin tools).
 * <p>
 * Soft-deleted users are hidden by the global {@code @SQLRestriction} on
 * {@code BaseEntity}; use {@code SoftDeleteService} to access them when
 * needed.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>,
		JpaSpecificationExecutor<User> {

	Optional<User> findByPublicUuid(UUID publicUuid);

	/**
	 * Batch lookup by public id. Callers skip this when the collection
	 * is empty so an empty {@code IN} is never sent.
	 */
	List<User> findAllByPublicUuidIn(Collection<UUID> publicUuids);

	Optional<User> findByEmail(String email);

	Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

	/**
	 * Looks up a user by their linked Google {@code sub} claim. Relies on
	 * Hibernate's {@code @TenantId} auto-filter so the search is scoped to
	 * the current {@link com.edushift.shared.multitenancy.TenantContext}.
	 */
	Optional<User> findByGoogleSubject(String googleSubject);

	/**
	 * System / admin overload of {@link #findByGoogleSubject(String)} — for
	 * background jobs that don't have a tenant context bound. Always passes
	 * the tenant id explicitly.
	 */
	Optional<User> findByGoogleSubjectAndTenantId(String googleSubject, UUID tenantId);

	boolean existsByPublicUuid(UUID publicUuid);

	boolean existsByEmail(String email);

	boolean existsByEmailAndTenantId(String email, UUID tenantId);

	Page<User> findAllByStatus(UserStatus status, Pageable pageable);

	long countByStatus(UserStatus status);

	long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

	/**
	 * Tenant-wide count of all users (any status) — used by the
	 * SUPER_ADMIN tenants panel (Sprint 16 / hardening). Explicit
	 * {@code tenant_id} parameter because the admin list runs under
	 * {@code SUPER_ADMIN_SENTINEL} which bypasses Hibernate's
	 * {@code @TenantId} auto-filter.
	 */
	long countByTenantId(UUID tenantId);

	/** Active, non-Super-Admin users available for a support impersonation picker. */
	@Query(value = """
			select * from edushift.users u
			where u.tenant_id = :tenantId
			  and u.deleted = false
			  and u.status = 'ACTIVE'
			  and not ('SUPER_ADMIN' = any(u.roles))
			  and (
			    :search = ''
			    or lower(u.first_name) like lower(concat('%', :search, '%'))
			    or lower(u.last_name) like lower(concat('%', :search, '%'))
			    or lower(u.email) like lower(concat('%', :search, '%'))
			  )
			order by u.first_name, u.last_name, u.email
			""",
		countQuery = """
			select count(*) from edushift.users u
			where u.tenant_id = :tenantId
			  and u.deleted = false
			  and u.status = 'ACTIVE'
			  and not ('SUPER_ADMIN' = any(u.roles))
			  and (
			    :search = ''
			    or lower(u.first_name) like lower(concat('%', :search, '%'))
			    or lower(u.last_name) like lower(concat('%', :search, '%'))
			    or lower(u.email) like lower(concat('%', :search, '%'))
			  )
			""", nativeQuery = true)
	Page<User> findActiveSupportUsersByTenantId(
			@Param("tenantId") UUID tenantId,
			@Param("search") String search,
			Pageable pageable);

	/**
	 * Bulk-updates the last successful login timestamp without loading the entity.
	 * Tenant-scoped via Hibernate's automatic {@code @TenantId} filter.
	 */
	@Modifying
	@Query("update User u set u.lastLoginAt = :loggedInAt where u.id = :userId")
	int updateLastLoginAt(@Param("userId") UUID userId, @Param("loggedInAt") Instant loggedInAt);

	/**
	 * Atomically transitions a user's status. Returns the number of rows affected
	 * (0 when the target user does not exist in the current tenant).
	 */
	@Modifying
	@Query("update User u set u.status = :newStatus where u.id = :userId")
	int updateStatus(@Param("userId") UUID userId, @Param("newStatus") UserStatus newStatus);

	/**
	 * Counts how many <em>active</em> admins the given tenant has. Used by
	 * the last-admin guardrail when the management module is about to
	 * remove the {@code TENANT_ADMIN} role from a user, disable an admin,
	 * or delete one. Returning {@code 0} after a candidate change means the
	 * tenant would be stranded and the operation must be rejected.
	 *
	 * <p>Native query because JPQL has no clean way to express
	 * {@code 'TENANT_ADMIN' = ANY(roles)} against a Postgres {@code varchar[]}.
	 * Tenant scoping is done via an explicit parameter (rather than relying
	 * on Hibernate's filter) since native queries bypass {@code @TenantId}.
	 */
	@Query(value = """
			select count(*) from edushift.users
			where tenant_id = :tenantId
			  and 'TENANT_ADMIN' = any(roles)
			  and status = 'ACTIVE'
			  and deleted = false
			""", nativeQuery = true)
	long countActiveTenantAdmins(@Param("tenantId") UUID tenantId);

	/**
	 * Active users who can reconcile payments (TENANT_ADMIN or STAFF).
	 * Native because roles are a Postgres {@code varchar[]}.
	 */
	@Query(value = """
			select * from edushift.users
			where tenant_id = :tenantId
			  and status = 'ACTIVE'
			  and deleted = false
			  and (
			    'TENANT_ADMIN' = any(roles)
			    or 'STAFF' = any(roles)
			  )
			""", nativeQuery = true)
	List<User> findActivePaymentAdmins(@Param("tenantId") UUID tenantId);

}
