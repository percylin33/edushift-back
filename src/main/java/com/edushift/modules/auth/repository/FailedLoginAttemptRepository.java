package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.FailedLoginAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link FailedLoginAttempt}.
 *
 * <p>Tenant-scoped automatically via Hibernate's {@code @TenantId}
 * discriminator when {@code TenantContext} is set. Queries here are
 * bounded by the unique constraint on
 * {@code (tenant_id, email, first_attempt_at)}, so lookups are O(1).
 */
@Repository
public interface FailedLoginAttemptRepository extends JpaRepository<FailedLoginAttempt, UUID> {

	/**
	 * Returns the most recent row for this (tenant, email) regardless of
	 * status. Used by the cleanup logic. The window logic itself is in
	 * {@link com.edushift.modules.auth.service.LoginAttemptService}.
	 */
	@Query("""
			select a from FailedLoginAttempt a
			where a.email = :email
			order by a.lastAttemptAt desc
			""")
	Optional<FailedLoginAttempt> findMostRecent(@Param("email") String email);
}
