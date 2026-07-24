package com.edushift.modules.analytics.controller;

import com.edushift.modules.analytics.dto.ChartSeriesResponse;
import com.edushift.modules.analytics.dto.KpiSummaryResponse;
import com.edushift.modules.analytics.service.AnalyticsService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped analytics read API (Sprint cierre-A / B1).
 *
 * <p>All endpoints resolve the tenant from {@code TenantContext}; no
 * tenant id is accepted from the client. Auth: any authenticated user
 * within the tenant — the data shown is dashboard-friendly and
 * non-sensitive aggregate metrics.</p>
 *
 * <p>Roles:
 * <ul>
 *   <li>{@code ROLE_TENANT_ADMIN}, {@code ROLE_TEACHER}, {@code ROLE_STUDENT}
 *       — can read; the response shape is the same for all.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/analytics")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','TEACHER','STUDENT')")
@Tag(name = "Analytics", description = "Tenant KPI summary + chart series (Sprint cierre-A / B1)")
@RequiredArgsConstructor
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	@GetMapping("/kpis")
	@Operation(summary = "Current-period KPI summary (attendance, performance, morosidad)")
	public ApiResponse<KpiSummaryResponse> kpis() {
		return ApiResponse.ok(analyticsService.currentSummary());
	}

	@GetMapping("/charts/attendance")
	@Operation(summary = "Time series of ATTENDANCE_RATE over the requested period")
	public ApiResponse<ChartSeriesResponse> attendanceChart(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return ApiResponse.ok(analyticsService.attendanceSeries(from, to));
	}

	@GetMapping("/charts/performance")
	@Operation(summary = "Time series of PERFORMANCE_AVG over the requested period")
	public ApiResponse<ChartSeriesResponse> performanceChart(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return ApiResponse.ok(analyticsService.performanceSeries(from, to));
	}

	@GetMapping("/charts/morosidad")
	@Operation(summary = "Time series of MOROSIDAD over the requested period")
	public ApiResponse<ChartSeriesResponse> morosidadChart(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
		return ApiResponse.ok(analyticsService.morosidadSeries(from, to));
	}
}