-- =============================================================================
-- V110 — Grade category column layout (OPEN / SERIES / SINGLE) + slot metadata
-- =============================================================================

ALTER TABLE edushift.grade_categories
    ADD COLUMN IF NOT EXISTS column_mode varchar(16) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS slot_count integer,
    ADD COLUMN IF NOT EXISTS slot_label_prefix varchar(16),
    ADD COLUMN IF NOT EXISTS slot_labels jsonb;

ALTER TABLE edushift.grade_categories
    DROP CONSTRAINT IF EXISTS chk_grade_categories_column_mode;

ALTER TABLE edushift.grade_categories
    ADD CONSTRAINT chk_grade_categories_column_mode CHECK (
        column_mode IN ('OPEN', 'SERIES', 'SINGLE')
    );

ALTER TABLE edushift.grade_categories
    DROP CONSTRAINT IF EXISTS chk_grade_categories_slot_count;

ALTER TABLE edushift.grade_categories
    ADD CONSTRAINT chk_grade_categories_slot_count CHECK (
        slot_count IS NULL OR (slot_count >= 0 AND slot_count <= 40)
    );

COMMENT ON COLUMN edushift.grade_categories.column_mode IS
    'OPEN = teacher creates columns; SERIES = S1..Sn slots; SINGLE = one fixed cell';
COMMENT ON COLUMN edushift.grade_categories.slot_labels IS
    'Optional JSON array of exact slot names; null => prefix+index or category name';
