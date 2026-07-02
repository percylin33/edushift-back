-- =============================================================================
-- V56__create_dashboard_materialized_views.sql
--
-- Sprint 15 (SUPER_ADMIN module) / BE-15.8 — Vistas materializadas para el
-- dashboard financiero del SUPER_ADMIN.
--
-- Decisión (ADR-15.10): agregaciones costosas (MRR, distribucion de planes,
-- estudiantes por plan) se pre-calculan en vistas materializadas y se refrescan
-- cada hora con `REFRESH MATERIALIZED VIEW CONCURRENTLY` (sin bloquear lecturas).
--
-- Cada vista lleva UNIQUE INDEX para permitir refresh concurrent y los indices
-- que necesita cada query downstream.
--
-- Las vistas son append-only en cuanto a datos (no se modifican por business
-- logic, solo por refresh). El trigger set_updated_at no aplica.
--
-- Monitoreo:
--   SELECT schemaname, matviewname, last_refresh_at
--   FROM pg_stat_user_tables
--   WHERE relname LIKE 'mv_%';
-- =============================================================================

-- =============================================================================
-- mv_monthly_revenue: ingresos mensuales (cobrado vs vencido) por mes
-- =============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS edushift.mv_monthly_revenue AS
SELECT
    DATE_TRUNC('month', i.issued_at)::date AS month,
    SUM(CASE WHEN i.status = 'PAID' THEN i.total_cents ELSE 0 END)::bigint AS collected_cents,
    SUM(CASE WHEN i.status = 'OVERDUE' THEN i.total_cents ELSE 0 END)::bigint AS overdue_cents,
    SUM(CASE WHEN i.status = 'PENDING' THEN i.total_cents ELSE 0 END)::bigint AS pending_cents,
    COUNT(DISTINCT i.tenant_id) AS tenant_count,
    COUNT(*) AS invoice_count
FROM edushift.b2b_invoices i
WHERE i.deleted = false
GROUP BY DATE_TRUNC('month', i.issued_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_monthly_revenue_month
    ON edushift.mv_monthly_revenue (month);

CREATE INDEX IF NOT EXISTS idx_mv_monthly_revenue_month_desc
    ON edushift.mv_monthly_revenue (month DESC);

COMMENT ON MATERIALIZED VIEW edushift.mv_monthly_revenue IS
    'Ingresos mensuales B2B agregados. Refrescada cada hora. '
    'Sirve al chart de revenue trend y al de cobrado vs vencido.';

-- =============================================================================
-- mv_active_tenants_trend: crecimiento de tenants activos por mes
-- =============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS edushift.mv_active_tenants_trend AS
WITH monthly AS (
    SELECT
        DATE_TRUNC('month', t.created_at)::date AS cohort_month,
        COUNT(*) AS new_tenants
    FROM edushift.tenants t
    WHERE t.deleted = false
      AND t.id <> '00000000-0000-0000-0000-000000000001'   -- excluimos sentinel
    GROUP BY DATE_TRUNC('month', t.created_at)
)
SELECT
    cohort_month AS month,
    new_tenants,
    SUM(new_tenants) OVER (ORDER BY cohort_month) AS cumulative_active_tenants
FROM monthly;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_active_tenants_trend_month
    ON edushift.mv_active_tenants_trend (month);

COMMENT ON MATERIALIZED VIEW edushift.mv_active_tenants_trend IS
    'Crecimiento de tenants activos. Refrescada cada hora. '
    'Sirve al chart de crecimiento de colegios.';

-- =============================================================================
-- mv_plan_distribution: cuantos tenants hay en cada plan (snapshot actual)
-- =============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS edushift.mv_plan_distribution AS
SELECT
    p.code AS plan_code,
    p.name AS plan_name,
    p.sort_order,
    p.price_per_student_cents,
    COUNT(t.id) AS tenant_count
FROM edushift.platform_plans p
LEFT JOIN edushift.tenants t ON t.plan_id = p.id
                              AND t.deleted = false
                              AND t.id <> '00000000-0000-0000-0000-000000000001'
WHERE p.is_active = true
  AND p.deleted = false
GROUP BY p.id, p.code, p.name, p.sort_order, p.price_per_student_cents
ORDER BY p.sort_order;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_plan_distribution_code
    ON edushift.mv_plan_distribution (plan_code);

COMMENT ON MATERIALIZED VIEW edushift.mv_plan_distribution IS
    'Distribucion actual de tenants por plan. Refrescada cada hora. '
    'Sirve al donut chart.';

-- =============================================================================
-- mv_students_by_plan: estudiantes activos agregados por plan
-- =============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS edushift.mv_students_by_plan AS
SELECT
    p.code AS plan_code,
    p.name AS plan_name,
    p.sort_order,
    COUNT(s.id) AS active_student_count,
    SUM(CASE WHEN s.id IS NOT NULL THEN 1 ELSE 0 END) AS total_enrolled
FROM edushift.platform_plans p
JOIN edushift.tenants t ON t.plan_id = p.id
                       AND t.deleted = false
                       AND t.id <> '00000000-0000-0000-0000-000000000001'
LEFT JOIN edushift.students s ON s.tenant_id = t.id
                              AND s.enrollment_status = 'ENROLLED'
                              AND s.deleted = false
WHERE p.is_active = true
  AND p.deleted = false
GROUP BY p.id, p.code, p.name, p.sort_order
ORDER BY p.sort_order;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mv_students_by_plan_code
    ON edushift.mv_students_by_plan (plan_code);

COMMENT ON MATERIALIZED VIEW edushift.mv_students_by_plan IS
    'Estudiantes activos agregados por plan. Refrescada cada hora. '
    'Sirve al grouped bar chart de estudiantes por plan.';

-- =============================================================================
-- Grant SELECT al role de la aplicacion (Postgres best practice)
-- =============================================================================
-- Asume que la app se conecta con un role dedicado. Si no es el caso, no hace dano.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'edushift_app') THEN
        GRANT SELECT ON edushift.mv_monthly_revenue       TO edushift_app;
        GRANT SELECT ON edushift.mv_active_tenants_trend  TO edushift_app;
        GRANT SELECT ON edushift.mv_plan_distribution     TO edushift_app;
        GRANT SELECT ON edushift.mv_students_by_plan      TO edushift_app;
    END IF;
END$$;
