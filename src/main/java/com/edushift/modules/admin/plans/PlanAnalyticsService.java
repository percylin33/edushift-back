package com.edushift.modules.admin.plans;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<PlanAnalyticsRow> list() {
        String sql = """
                WITH active_subscriptions AS (
                    SELECT s.tenant_id, s.plan_id
                    FROM edushift.b2b_subscriptions s
                    JOIN edushift.tenants t ON t.id = s.tenant_id
                    WHERE s.deleted = false AND s.status = 'ACTIVE'
                      AND t.deleted = false AND t.status = 'ACTIVE'
                ), student_counts AS (
                    SELECT st.tenant_id, COUNT(*) AS enrolled_students
                    FROM edushift.students st
                    WHERE st.deleted = false AND st.enrollment_status = 'ENROLLED'
                    GROUP BY st.tenant_id
                ), ai_usage AS (
                    SELECT u.tenant_id, SUM(u.tokens_in_total + u.tokens_out_total) AS tokens_used
                    FROM edushift.tenant_ai_usage u
                    WHERE u.usage_day >= ? AND u.usage_day < ?
                    GROUP BY u.tenant_id
                )
                SELECT p.id AS plan_id,
                       COUNT(DISTINCT a.tenant_id) AS active_institutions,
                       COALESCE(SUM(COALESCE(sc.enrolled_students, 0)), 0) AS enrolled_students,
                       COALESCE(SUM(CASE WHEN a.tenant_id IS NOT NULL
                           THEN COALESCE(sc.enrolled_students, 0) * p.price_per_student_cents ELSE 0 END), 0)
                           AS projected_mrr_cents,
                       COALESCE(SUM(CASE WHEN tas.ai_enabled = true
                           THEN COALESCE(au.tokens_used, 0) ELSE 0 END), 0) AS ai_tokens_used,
                       COALESCE(SUM(CASE WHEN tas.ai_enabled = true AND pol.plan_id IS NOT NULL
                           THEN LEAST(pol.monthly_token_limit,
                                      COALESCE(tas.monthly_token_quota, pol.monthly_token_limit))
                           ELSE 0 END), 0) AS ai_token_quota
                FROM edushift.platform_plans p
                LEFT JOIN active_subscriptions a ON a.plan_id = p.id
                LEFT JOIN student_counts sc ON sc.tenant_id = a.tenant_id
                LEFT JOIN edushift.tenants t ON t.id = a.tenant_id
                LEFT JOIN edushift.tenant_ai_settings tas ON tas.tenant_id = t.public_uuid
                LEFT JOIN ai_usage au ON au.tenant_id = t.public_uuid
                LEFT JOIN edushift.plan_ai_policies pol ON pol.plan_id = p.id AND pol.deleted = false
                WHERE p.deleted = false
                GROUP BY p.id
                ORDER BY p.sort_order, p.code
                """;
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new PlanAnalyticsRow(
                rs.getObject("plan_id", java.util.UUID.class),
                rs.getLong("active_institutions"),
                rs.getLong("enrolled_students"),
                rs.getLong("projected_mrr_cents"),
                rs.getLong("ai_tokens_used"),
                rs.getLong("ai_token_quota")),
                month.atDay(1), month.plusMonths(1).atDay(1));
    }
}
