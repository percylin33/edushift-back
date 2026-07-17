package com.edushift.modules.admin.metrics;

import com.edushift.modules.admin.metrics.AdminMetricsService.MetricsByTenant;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/metrics")
@Validated
@Tag(name = "Admin Metrics", description = "Platform usage metrics (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMetricsController {

    private final AdminMetricsService metricsService;

    @GetMapping("/students")
    @Operation(summary = "Active students by tenant")
    public ApiResponse<MetricsByTenant> getStudents() {
        return ApiResponse.ok(metricsService.getStudentsByTenant());
    }

    @GetMapping("/teachers")
    @Operation(summary = "Active teachers by tenant")
    public ApiResponse<MetricsByTenant> getTeachers() {
        return ApiResponse.ok(metricsService.getTeachersByTenant());
    }

    @GetMapping("/storage")
    @Operation(summary = "Storage used by tenant (from file_objects)")
    public ApiResponse<MetricsByTenant> getStorage() {
        return ApiResponse.ok(metricsService.getStorageByTenant());
    }

    @GetMapping("/ai")
    @Operation(summary = "AI usage by tenant")
    public ApiResponse<MetricsByTenant> getAiUsage() {
        return ApiResponse.ok(metricsService.getAiUsageByTenant());
    }
}
