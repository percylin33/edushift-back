package com.edushift.modules.users.service;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * User-invitation lifecycle: create, list, cancel (admin paths) and
 * preflight + accept (public paths).
 *
 * <h3>Tenant scoping</h3>
 * Admin paths flow through Hibernate's tenant filter (the controller
 * gate is {@code @PreAuthorize("hasRole('TENANT_ADMIN')")} and the
 * caller is bound to a tenant via the JWT). Public paths run without
 * a tenant context: {@link #getPreflight(String)} and
 * {@link #acceptInvitation(AcceptInvitationRequest)} resolve the
 * tenant <em>from</em> the token and use {@code TenantContext.runAs}
 * before touching tenant-scoped tables.
 *
 * <h3>State transitions</h3>
 * The status surface ({@code PENDING / ACCEPTED / CANCELLED / EXPIRED}) is
 * derived; see {@link com.edushift.modules.users.entity.UserInvitation}.
 */
public interface UserInvitationService {

	/**
	 * Persists a new invitation in the current tenant. Returns the full
	 * projection with the {@code token} included so the admin can copy
	 * the invitation link before the response leaves the wire.
	 *
	 * <p>Throws {@code 409 INVITATION_ALREADY_PENDING} when the same
	 * email already has an active pending invitation in this tenant.
	 * Throws {@code 422 INVALID_ROLE} when the request contains an
	 * unknown role name.
	 */
	InvitationResponse createInvitation(CreateInvitationRequest request);

	/**
	 * Pending invitations in the current tenant, paginated. Token is
	 * stripped from each entry — admins use the cancel-by-publicUuid
	 * flow if they need to act on a row, not the token.
	 */
	Page<InvitationResponse> listPendingInvitations(Pageable pageable);

	/**
	 * Soft-cancels a pending invitation. Idempotent on already-cancelled
	 * invitations (returns the current snapshot). Refuses
	 * {@code ACCEPTED} invitations with {@code 409 INVITATION_ALREADY_ACCEPTED}
	 * — the user is already in the system; cancellation is a no-op
	 * the API surfaces explicitly to avoid a misleading 200.
	 */
	InvitationResponse cancelInvitation(UUID publicUuid);

	/**
	 * Public preflight check for the accept page.
	 *
	 * <ul>
	 *   <li>Token unknown → {@code 404 RESOURCE_NOT_FOUND}.</li>
	 *   <li>Token already redeemed → {@code 410 INVITATION_ALREADY_ACCEPTED}.</li>
	 *   <li>Token cancelled → {@code 410 INVITATION_CANCELLED}.</li>
	 *   <li>Token expired → {@code 410 INVITATION_EXPIRED}.</li>
	 *   <li>Token pending → returns a public-safe view of the invitation.</li>
	 * </ul>
	 */
	InvitationPreflightResponse getPreflight(String token);

	/**
	 * Public redemption flow. On success: creates the new user inside
	 * the invitation's tenant, marks the invitation as accepted, and
	 * returns a logged-in session — same envelope as
	 * {@code /v1/auth/login}. Same 410 / 404 mapping as
	 * {@link #getPreflight(String)} on token failures.
	 */
	AuthResponse acceptInvitation(AcceptInvitationRequest request);

}
