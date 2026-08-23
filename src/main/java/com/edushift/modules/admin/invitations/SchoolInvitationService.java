package com.edushift.modules.admin.invitations;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchoolInvitationService {

	SchoolInvitationResponse create(CreateSchoolInvitationRequest request);

	Page<SchoolInvitationResponse> listPending(Pageable pageable);

	SchoolInvitationResponse cancel(UUID publicUuid);

	SchoolInvitationResponse resend(UUID publicUuid);

	SchoolInvitationPreflight getPreflight(String token);

	/** Validates a still-pending token for {@code POST /tenants/register}. */
	SchoolInvitation requirePending(String token);

	void markAccepted(UUID invitationId, UUID createdTenantId);
}
