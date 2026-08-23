package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleSettingsDto;
import com.edushift.modules.schedule.daytemplate.dto.UpdateScheduleSettingsRequest;
import com.edushift.modules.schedule.daytemplate.service.ScheduleSettingsService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Settings", description = "Tenant recess policy settings")
public class ScheduleSettingsController {

	private final ScheduleSettingsService service;

	@GetMapping("/schedule/settings")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Get schedule settings (recessPolicy, shareGroupLevelCodes)")
	public ResponseEntity<ApiResponse<ScheduleSettingsDto>> get() {
		return ResponseEntity.ok(ApiResponse.ok(service.getSettings()));
	}

	@PutMapping("/schedule/settings")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Update schedule settings")
	public ResponseEntity<ApiResponse<ScheduleSettingsDto>> update(
			@Valid @RequestBody UpdateScheduleSettingsRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateSettings(request)));
	}
}
