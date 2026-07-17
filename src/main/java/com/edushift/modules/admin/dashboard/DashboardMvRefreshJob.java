package com.edushift.modules.admin.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardMvRefreshJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 * * * *")
    public void refreshMaterializedViews() {
        log.info("[dashboard-mv] refreshing materialized views...");
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY edushift.mv_monthly_revenue");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY edushift.mv_active_tenants_trend");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY edushift.mv_plan_distribution");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY edushift.mv_students_by_plan");
            log.info("[dashboard-mv] materialized views refreshed successfully");
        } catch (Exception e) {
            log.warn("[dashboard-mv] refresh failed (views may not exist yet): {}", e.getMessage());
        }
    }
}
