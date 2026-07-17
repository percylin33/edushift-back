package com.edushift.modules.admin.dashboard;

import com.edushift.modules.admin.dashboard.AdminDashboardService.ActiveTenantsTrend;
import com.edushift.modules.admin.dashboard.AdminDashboardService.DashboardKpis;
import com.edushift.modules.admin.dashboard.AdminDashboardService.MonthlyRevenue;
import com.edushift.modules.admin.dashboard.AdminDashboardService.PlanDistribution;
import com.edushift.modules.admin.dashboard.AdminDashboardService.TopTenant;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@Validated
@Tag(name = "Admin Dashboard", description = "Financial dashboard (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/kpis")
    @Operation(summary = "Get dashboard KPIs (MRR, active tenants, active students, overdue)")
    public ApiResponse<DashboardKpis> getKpis() {
        return ApiResponse.ok(dashboardService.getKpis());
    }

    @GetMapping("/revenue-trend")
    @Operation(summary = "Monthly revenue trend (collected, overdue, tenant count)")
    public ApiResponse<List<MonthlyRevenue>> getRevenueTrend(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(dashboardService.getRevenueTrend(months));
    }

    @GetMapping("/active-tenants")
    @Operation(summary = "Active tenants trend by month")
    public ApiResponse<List<ActiveTenantsTrend>> getActiveTenantsTrend(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(dashboardService.getActiveTenantsTrend(months));
    }

    @GetMapping("/plan-distribution")
    @Operation(summary = "Plan distribution (doughnut chart)")
    public ApiResponse<PlanDistribution> getPlanDistribution() {
        return ApiResponse.ok(dashboardService.getPlanDistribution());
    }

    @GetMapping("/top-tenants")
    @Operation(summary = "Top N tenants by revenue for a given month")
    public ApiResponse<List<TopTenant>> getTopTenants(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String month) {
        String m = month != null ? month : java.time.LocalDate.now().withDayOfMonth(1).toString().substring(0, 7);
        return ApiResponse.ok(dashboardService.getTopTenants(limit, m));
    }

    @GetMapping("/collection-vs-overdue")
    @Operation(summary = "Collection vs overdue for the last N months (stacked bar)")
    public ApiResponse<List<MonthlyRevenue>> getCollectionVsOverdue(
            @RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(dashboardService.getRevenueTrend(months));
    }

    @GetMapping("/students-by-plan")
    @Operation(summary = "Students count grouped by plan")
    public ApiResponse<PlanDistribution> getStudentsByPlan() {
        return ApiResponse.ok(dashboardService.getStudentsByPlan());
    }
}
