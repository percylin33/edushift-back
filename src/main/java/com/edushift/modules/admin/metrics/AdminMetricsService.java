package com.edushift.modules.admin.metrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private final JdbcTemplate jdbcTemplate;

    public MetricsByTenant getStudentsByTenant() {
        List<TenantMetric> byTenant = jdbcTemplate.query(
                "SELECT t.id AS tenant_uuid, t.name AS tenant_name, "
                        + "COUNT(s.id) AS value "
                        + "FROM edushift.tenants t "
                        + "LEFT JOIN edushift.students s ON s.tenant_id = t.id "
                        + "  AND s.enrollment_status = 'ENROLLED' AND s.deleted = false "
                        + "WHERE t.deleted = false GROUP BY t.id, t.name",
                (rs, rowNum) -> new TenantMetric(
                        (UUID) rs.getObject("tenant_uuid"),
                        rs.getString("tenant_name"),
                        rs.getLong("value")));

        long total = byTenant.stream().mapToLong(TenantMetric::value).sum();
        return new MetricsByTenant(total, byTenant, Instant.now());
    }

    public MetricsByTenant getTeachersByTenant() {
        List<TenantMetric> byTenant = jdbcTemplate.query(
                "SELECT t.id AS tenant_uuid, t.name AS tenant_name, "
                        + "COUNT(u.id) AS value "
                        + "FROM edushift.tenants t "
                        + "LEFT JOIN edushift.users u ON u.tenant_id = t.id "
                        + "  AND u.status = 'ACTIVE' AND u.deleted = false "
                        + "  AND 'TEACHER' = ANY(u.roles) "
                        + "WHERE t.deleted = false GROUP BY t.id, t.name",
                (rs, rowNum) -> new TenantMetric(
                        (UUID) rs.getObject("tenant_uuid"),
                        rs.getString("tenant_name"),
                        rs.getLong("value")));

        long total = byTenant.stream().mapToLong(TenantMetric::value).sum();
        return new MetricsByTenant(total, byTenant, Instant.now());
    }

    public MetricsByTenant getStorageByTenant() {
        List<TenantMetric> byTenant = jdbcTemplate.query(
                "SELECT t.id AS tenant_uuid, t.name AS tenant_name, "
                        + "COALESCE(SUM(fo.size_bytes), 0) AS value "
                        + "FROM edushift.tenants t "
                        + "LEFT JOIN edushift.lms_file_objects fo ON fo.tenant_id = t.id AND fo.deleted = false "
                        + "WHERE t.deleted = false GROUP BY t.id, t.name",
                (rs, rowNum) -> new TenantMetric(
                        (UUID) rs.getObject("tenant_uuid"),
                        rs.getString("tenant_name"),
                        rs.getLong("value")));

        long total = byTenant.stream().mapToLong(TenantMetric::value).sum();
        return new MetricsByTenant(total, byTenant, Instant.now());
    }

    public MetricsByTenant getAiUsageByTenant() {
        List<TenantMetric> byTenant = jdbcTemplate.query(
                "SELECT t.id AS tenant_uuid, t.name AS tenant_name, "
                        + "COALESCE(COUNT(ag.id), 0) AS value "
                        + "FROM edushift.tenants t "
                        + "LEFT JOIN edushift.ai_generations ag ON ag.tenant_id = t.id AND ag.deleted = false "
                        + "WHERE t.deleted = false GROUP BY t.id, t.name",
                (rs, rowNum) -> new TenantMetric(
                        (UUID) rs.getObject("tenant_uuid"),
                        rs.getString("tenant_name"),
                        rs.getLong("value")));

        long total = byTenant.stream().mapToLong(TenantMetric::value).sum();
        return new MetricsByTenant(total, byTenant, Instant.now());
    }

    public record TenantMetric(UUID tenantUuid, String tenantName, long value) {}

    public record MetricsByTenant(long total, List<TenantMetric> byTenant, Instant lastUpdated) {}
}
