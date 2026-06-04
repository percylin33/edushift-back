package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByPublicUuid(UUID publicUuid);

	Optional<User> findByEmail(String email);

	Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

	boolean existsByPublicUuid(UUID publicUuid);

	boolean existsByEmail(String email);

	boolean existsByEmailAndTenantId(String email, UUID tenantId);

	Page<User> findAllByStatus(UserStatus status, Pageable pageable);

	long countByStatus(UserStatus status);

	long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

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

}
