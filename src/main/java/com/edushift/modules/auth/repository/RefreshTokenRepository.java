package com.edushift.modules.auth.repository;

import com.edushift.modules.auth.entity.RefreshToken;
import com.edushift.modules.auth.entity.RevocationReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link RefreshToken}.
 * <p>
 * Tenant-scoped automatically via Hibernate's {@code @TenantId} discriminator
 * when {@code TenantContext} is set. Theft detection ({@link #revokeChain})
 * walks the rotation chain via {@code parent_token_id}.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/**
	 * Looks up a token by its SHA-256 hex digest. Tenant-scoped automatically.
	 * Soft-deleted rows are filtered by the global {@code @SQLRestriction}.
	 */
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Returns the active (non-revoked, non-expired, non-deleted) refresh tokens
	 * owned by a user, newest first. Useful for admin views and "log out
	 * everywhere" flows.
	 */
	@Query("""
			select t from RefreshToken t
			where t.userId = :userId
			  and t.revokedAt is null
			  and t.expiresAt > :now
			order by t.createdAt desc
			""")
	List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId,
	                                       @Param("now") Instant now);

	/**
	 * Walks the rotation chain rooted at {@code rootId} (following
	 * {@code parent_token_id}) and revokes every node in a single UPDATE.
	 * <p>
	 * Used by the theft-detection path: when a revoked token is presented
	 * again, every refresh derived from it must be invalidated immediately.
	 *
	 * @return the number of rows updated (0 if the chain was already revoked)
	 */
	@Modifying
	@Query(value = """
			WITH RECURSIVE chain AS (
			    SELECT id FROM edushift.refresh_tokens WHERE id = :rootId
			    UNION ALL
			    SELECT t.id FROM edushift.refresh_tokens t
			        JOIN chain c ON t.parent_token_id = c.id
			)
			UPDATE edushift.refresh_tokens
			SET revoked_at = NOW(),
			    revoked_reason = :reason,
			    updated_at = NOW()
			WHERE id IN (SELECT id FROM chain)
			  AND revoked_at IS NULL
			""", nativeQuery = true)
	int revokeChain(@Param("rootId") UUID rootId, @Param("reason") String reason);

	default int revokeChain(UUID rootId, RevocationReason reason) {
		return revokeChain(rootId, reason.name());
	}

}
