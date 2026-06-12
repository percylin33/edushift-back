package com.edushift.modules.attendance.controller;

import com.edushift.modules.attendance.dto.DashboardOverviewResponse;
import com.edushift.modules.attendance.service.DashboardService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the admin attendance dashboard
 * (Sprint 6 / BE-6.5).
 *
 * <h3>Endpoint</h3>
 * <table>
 *   <caption>Dashboard endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Auth</th><th>Returns</th></tr>
 *   <tr><td>GET</td>
 *       <td>/v1/attendance/dashboard/overview</td>
 *       <td>TENANT_ADMIN</td>
 *       <td>{@link ApiResponse}&lt;{@link DashboardOverviewResponse}&gt;</td></tr>
 * </table>
 *
 * <h3>Why TENANT_ADMIN only</h3>
 * The dashboard exposes aggregate counts (absent count per section,
 * today attendance rate) that an individual teacher does not need —
 * their work flow is the per-section roster
 * ({@code GET /v1/attendance/sessions/{id}/records}). Restricting the
 * surface to {@code TENANT_ADMIN} aligns with the rest of the
 * analytics-style endpoints in the project (e.g. the audit log
 * browse surface).
 *
 * <h3>Latency &amp; caching</h3>
 * Five small aggregate queries; no per-row hydrate. The response
 * carries {@code generatedAt} so the FE can render
 * "Actualizado hace 3s" and re-poll on a 30s timer. A future Redis
 * cache (TTL 30s) can drop behind the same controller without
 * breaking clients.
 */
@Slf4j
@RestController
@RequestMapping("/attendance/dashboard")
@RequiredArgsConstructor
@Tag(name = "AttendanceDashboard",
		description = "Admin-only attendance KPIs: today rate, open "
				+ "sessions, unique students today, total absences, "
				+ "top absent sections (7d) and recent closed sessions.")
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping(value = "/overview",
			produces = MediaType.APPLICATION_JSON_VALUE)
	@SecurityRequirement(name = "bearerAuth")
	@PreAuthorize("hasRole('TENANT_ADMIN')")
	@Operation(summary = "Admin attendance dashboard overview",
			description = "Single-roundtrip snapshot. All numbers are "
					+ "scoped to the bearer tenant; timezone is UTC "
					+ "until ADR-6.12 lands.")
	public ResponseEntity<ApiResponse<DashboardOverviewResponse>> overview() {
		DashboardOverviewResponse response = dashboardService.getOverview();
		log.debug("[attendance-api] dashboard overview -- open={} absent={} "
						+ "rate={} unique={} topAbsent={} recentClosed={}",
				response.openSessions(), response.totalAbsencesToday(),
				response.attendanceRateToday(),
				response.uniqueStudentsRegisteredToday(),
				response.topAbsentSections().size(),
				response.recentClosedSessions().size());
		return ResponseEntity.ok(ApiResponse.ok(response));
	}
}
