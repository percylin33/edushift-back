package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA access to {@link PasswordResetToken}.
 *
 * <p>All methods implicitly run inside the current {@code TenantContext};
 * the {@code @TenantId} Hibernate filter scopes every SELECT to the
 * current tenant. To look up a row across tenants (e.g. for an admin /
 * forensic view), call the corresponding native query.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	/**
	 * Look up by {@code jti} (the JWT id claim). Returns the row in the
	 * current tenant or empty.
	 */
	Optional<PasswordResetToken> findByJti(UUID jti);

	/**
	 * Mark all non-used, non-superseded tokens for a given user as superseded.
	 * Called when a new reset is requested so the older links stop working.
	 *
	 * @return number of rows affected
	 */
	@Modifying
	@Query("""
			UPDATE PasswordResetToken t
			   SET t.supersededAt = :now
			 WHERE t.userId = :userId
			   AND t.usedAt IS NULL
			   AND t.supersededAt IS NULL
			""")
	int supersedeAllPendingForUser(@Param("userId") UUID userId, @Param("now") java.time.Instant now);
}