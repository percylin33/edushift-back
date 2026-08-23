package com.edushift.modules.schedule.daytemplate.controller;

import com.edushift.modules.schedule.daytemplate.dto.TeacherWorkloadItem;
import com.edushift.modules.schedule.daytemplate.service.TeacherWorkloadService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Schedule — Teacher Workload",
		description = "Weekly teaching minutes from active TimeSlots")
public class TeacherWorkloadController {

	private final TeacherWorkloadService service;

	@GetMapping("/teachers/workload")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "List teacher workload for a period")
	public ResponseEntity<ApiResponse<List<TeacherWorkloadItem>>> list(
			@RequestParam("periodId") UUID periodId) {
		return ResponseEntity.ok(ApiResponse.ok(service.listWorkload(periodId)));
	}

	@GetMapping("/teachers/{uuid}/workload")
	@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER')")
	@Operation(summary = "Get one teacher's workload for a period")
	public ResponseEntity<ApiResponse<TeacherWorkloadItem>> getOne(
			@PathVariable UUID uuid,
			@RequestParam("periodId") UUID periodId) {
		return ResponseEntity.ok(ApiResponse.ok(service.getWorkload(uuid, periodId)));
	}
}
