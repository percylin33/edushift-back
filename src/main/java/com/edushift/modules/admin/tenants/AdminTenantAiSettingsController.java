package com.edushift.modules.admin.tenants;

import com.edushift.modules.admin.tenants.dto.AdminTenantAiSettingsRequest;
import com.edushift.modules.admin.tenants.dto.AdminTenantAiSettingsResponse;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants/{uuid}/ai-settings")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTenantAiSettingsController {

	private final AdminTenantAiSettingsService service;

	@GetMapping
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "Get AI access and effective quota for a tenant")
	public ApiResponse<AdminTenantAiSettingsResponse> get(@PathVariable("uuid") UUID tenantPublicUuid) {
		return ApiResponse.ok(service.get(tenantPublicUuid));
	}

	@PutMapping
	@PreAuthorize("hasRole('SUPER_ADMIN')")
	@Operation(summary = "Enable or disable AI for a tenant")
	public ApiResponse<AdminTenantAiSettingsResponse> update(
			@PathVariable("uuid") UUID tenantPublicUuid,
			@AuthenticationPrincipal JwtAuthenticatedPrincipal actor,
			@Valid @RequestBody AdminTenantAiSettingsRequest request) {
		return ApiResponse.ok(service.update(tenantPublicUuid, request.enabled(),
				actor != null ? actor.getId() : null));
	}
}
