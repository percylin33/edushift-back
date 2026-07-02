-- =============================================================================
-- V54__create_platform_plans.sql
--
-- Sprint 15 (SUPER_ADMIN module) / BE-15.3 — Catálogo de planes en DB.
--
-- Reemplaza el enum `TenantPlan` hardcodeado (BASIC, PRO, ENTERPRISE) por una
-- tabla catálogo modificable sin deploy. SUPER_ADMIN puede crear/editar planes
-- sin cambiar código.
--
-- Decisión (ADR-15.8): planes en DB desde el día 1. La migración V54_1 migra
-- los datos existentes en `tenants.plan` (varchar) a `tenants.plan_id` (FK).
--
-- Precios por estudiante activo (snapshot al emitir invoice):
--   * BASIC       100 centavos = $1 / estudiante / mes
--   * PRO         200 centavos = $2 / estudiante / mes
--   * ENTERPRISE  300 centavos = $3 / estudiante / mes
--
-- Features por plan (jsonb array de strings):
--   * LMS                          acceso a materials/tasks/quizzes/submissions
--   * REPORTS                      módulo de reportes
--   * AI_GENERATE                  asistente + generación con IA
--   * PAYMENTS                     invoice/payment workflow
--   * API_ACCESS                   acceso a API pública (futuro)
--   * DEDICATED_SUPPORT            soporte prioritario (futuro)
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.platform_plans (
    id                       uuid           PRIMARY KEY,
    public_uuid              uuid           NOT NULL,
    name                     varchar(80)    NOT NULL,
    code                     varchar(30)    NOT NULL,
    description              text,
    price_per_student_cents  int            NOT NULL,
    max_students             int,
    max_teachers             int,
    max_storage_mb           int            NOT NULL,
    features                 jsonb          NOT NULL DEFAULT '[]'::jsonb,
    sort_order               int            NOT NULL DEFAULT 0,
    is_active                boolean        NOT NULL DEFAULT true,
    created_at               timestamptz    NOT NULL,
    updated_at               timestamptz    NOT NULL,
    created_by               uuid,
    updated_by               uuid,
    deleted                  boolean        NOT NULL DEFAULT false,
    deleted_at               timestamptz,

    CONSTRAINT uk_platform_plans_code UNIQUE (code),
    CONSTRAINT uk_platform_plans_public_uuid UNIQUE (public_uuid),
    CONSTRAINT chk_platform_plans_price_positive
        CHECK (price_per_student_cents > 0),
    CONSTRAINT chk_platform_plans_max_storage_positive
        CHECK (max_storage_mb > 0),
    CONSTRAINT chk_platform_plans_features_is_array
        CHECK (jsonb_typeof(features) = 'array')
);

COMMENT ON TABLE edushift.platform_plans IS
    'Catalogo de planes SaaS modificable por SUPER_ADMIN (Sprint 15 / ADR-15.8). '
    'Cada plan define precio por estudiante activo y limites opcionales.';
COMMENT ON COLUMN edushift.platform_plans.code IS
    'Identificador textual unico (BASIC, PRO, ENTERPRISE, etc.). '
    'Es el codigo que el enum TenantPlan legacy mapeaba.';
COMMENT ON COLUMN edushift.platform_plans.price_per_student_cents IS
    'Precio mensual en centavos (1 centavo = $0.01 PEN). La factura B2B calcula '
    'subtotal = active_student_count x price_per_student_cents (con snapshot).';
COMMENT ON COLUMN edushift.platform_plans.max_students IS
    'Cap opcional de estudiantes activos. NULL = sin limite dentro del plan.';
COMMENT ON COLUMN edushift.platform_plans.features IS
    'Array JSON de features habilitados. Valores posibles: LMS, REPORTS, '
    'AI_GENERATE, PAYMENTS, API_ACCESS, DEDICATED_SUPPORT.';

INSERT INTO edushift.platform_plans (
    id, public_uuid, name, code, description, price_per_student_cents,
    max_students, max_teachers, max_storage_mb, features, sort_order, is_active,
    created_at, updated_at, deleted
) VALUES
    (gen_random_uuid(), gen_random_uuid(),
     'Plan Basico', 'BASIC',
     'Plan de entrada para instituciones pequenas.',
     100, 100, 5, 500,
     '["LMS", "REPORTS"]'::jsonb, 1, true,
     now(), now(), false),
    (gen_random_uuid(), gen_random_uuid(),
     'Plan Pro', 'PRO',
     'Plan recomendado para escuelas establecidas.',
     200, 500, 30, 2048,
     '["LMS", "REPORTS", "AI_GENERATE", "PAYMENTS"]'::jsonb, 2, true,
     now(), now(), false),
    (gen_random_uuid(), gen_random_uuid(),
     'Enterprise', 'ENTERPRISE',
     'Plan para instituciones grandes con soporte dedicado.',
     300, NULL, 50, 10240,
     '["LMS", "REPORTS", "AI_GENERATE", "PAYMENTS", "API_ACCESS", "DEDICATED_SUPPORT"]'::jsonb, 3, true,
     now(), now(), false)
ON CONFLICT (code) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_platform_plans_active_sort
    ON edushift.platform_plans (is_active, sort_order)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_platform_plans_code_lookup
    ON edushift.platform_plans (code)
    WHERE deleted = false;

CREATE TRIGGER set_updated_at_platform_plans
    BEFORE UPDATE ON edushift.platform_plans
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();
