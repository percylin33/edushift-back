package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.CloneScheduleRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.service.ScheduleCloneService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Clone", description = "Clone day templates to a new academic year")
public class ScheduleCloneController {

	private final ScheduleCloneService service;

	@PostMapping("/academic/years/{yearUuid}/clone-schedule")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Clone day schedule templates from a source year into the target year")
	public ResponseEntity<ApiResponse<List<DayTemplateResponse>>> clone(
			@PathVariable UUID yearUuid,
			@Valid @RequestBody CloneScheduleRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(service.cloneSchedule(yearUuid, request)));
	}
}
