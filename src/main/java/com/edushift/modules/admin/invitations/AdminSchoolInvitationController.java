package com.edushift.modules.admin.invitations;

import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/admin/school-invitations")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin School Invitations", description = "Invite a new school by email (SUPER_ADMIN)")
public class AdminSchoolInvitationController {

	private final SchoolInvitationService service;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Invite a school founder by email")
	public ResponseEntity<ApiResponse<SchoolInvitationResponse>> create(
			@Valid @RequestBody CreateSchoolInvitationRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request)));
	}

	@GetMapping
	@Operation(summary = "List pending school invitations")
	public ResponseEntity<Page<SchoolInvitationResponse>> list(
			@PageableDefault(size = 20, sort = "expiresAt", direction = Sort.Direction.ASC)
			Pageable pageable
	) {
		return ResponseEntity.ok(service.listPending(pageable));
	}

	@PostMapping("/{publicUuid}/resend")
	@Operation(summary = "Resend a pending school invitation")
	public ResponseEntity<ApiResponse<SchoolInvitationResponse>> resend(@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.resend(publicUuid)));
	}

	@DeleteMapping("/{publicUuid}")
	@Operation(summary = "Cancel a pending school invitation")
	public ResponseEntity<ApiResponse<SchoolInvitationResponse>> cancel(@PathVariable UUID publicUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.cancel(publicUuid)));
	}
}
