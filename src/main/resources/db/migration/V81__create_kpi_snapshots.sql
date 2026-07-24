-- =============================================================================
-- V81__create_kpi_snapshots.sql
--
-- Cierre-A / B1 — Analytics module (Sprint MVP cierre-A).
--
-- Tabla `kpi_snapshots`: snapshots materializados de KPIs por tenant.
-- Permite cachear resultados agregados (ATTENDANCE_RATE, PERFORMANCE_AVG,
-- MOROSIDAD) para que el dashboard no recalcule agregaciones pesadas en
-- cada request. La tabla es append-only (se inserta, nunca se modifica).
--
-- Decisiones:
--   * ADR-Cierre-A.1 — append-only, sin soft-delete. Refresh = INSERT new row.
--   * ADR-Cierre-A.1 — multi-tenant via columna `tenant_id` + Hibernate
--     `@TenantId` discriminator. Cross-tenant SELECT solo desde SUPER_ADMIN.
--   * ADR-Cierre-A.1 — `metric_key` + `period_start` + `period_end` +
--     `dimensions_hash` forman la UNIQUE constraint para idempotencia del
--     job de cache warming (@Scheduled). Mismo (metric, period, dims) no
--     genera dos filas.
--   * `value_numeric` cubre el caso comun (rates 0..1, promedios). Para
--     KPIs complejos (multi-metric) el JSONB `dimensions` carga el resto.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.kpi_snapshots (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    metric_key              varchar(64)   NOT NULL,
    period_start            timestamptz   NOT NULL,
    period_end              timestamptz   NOT NULL,
    value_numeric           numeric(18,6),
    dimensions              jsonb         NOT NULL DEFAULT '{}'::jsonb,
    dimensions_hash         varchar(64)   NOT NULL,
    computed_at             timestamptz   NOT NULL,

    CONSTRAINT uk_kpi_snapshots_idempotency
        UNIQUE (tenant_id, metric_key, period_start, period_end, dimensions_hash),

    CONSTRAINT chk_kpi_snapshots_period
        CHECK (period_end > period_start),

    CONSTRAINT chk_kpi_snapshots_metric_key
        CHECK (metric_key IN (
            'ATTENDANCE_RATE',
            'PERFORMANCE_AVG',
            'MOROSIDAD'
        )),

    CONSTRAINT chk_kpi_snapshots_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    CONSTRAINT fk_kpi_snapshots_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

-- Hot path: GET /v1/analytics/kpis (sumario actual del tenant).
CREATE INDEX idx_kpi_snapshots_tenant_metric_computed
    ON edushift.kpi_snapshots (tenant_id, metric_key, computed_at DESC)
    WHERE deleted = false;

-- Hot path: GET /v1/analytics/charts/{attendance|performance|morosidad}
-- (series temporal por periodo).
CREATE INDEX idx_kpi_snapshots_tenant_metric_period
    ON edushift.kpi_snapshots (tenant_id, metric_key, period_start DESC)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_kpi_snapshots
    BEFORE UPDATE ON edushift.kpi_snapshots
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.kpi_snapshots                          IS 'Cached KPI snapshots per tenant. Append-only on refresh (Sprint cierre-A / B1).';
COMMENT ON COLUMN edushift.kpi_snapshots.metric_key               IS 'ATTENDANCE_RATE | PERFORMANCE_AVG | MOROSIDAD. CHECK constraint enforces whitelist.';
COMMENT ON COLUMN edushift.kpi_snapshots.value_numeric            IS 'Primary scalar value (rate 0..1 for rates, raw avg for PERFORMANCE_AVG).';
COMMENT ON COLUMN edushift.kpi_snapshots.dimensions               IS 'JSONB breakdown (e.g. per-section counts, numerator/denominator for rate KPIs).';
COMMENT ON COLUMN edushift.kpi_snapshots.dimensions_hash          IS 'SHA-256 hex of canonicalized dimensions JSON. Idempotency key part.';
COMMENT ON COLUMN edushift.kpi_snapshots.computed_at              IS 'When the source query ran. Distinct from created_at (DB write time).';