package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.ScheduleWarningItem;
import com.edushift.modules.schedule.daytemplate.service.ScheduleValidationService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Validation", description = "Soft schedule validation warnings")
public class ScheduleValidationController {

	private final ScheduleValidationService service;

	@GetMapping("/schedule/validation-warnings")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List soft validation warnings (mono/poli/homeroom/specialist)")
	public ResponseEntity<ApiResponse<List<ScheduleWarningItem>>> list(
			@RequestParam(required = false) UUID yearUuid) {
		return ResponseEntity.ok(ApiResponse.ok(service.listWarnings(yearUuid)));
	}
}
