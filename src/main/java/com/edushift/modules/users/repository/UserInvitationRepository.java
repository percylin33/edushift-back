package com.edushift.modules.users.repository;

import com.edushift.modules.users.entity.UserInvitation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence port for {@link UserInvitation}.
 *
 * <h3>Two query flavors</h3>
 * <ul>
 *   <li><strong>Tenant-scoped</strong>: {@link #findByPublicUuid} and
 *       {@link #findPendingInTenant} flow through Hibernate's
 *       {@code @TenantId} filter, so they only see rows in the caller's
 *       tenant. Use these from authenticated admin paths.</li>
 *   <li><strong>Global</strong>: {@link #findActiveByToken} is a native
 *       query that ignores the tenant filter on purpose — the public
 *       accept endpoint resolves the tenant <em>from</em> the token, so
 *       it has none to scope by. The query is still safe because tokens
 *       are high-entropy secrets (~192 bits) and the partial unique
 *       index enforces global uniqueness on non-deleted rows.</li>
 * </ul>
 */
@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

	Optional<UserInvitation> findByPublicUuid(UUID publicUuid);

	/**
	 * Lookup by token without tenant scope. The accept endpoint uses
	 * this; nobody else should. Native query is the easiest way to
	 * sidestep the {@code @TenantId} filter in Hibernate 6.
	 */
	@Query(value = """
			select * from edushift.user_invitations
			where token = :token
			  and deleted = false
			""", nativeQuery = true)
	Optional<UserInvitation> findActiveByToken(@Param("token") String token);

	/**
	 * Pending invitations in the current tenant, paginated. "Pending" =
	 * not accepted, not cancelled, not yet past {@code expires_at}. The
	 * {@code now} parameter is taken at the controller layer so unit
	 * tests can inject a stable clock value.
	 */
	@Query("""
			select i from UserInvitation i
			where i.acceptedAt is null
			  and i.cancelledAt is null
			  and i.expiresAt > :now
			""")
	Page<UserInvitation> findPendingInTenant(@Param("now") Instant now, Pageable pageable);

	/**
	 * Used by the create-invitation guardrail: re-inviting an email that
	 * already has an active pending invitation in the same tenant is a
	 * conflict. Match is case-insensitive (emails are normalised lowercase
	 * on persist; this guard exists for safety).
	 */
	@Query("""
			select i from UserInvitation i
			where lower(i.email) = lower(:email)
			  and i.acceptedAt is null
			  and i.cancelledAt is null
			  and i.expiresAt > :now
			""")
	Optional<UserInvitation> findActivePendingByEmail(
			@Param("email") String email,
			@Param("now") Instant now);

}
