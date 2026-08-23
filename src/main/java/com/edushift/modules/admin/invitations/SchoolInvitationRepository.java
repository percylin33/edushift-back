package com.edushift.modules.admin.invitations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SchoolInvitationRepository extends JpaRepository<SchoolInvitation, UUID> {

	Optional<SchoolInvitation> findByPublicUuid(UUID publicUuid);

	Optional<SchoolInvitation> findByToken(String token);

	@Query("""
			select i from SchoolInvitation i
			where lower(i.email) = lower(:email)
			  and i.acceptedAt is null
			  and i.cancelledAt is null
			  and i.expiresAt > :now
			""")
	Optional<SchoolInvitation> findActivePendingByEmail(
			@Param("email") String email,
			@Param("now") Instant now);

	@Query("""
			select i from SchoolInvitation i
			where i.acceptedAt is null
			  and i.cancelledAt is null
			  and i.expiresAt > :now
			""")
	Page<SchoolInvitation> findPending(@Param("now") Instant now, Pageable pageable);
}
