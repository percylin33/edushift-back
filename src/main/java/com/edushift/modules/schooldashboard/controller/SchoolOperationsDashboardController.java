package com.edushift.modules.schooldashboard.controller;

import com.edushift.modules.schooldashboard.dto.SchoolOperationsDashboardSummary;
import com.edushift.modules.schooldashboard.service.SchoolOperationsDashboardService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-admin view of school operations without student-level rankings. */
@RestController
@RequestMapping("/school-dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "School operations dashboard")
public class SchoolOperationsDashboardController {
	private final SchoolOperationsDashboardService dashboardService;

	@GetMapping("/summary")
	@Operation(summary = "Aggregated operational status for the active school year")
	public ApiResponse<SchoolOperationsDashboardSummary> summary() {
		return ApiResponse.ok(dashboardService.getSummary());
	}
}
