package com.edushift.modules.admin.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardKpis getKpis() {
        Integer mrr = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status = 'PAID' AND issued_at >= DATE_TRUNC('month', NOW())",
                Integer.class);

        Integer activeTenants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edushift.tenants WHERE status = 'ACTIVE' AND deleted = false",
                Integer.class);

        Integer activeStudents = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT id) FROM edushift.students WHERE enrollment_status = 'ENROLLED' AND deleted = false",
                Integer.class);

        Integer overdue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status = 'OVERDUE'",
                Integer.class);

        Integer totalBilled = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE issued_at >= DATE_TRUNC('month', NOW())",
                Integer.class);

        double collectionRate = (totalBilled != null && totalBilled > 0 && mrr != null)
                ? (double) mrr / totalBilled : 0.0;

        return new DashboardKpis(
                mrr != null ? mrr : 0,
                activeTenants != null ? activeTenants : 0,
                activeStudents != null ? activeStudents : 0,
                overdue != null ? overdue : 0,
                Math.round(collectionRate * 100.0) / 100.0,
                Instant.now());
    }

    public List<MonthlyRevenue> getRevenueTrend(int months) {
        return jdbcTemplate.query(
                """
                SELECT DATE_TRUNC('month', issued_at)::date AS month,
                       COALESCE(SUM(CASE WHEN status = 'PAID' THEN total_cents ELSE 0 END), 0) AS collected,
                       COALESCE(SUM(CASE WHEN status = 'OVERDUE' THEN total_cents ELSE 0 END), 0) AS overdue,
                       COUNT(DISTINCT tenant_id) AS tenant_count
                FROM edushift.b2b_invoices
                WHERE issued_at >= DATE_TRUNC('month', NOW()) - CAST(? AS interval)
                GROUP BY 1 ORDER BY 1
                """,
                (rs, rowNum) -> new MonthlyRevenue(
                        rs.getObject("month", java.time.LocalDate.class),
                        rs.getLong("collected"),
                        rs.getLong("overdue"),
                        rs.getInt("tenant_count")),
                months + " months");
    }

    public List<ActiveTenantsTrend> getActiveTenantsTrend(int months) {
        return jdbcTemplate.query(
                """
                SELECT DATE_TRUNC('month', created_at)::date AS month, COUNT(*) AS count
                FROM edushift.tenants
                WHERE deleted = false
                  AND created_at >= DATE_TRUNC('month', NOW()) - CAST(? AS interval)
                GROUP BY 1 ORDER BY 1
                """,
                (rs, rowNum) -> new ActiveTenantsTrend(
                        rs.getObject("month", java.time.LocalDate.class),
                        rs.getInt("count")),
                months + " months");
    }

    public PlanDistribution getPlanDistribution() {
        List<String> labels = jdbcTemplate.query(
                "SELECT code FROM edushift.platform_plans WHERE is_active = true ORDER BY sort_order",
                (rs, rowNum) -> rs.getString("code"));

        List<Long> values = jdbcTemplate.query(
                "SELECT p.code, COUNT(t.id) AS count "
                        + "FROM edushift.platform_plans p "
                        + "LEFT JOIN edushift.tenants t ON t.plan_id = p.id AND t.deleted = false "
                        + "WHERE p.is_active = true GROUP BY p.code, p.sort_order ORDER BY p.sort_order",
                (rs, rowNum) -> rs.getLong("count"));

        return new PlanDistribution(
                labels.toArray(String[]::new),
                values.toArray(Long[]::new));
    }

    public PlanDistribution getStudentsByPlan() {
        List<String> labels = jdbcTemplate.query(
                "SELECT code FROM edushift.platform_plans WHERE is_active = true ORDER BY sort_order",
                (rs, rowNum) -> rs.getString("code"));

        List<Long> values = jdbcTemplate.query(
                "SELECT p.code, COUNT(s.id) AS count "
                        + "FROM edushift.platform_plans p "
                        + "LEFT JOIN edushift.tenants t ON t.plan_id = p.id AND t.deleted = false "
                        + "LEFT JOIN edushift.students s ON s.tenant_id = t.id "
                        + "  AND s.enrollment_status = 'ENROLLED' AND s.deleted = false "
                        + "WHERE p.is_active = true GROUP BY p.code, p.sort_order ORDER BY p.sort_order",
                (rs, rowNum) -> rs.getLong("count"));

        return new PlanDistribution(
                labels.toArray(String[]::new),
                values.toArray(Long[]::new));
    }

    public List<TopTenant> getTopTenants(int limit, String month) {
        return jdbcTemplate.query(
                """
                SELECT t.name, t.slug, COALESCE(SUM(i.total_cents), 0) AS revenue,
                       pp.code AS plan, COUNT(DISTINCT s.id) AS student_count
                FROM edushift.tenants t
                JOIN edushift.b2b_invoices i ON i.tenant_id = t.id
                LEFT JOIN edushift.platform_plans pp ON pp.id = t.plan_id
                LEFT JOIN edushift.students s ON s.tenant_id = t.id AND s.enrollment_status = 'ENROLLED' AND s.deleted = false
                WHERE t.deleted = false
                  AND (i.issued_at >= CAST(? AS date) AND i.issued_at < (CAST(? AS date) + INTERVAL '1 month'))
                GROUP BY t.id, t.name, t.slug, pp.code
                ORDER BY revenue DESC LIMIT ?
                """,
                (rs, rowNum) -> new TopTenant(
                        rs.getString("name"),
                        rs.getLong("revenue"),
                        rs.getString("plan"),
                        rs.getInt("student_count")),
                month + "-01", month + "-01", limit);
    }

    public record DashboardKpis(
            long mrrCents,
            int activeTenants,
            int activeStudents,
            long overdueCents,
            double collectionRate,
            Instant lastUpdated
    ) {}

    public record MonthlyRevenue(
            java.time.LocalDate month,
            long collected,
            long overdue,
            int tenantCount
    ) {}

    public record ActiveTenantsTrend(
            java.time.LocalDate month,
            int count
    ) {}

    public record PlanDistribution(
            String[] labels,
            Long[] values
    ) {}

    public record TopTenant(
            String name,
            long revenueCents,
            String plan,
            int studentCount
    ) {}
}
