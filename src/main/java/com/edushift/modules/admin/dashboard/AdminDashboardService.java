package com.edushift.modules.admin.dashboard;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardKpis getKpis() {
        Long mrr = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(p.price_per_student_cents::bigint * student_counts.active_student_count), 0)
                FROM edushift.b2b_subscriptions s
                JOIN edushift.tenants t ON t.id = s.tenant_id AND t.status = 'ACTIVE' AND t.deleted = false
                JOIN edushift.platform_plans p ON p.id = s.plan_id AND p.deleted = false
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS active_student_count
                    FROM edushift.students st
                    WHERE st.tenant_id = t.id
                      AND st.enrollment_status = 'ENROLLED'
                      AND st.deleted = false
                ) student_counts ON true
                WHERE s.status = 'ACTIVE' AND s.deleted = false
                """,
                Long.class);

        Integer activeTenants = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edushift.tenants WHERE status = 'ACTIVE' AND deleted = false "
                        + "AND id <> '00000000-0000-0000-0000-000000000001'",
                Integer.class);

        Integer activeStudents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM edushift.students s
                JOIN edushift.tenants t ON t.id = s.tenant_id
                WHERE s.enrollment_status = 'ENROLLED'
                  AND s.deleted = false
                  AND t.status = 'ACTIVE'
                  AND t.deleted = false
                """,
                Integer.class);

        Long overdue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status = 'OVERDUE' AND deleted = false",
                Long.class);

        Long pending = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status IN ('PENDING', 'OVERDUE') AND deleted = false",
                Long.class);

        Long collectedThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status = 'PAID' AND paid_at >= DATE_TRUNC('month', NOW()) AND deleted = false",
                Long.class);

        Long issuedThisMonth = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                        + "WHERE status IN ('PENDING', 'PAID', 'OVERDUE') "
                        + "AND issued_at >= DATE_TRUNC('month', NOW()) AND deleted = false",
                Long.class);

        Long collectedFromCurrentInvoices = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_cents), 0) FROM edushift.b2b_invoices "
                + "WHERE status = 'PAID' AND issued_at >= DATE_TRUNC('month', NOW()) AND deleted = false",
                Long.class);

        double collectionRate = (issuedThisMonth != null && issuedThisMonth > 0 && collectedFromCurrentInvoices != null)
                ? (double) collectedFromCurrentInvoices / issuedThisMonth : 0.0;

        return new DashboardKpis(
                mrr != null ? mrr : 0,
                activeTenants != null ? activeTenants : 0,
                activeStudents != null ? activeStudents : 0,
                overdue != null ? overdue : 0,
                pending != null ? pending : 0,
                collectedThisMonth != null ? collectedThisMonth : 0,
                Math.round(collectionRate * 100.0) / 100.0,
                Instant.now());
    }

    public List<MonthlyRevenue> getRevenueTrend(int months) {
        return jdbcTemplate.query(
                """
                WITH month_range AS (
                    SELECT generate_series(
                        DATE_TRUNC('month', NOW()) - CAST(? AS interval),
                        DATE_TRUNC('month', NOW()),
                        INTERVAL '1 month'
                    )::date AS month
                ), invoice_totals AS (
                    SELECT DATE_TRUNC('month', issued_at)::date AS month,
                           SUM(CASE WHEN status = 'PAID' THEN total_cents ELSE 0 END) AS collected,
                           SUM(CASE WHEN status = 'OVERDUE' THEN total_cents ELSE 0 END) AS overdue,
                           COUNT(DISTINCT tenant_id) AS tenant_count
                    FROM edushift.b2b_invoices
                    WHERE deleted = false
                      AND status IN ('PENDING', 'PAID', 'OVERDUE')
                      AND issued_at >= DATE_TRUNC('month', NOW()) - CAST(? AS interval)
                    GROUP BY 1
                )
                SELECT month_range.month,
                       COALESCE(invoice_totals.collected, 0) AS collected,
                       COALESCE(invoice_totals.overdue, 0) AS overdue,
                       COALESCE(invoice_totals.tenant_count, 0) AS tenant_count
                FROM month_range
                LEFT JOIN invoice_totals ON invoice_totals.month = month_range.month
                ORDER BY month_range.month
                """,
                (rs, rowNum) -> new MonthlyRevenue(
                        rs.getObject("month", java.time.LocalDate.class),
                        rs.getLong("collected"),
                        rs.getLong("overdue"),
                        rs.getInt("tenant_count")),
                (months - 1) + " months", (months - 1) + " months");
    }

    public List<NewInstitutionsTrend> getNewInstitutionsTrend(int months) {
        return jdbcTemplate.query(
                """
                WITH month_range AS (
                    SELECT generate_series(
                        DATE_TRUNC('month', NOW()) - CAST(? AS interval),
                        DATE_TRUNC('month', NOW()),
                        INTERVAL '1 month'
                    )::date AS month
                ), new_tenants AS (
                    SELECT DATE_TRUNC('month', created_at)::date AS month, COUNT(*) AS count
                    FROM edushift.tenants
                    WHERE deleted = false
                      AND id <> '00000000-0000-0000-0000-000000000001'
                      AND created_at >= DATE_TRUNC('month', NOW()) - CAST(? AS interval)
                    GROUP BY 1
                )
                SELECT month_range.month, COALESCE(new_tenants.count, 0) AS count
                FROM month_range
                LEFT JOIN new_tenants ON new_tenants.month = month_range.month
                ORDER BY month_range.month
                """,
                (rs, rowNum) -> new NewInstitutionsTrend(
                        rs.getObject("month", java.time.LocalDate.class),
                        rs.getInt("count")),
                (months - 1) + " months", (months - 1) + " months");
    }

    public PlanDistribution getPlanDistribution() {
        List<String> labels = jdbcTemplate.query(
                "SELECT name FROM edushift.platform_plans WHERE is_active = true AND deleted = false ORDER BY sort_order, code",
                (rs, rowNum) -> rs.getString("name"));

        List<Long> values = jdbcTemplate.query(
                "SELECT p.code, COUNT(t.id) AS count "
                        + "FROM edushift.platform_plans p "
                        + "LEFT JOIN edushift.tenants t ON t.plan_id = p.id AND t.deleted = false "
                        + "WHERE p.is_active = true AND p.deleted = false GROUP BY p.code, p.sort_order ORDER BY p.sort_order, p.code",
                (rs, rowNum) -> rs.getLong("count"));

        return new PlanDistribution(
                labels.toArray(String[]::new),
                values.toArray(Long[]::new));
    }

    public PlanDistribution getStudentsByPlan() {
        List<String> labels = jdbcTemplate.query(
                "SELECT name FROM edushift.platform_plans WHERE is_active = true AND deleted = false ORDER BY sort_order, code",
                (rs, rowNum) -> rs.getString("name"));

        List<Long> values = jdbcTemplate.query(
                "SELECT p.code, COUNT(s.id) AS count "
                        + "FROM edushift.platform_plans p "
                        + "LEFT JOIN edushift.tenants t ON t.plan_id = p.id AND t.deleted = false AND t.status = 'ACTIVE' "
                        + "LEFT JOIN edushift.students s ON s.tenant_id = t.id "
                        + "  AND s.enrollment_status = 'ENROLLED' AND s.deleted = false "
                        + "WHERE p.is_active = true AND p.deleted = false GROUP BY p.code, p.sort_order ORDER BY p.sort_order, p.code",
                (rs, rowNum) -> rs.getLong("count"));

        return new PlanDistribution(
                labels.toArray(String[]::new),
                values.toArray(Long[]::new));
    }

    public List<TopTenant> getTopTenants(int limit, String month) {
        return jdbcTemplate.query(
                """
                WITH monthly_invoices AS (
                    SELECT tenant_id, SUM(total_cents) AS revenue
                    FROM edushift.b2b_invoices
                    WHERE deleted = false
                      AND status IN ('PENDING', 'PAID', 'OVERDUE')
                      AND issued_at >= CAST(? AS date)
                      AND issued_at < (CAST(? AS date) + INTERVAL '1 month')
                    GROUP BY tenant_id
                ), active_students AS (
                    SELECT tenant_id, COUNT(*) AS student_count
                    FROM edushift.students
                    WHERE enrollment_status = 'ENROLLED' AND deleted = false
                    GROUP BY tenant_id
                )
                SELECT t.name, t.public_uuid, monthly_invoices.revenue,
                       pp.code AS plan, COALESCE(active_students.student_count, 0) AS student_count
                FROM monthly_invoices
                JOIN edushift.tenants t ON t.id = monthly_invoices.tenant_id
                LEFT JOIN edushift.platform_plans pp ON pp.id = t.plan_id
                LEFT JOIN active_students ON active_students.tenant_id = t.id
                WHERE t.deleted = false
                ORDER BY monthly_invoices.revenue DESC LIMIT ?
                """,
                (rs, rowNum) -> new TopTenant(
                        rs.getString("name"),
                        rs.getObject("public_uuid", java.util.UUID.class),
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
            long receivableCents,
            long collectedThisMonthCents,
            double collectionRate,
            Instant lastUpdated
    ) {}

    public record MonthlyRevenue(
            java.time.LocalDate month,
            long collected,
            long overdue,
            int tenantCount
    ) {}

    public record NewInstitutionsTrend(
            java.time.LocalDate month,
            int count
    ) {}

    public record PlanDistribution(
            String[] labels,
            Long[] values
    ) {}

    public record TopTenant(
            String name,
            java.util.UUID publicUuid,
            long revenueCents,
            String plan,
            int studentCount
    ) {}
}
