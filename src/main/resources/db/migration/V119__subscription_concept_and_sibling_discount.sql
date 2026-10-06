-- =============================================================================
-- V119 — Subscriptions: link to payment concept + sibling discount staff status
-- =============================================================================
-- concept_id: soft FK to payment_concepts.id (nullable for legacy rows).
-- sibling_discount_status: NONE | SUGGESTED | APPLIED | REJECTED
-- Staff decides APPLIED/REJECTED; cron only discounts when APPLIED.
-- =============================================================================

ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS concept_id uuid,
    ADD COLUMN IF NOT EXISTS sibling_discount_status varchar(15) NOT NULL DEFAULT 'NONE';

CREATE INDEX IF NOT EXISTS idx_subscriptions_concept
    ON edushift.subscriptions (tenant_id, concept_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_subscriptions_sibling_status
    ON edushift.subscriptions (tenant_id, sibling_discount_status)
    WHERE deleted_at IS NULL AND sibling_discount_status <> 'NONE';

COMMENT ON COLUMN edushift.subscriptions.concept_id IS
    'Optional FK to payment_concepts.id; required for newly created plans';
COMMENT ON COLUMN edushift.subscriptions.sibling_discount_status IS
    'NONE | SUGGESTED | APPLIED | REJECTED — staff-reviewed sibling discount';
