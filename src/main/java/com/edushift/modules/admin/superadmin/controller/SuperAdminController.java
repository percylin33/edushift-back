package com.edushift.modules.admin.superadmin.controller;

import com.edushift.modules.admin.superadmin.SuperAdminService;
import com.edushift.modules.admin.superadmin.dto.CreateSuperAdminRequest;
import com.edushift.modules.admin.superadmin.dto.SuperAdminSummary;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 15 / F-06 / H-04: SUPER_ADMIN management API.
 *
 * <p>Class-level {@code @PreAuthorize} ensures every endpoint requires
 * ROLE_SUPER_ADMIN; quorum is enforced in the service layer.</p>
 */
@RestController
@RequestMapping("/admin/super-admins")
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Admin Super Admins",
		description = "SUPER_ADMIN lifecycle (Sprint 15 / F-06)")
@RequiredArgsConstructor
public class SuperAdminController {

	private final SuperAdminService superAdminService;

	@GetMapping
	@Operation(summary = "List all SUPER_ADMINs")
	public ApiResponse<List<SuperAdminSummary>> list() {
		return ApiResponse.ok(superAdminService.list());
	}

	@PostMapping
	@Operation(summary = "Create a new SUPER_ADMIN (must reset password + enrol MFA on first login)")
	public ApiResponse<SuperAdminSummary> create(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@Valid @RequestBody CreateSuperAdminRequest request) {
		return ApiResponse.ok(superAdminService.create(request,
				actor != null ? actor.getId() : null));
	}

	@PatchMapping("/{uuid}/disable")
	@Operation(summary = "Disable a SUPER_ADMIN (quorum enforced)")
	public ApiResponse<SuperAdminSummary> disable(
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@PathVariable("uuid") UUID uuid) {
		return ApiResponse.ok(superAdminService.disable(uuid,
				actor != null ? actor.getId() : null));
	}

	@GetMapping("/count-active")
	@Operation(summary = "Active SUPER_ADMIN count (used by FE to show quorum badge)")
	public ApiResponse<Long> countActive() {
		return ApiResponse.ok(superAdminService.countActive());
	}
}
