package com.edushift.modules.users.controller;

import com.edushift.modules.auth.dto.AuthResponse;
import com.edushift.modules.users.dto.AcceptInvitationRequest;
import com.edushift.modules.users.dto.CreateInvitationRequest;
import com.edushift.modules.users.dto.InvitationPreflightResponse;
import com.edushift.modules.users.dto.InvitationResponse;
import com.edushift.modules.users.service.UserInvitationService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for user invitations.
 *
 * <h3>Path table (under {@code /api/v1/users/invitations})</h3>
 * <table>
 *   <caption>Invitation endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>POST  </td><td>/                       </td><td>TENANT_ADMIN</td><td>{@link InvitationResponse} (with token)</td></tr>
 *   <tr><td>GET   </td><td>/                       </td><td>TENANT_ADMIN</td><td>{@link Page}&lt;{@link InvitationResponse}&gt;</td></tr>
 *   <tr><td>DELETE</td><td>/{publicUuid}           </td><td>TENANT_ADMIN</td><td>{@link InvitationResponse} (cancelled)</td></tr>
 *   <tr><td>GET   </td><td>/by-token/{token}       </td><td>—</td>          <td>{@link InvitationPreflightResponse}</td></tr>
 *   <tr><td>POST  </td><td>/accept                 </td><td>—</td>          <td>{@link AuthResponse} (raw, OAuth-shape)</td></tr>
 * </table>
 *
 * <p>The two public paths ({@code by-token/**} and {@code accept}) must
 * be added to {@code SecurityConfig.PUBLIC_PATHS} — see that class.
 */
@RestController
@RequestMapping("/users/invitations")
@Validated
@RequiredArgsConstructor
@Tag(name = "User Invitations",
		description = "Invitation lifecycle: create / list / cancel (admin) and preflight / accept (public)")
public class UserInvitationController {

	private final UserInvitationService service;

	// ===========================================================================
	// Admin paths — TENANT_ADMIN
	// ===========================================================================

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Create an invitation (TENANT_ADMIN)",
			description = "Persists a new pending invitation in the current tenant "
					+ "and returns the projection including the token, so the "
					+ "admin can copy the resulting invitation link to the "
					+ "clipboard. Sprint 9 will replace the manual copy with "
					+ "automatic email delivery."
	)
	public ResponseEntity<ApiResponse<InvitationResponse>> create(
			@Valid @RequestBody CreateInvitationRequest request
	) {
		// Strip `metadata` here on purpose: the public API surface MUST NOT
		// let admins inject arbitrary side-channel keys (e.g. teacherId) —
		// only internal callers (TeacherServiceImpl.invite, ...) populate
		// metadata via the no-metadata constructor.
		InvitationResponse response = service.createInvitation(
				new CreateInvitationRequest(
						request.email(),
						request.firstName(),
						request.lastName(),
						request.roles()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List pending invitations (TENANT_ADMIN)",
			description = "Tokens are stripped from list entries: admins should "
					+ "use the publicUuid for any subsequent action (cancel) "
					+ "instead of the token. Default sort is by expiresAt ASC "
					+ "so soon-to-expire invitations bubble up."
	)
	public ResponseEntity<Page<InvitationResponse>> listPending(
			@PageableDefault(size = 20, sort = "expiresAt", direction = Sort.Direction.ASC)
			Pageable pageable
	) {
		return ResponseEntity.ok(service.listPendingInvitations(pageable));
	}

	@DeleteMapping("/{publicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Cancel a pending invitation (TENANT_ADMIN)",
			description = "Idempotent on already-cancelled invitations. "
					+ "Refuses ACCEPTED invitations with 409 "
					+ "INVITATION_ALREADY_ACCEPTED — the user is already "
					+ "in the system; cancellation would be theatrical."
	)
	public ResponseEntity<ApiResponse<InvitationResponse>> cancel(@PathVariable UUID publicUuid) {
		InvitationResponse response = service.cancelInvitation(publicUuid);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	// ===========================================================================
	// Public paths — token-driven
	// ===========================================================================

	@GetMapping("/by-token/{token}")
	@Operation(
			summary = "Pre-flight check before showing the accept page (public)",
			description = "Returns the recipient's name and tenant name so the "
					+ "accept page can render 'Welcome to {tenant}, {firstName}'. "
					+ "Maps token errors to 404 (not found) and 410 (gone: "
					+ "ACCEPTED / CANCELLED / EXPIRED)."
	)
	public ResponseEntity<ApiResponse<InvitationPreflightResponse>> preflight(
			@PathVariable
			@NotBlank(message = "token is required")
			@Size(min = 16, max = 128, message = "token length out of range")
			@Parameter(description = "Opaque invitation token")
			String token
	) {
		InvitationPreflightResponse response = service.getPreflight(token);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/accept")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Redeem an invitation token (public)",
			description = "Creates the new user inside the invitation's tenant "
					+ "and returns a logged-in session — same envelope as "
					+ "/v1/auth/login. Maps token errors to 404 / 410 (see "
					+ "preflight). Returns 201 Created because a new user "
					+ "resource is created."
	)
	public ResponseEntity<AuthResponse> accept(
			@Valid @RequestBody AcceptInvitationRequest request
	) {
		AuthResponse response = service.acceptInvitation(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
