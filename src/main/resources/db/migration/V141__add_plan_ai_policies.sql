-- AI entitlements and pilot limits for the three Sprint 21a assistants.
-- platform_plans.features remains the source of truth for enabled functions.
CREATE TABLE edushift.plan_ai_policies (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL,
    daily_request_limit integer NOT NULL,
    monthly_token_limit bigint NOT NULL,
    tutor_messages_per_student_day integer NOT NULL,
    institutional_document_limit integer NOT NULL,
    institutional_text_bytes_limit bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    CONSTRAINT fk_plan_ai_policies_plan FOREIGN KEY (plan_id)
        REFERENCES edushift.platform_plans(id) ON DELETE RESTRICT,
    CONSTRAINT uq_plan_ai_policies_plan UNIQUE (plan_id),
    CONSTRAINT chk_plan_ai_policies_limits CHECK (
        daily_request_limit >= 0 AND monthly_token_limit >= 0
        AND tutor_messages_per_student_day >= 0
        AND institutional_document_limit >= 0
        AND institutional_text_bytes_limit >= 0),
    CONSTRAINT chk_plan_ai_policies_deleted_at CHECK (
        (deleted = false AND deleted_at IS NULL)
        OR (deleted = true AND deleted_at IS NOT NULL))
);

CREATE INDEX idx_plan_ai_policies_active_plan
    ON edushift.plan_ai_policies(plan_id) WHERE deleted = false;

CREATE TRIGGER set_updated_at_plan_ai_policies
    BEFORE UPDATE ON edushift.plan_ai_policies
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE edushift.plan_ai_policies IS
    'Numeric AI limits per commercial plan; functions remain in platform_plans.features.';
COMMENT ON COLUMN edushift.plan_ai_policies.institutional_text_bytes_limit IS
    'Maximum extracted text bytes indexed from tenant-owned institutional documents.';

INSERT INTO edushift.plan_ai_policies
    (id, plan_id, daily_request_limit, monthly_token_limit,
     tutor_messages_per_student_day, institutional_document_limit,
     institutional_text_bytes_limit)
SELECT gen_random_uuid(), p.id,
       CASE p.code WHEN 'PRO' THEN 200 WHEN 'ENTERPRISE' THEN 1000 ELSE 0 END,
       CASE p.code WHEN 'PRO' THEN 1000000 WHEN 'ENTERPRISE' THEN 5000000 ELSE 0 END,
       CASE p.code WHEN 'PRO' THEN 20 WHEN 'ENTERPRISE' THEN 50 ELSE 0 END,
       CASE p.code WHEN 'PRO' THEN 25 WHEN 'ENTERPRISE' THEN 250 ELSE 0 END,
       CASE p.code WHEN 'PRO' THEN 100000000 WHEN 'ENTERPRISE' THEN 1000000000 ELSE 0 END
FROM edushift.platform_plans p
WHERE p.code IN ('BASIC', 'PRO', 'ENTERPRISE')
  AND NOT EXISTS (SELECT 1 FROM edushift.plan_ai_policies a WHERE a.plan_id = p.id);

UPDATE edushift.platform_plans
SET features = features || '["AI_TUTOR","AI_TEACHER","AI_FAMILY","AI_RAG_CORE","AI_RAG_SCHOOL_DOCS"]'::jsonb
WHERE code IN ('PRO', 'ENTERPRISE');

UPDATE edushift.platform_plans
SET features = features || '["AI_RAG_BULK_IMPORT","AI_PROMPT_CUSTOMIZATION"]'::jsonb
WHERE code = 'ENTERPRISE';
