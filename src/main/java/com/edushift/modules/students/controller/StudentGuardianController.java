package com.edushift.modules.students.controller;

import com.edushift.modules.students.dto.AddGuardianRequest;
import com.edushift.modules.students.dto.GuardianResponse;
import com.edushift.modules.students.dto.InviteGuardianResponse;
import com.edushift.modules.students.dto.LinkGuardianUserRequest;
import com.edushift.modules.students.dto.UpdateGuardianLinkRequest;
import com.edushift.modules.students.service.StudentGuardianService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the student↔guardian link.
 *
 * <h3>Path table (under {@code /api/v1/students/{publicUuid}/guardians})</h3>
 * <table>
 *   <caption>Guardian endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET   </td><td>/                       </td><td>TENANT_ADMIN</td><td>{@link List}&lt;{@link GuardianResponse}&gt;</td></tr>
 *   <tr><td>POST  </td><td>/                       </td><td>TENANT_ADMIN</td><td>{@link GuardianResponse} (201)</td></tr>
 *   <tr><td>PUT   </td><td>/{guardianPublicUuid}   </td><td>TENANT_ADMIN</td><td>{@link GuardianResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{guardianPublicUuid}/link-user</td><td>TENANT_ADMIN</td><td>{@link GuardianResponse}</td></tr>
 *   <tr><td>POST  </td><td>/{guardianPublicUuid}/invite</td><td>TENANT_ADMIN</td><td>{@link InviteGuardianResponse}</td></tr>
 *   <tr><td>DELETE</td><td>/{guardianPublicUuid}   </td><td>TENANT_ADMIN</td><td>204</td></tr>
 * </table>
 *
 * <p>The path embeds the student's {@code publicUuid} so admins can
 * navigate "students → student detail → tutores" without juggling
 * separate IDs at the URL level.
 */
@RestController
@RequestMapping("/students/{studentPublicUuid}/guardians")
@Validated
@RequiredArgsConstructor
@Tag(name = "Student Guardians",
		description = "Parent / guardian relationships scoped to a single student")
public class StudentGuardianController {

	private final StudentGuardianService service;

	@GetMapping
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "List guardians of a student (TENANT_ADMIN)",
			description = "Returns each active link with the merged guardian "
					+ "profile + relationship metadata. 404 RESOURCE_NOT_FOUND "
					+ "when the student does not exist."
	)
	public ResponseEntity<ApiResponse<List<GuardianResponse>>> list(
			@PathVariable UUID studentPublicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(service.listGuardians(studentPublicUuid)));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Add (or link) a guardian to a student (TENANT_ADMIN)",
			description = "Looks up an existing guardian by document; reuses it "
					+ "if found (sibling-sharing), creates a new one otherwise. "
					+ "Then links it to the student with the provided relationship "
					+ "metadata. 409 GUARDIAN_ALREADY_LINKED if the same pair is "
					+ "already active."
	)
	public ResponseEntity<ApiResponse<GuardianResponse>> add(
			@PathVariable UUID studentPublicUuid,
			@Valid @RequestBody AddGuardianRequest request
	) {
		GuardianResponse response = service.addGuardian(studentPublicUuid, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}

	@PutMapping("/{guardianPublicUuid}")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Edit the relationship metadata (TENANT_ADMIN)",
			description = "Updates only the link fields (relationship, "
					+ "isPrimaryContact, canPickupStudent). 422 LAST_PRIMARY_CONTACT "
					+ "if the operation would leave the student without a primary "
					+ "contact."
	)
	public ResponseEntity<ApiResponse<GuardianResponse>> update(
			@PathVariable UUID studentPublicUuid,
			@PathVariable UUID guardianPublicUuid,
			@Valid @RequestBody UpdateGuardianLinkRequest request
	) {
		GuardianResponse response = service.updateLink(
				studentPublicUuid, guardianPublicUuid, request);
		return ResponseEntity.ok(ApiResponse.ok(response));
	}

	@PostMapping("/{guardianPublicUuid}/link-user")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Link a guardian to an existing PARENT user (TENANT_ADMIN)",
			description = "Rescue path after inviting from Usuarios. Errors: "
					+ "409 GUARDIAN_ALREADY_HAS_USER, 409 USER_NOT_PARENT_ROLE, "
					+ "409 USER_ALREADY_LINKED_TO_GUARDIAN, 404 RESOURCE_NOT_FOUND."
	)
	public ResponseEntity<ApiResponse<GuardianResponse>> linkUser(
			@PathVariable UUID studentPublicUuid,
			@PathVariable UUID guardianPublicUuid,
			@Valid @RequestBody LinkGuardianUserRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.linkUser(studentPublicUuid, guardianPublicUuid, request)));
	}

	@PostMapping("/{guardianPublicUuid}/invite")
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Invite a guardian to claim a PARENT portal (TENANT_ADMIN)",
			description = "Creates a user_invitations row with role PARENT and "
					+ "metadata.guardianId. One invite per Guardian (siblings share). "
					+ "Errors: 409 GUARDIAN_ALREADY_HAS_USER, 422 GUARDIAN_EMAIL_REQUIRED, "
					+ "409 INVITATION_ALREADY_PENDING."
	)
	public ResponseEntity<ApiResponse<InviteGuardianResponse>> invite(
			@PathVariable UUID studentPublicUuid,
			@PathVariable UUID guardianPublicUuid
	) {
		return ResponseEntity.ok(ApiResponse.ok(
				service.invite(studentPublicUuid, guardianPublicUuid)));
	}

	@DeleteMapping("/{guardianPublicUuid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(
			summary = "Unlink a guardian from a student (TENANT_ADMIN)",
			description = "Soft-delete on the link only — the guardian row is "
					+ "preserved (it may still be linked to a sibling). 422 "
					+ "LAST_PRIMARY_CONTACT if removing the primary contact would "
					+ "strand the student."
	)
	public ResponseEntity<Void> unlink(
			@PathVariable UUID studentPublicUuid,
			@PathVariable UUID guardianPublicUuid
	) {
		service.unlinkGuardian(studentPublicUuid, guardianPublicUuid);
		return ResponseEntity.noContent().build();
	}
}
