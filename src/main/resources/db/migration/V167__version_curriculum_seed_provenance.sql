-- Preserve the national curriculum source and distinguish it from school edits.
ALTER TABLE edushift.competencies
    ADD COLUMN source_catalog_version varchar(60),
    ADD COLUMN source_catalog_code varchar(80),
    ADD COLUMN source_level_code varchar(20),
    ADD COLUMN source_cycle_codes varchar(30),
    ADD COLUMN source_content_status varchar(32);

ALTER TABLE edushift.capacities
    ADD COLUMN source_catalog_version varchar(60),
    ADD COLUMN source_catalog_code varchar(80),
    ADD COLUMN source_content_status varchar(32);

ALTER TABLE edushift.competencies
    ADD CONSTRAINT chk_competencies_source_content_status CHECK (
        source_content_status IS NULL OR source_content_status IN (
            'MINEDU_SUMMARY', 'MINEDU_TRANSCRIPTION',
            'INSTITUTIONAL_ADAPTATION', 'LEGACY_UNVERIFIED'
        )
    ),
    ADD CONSTRAINT chk_competencies_source_reference CHECK (
        (source_catalog_version IS NULL AND source_catalog_code IS NULL
            AND source_content_status IS NULL)
        OR (source_catalog_version IS NOT NULL AND source_catalog_code IS NOT NULL
            AND source_content_status IS NOT NULL)
    );

ALTER TABLE edushift.capacities
    ADD CONSTRAINT chk_capacities_source_content_status CHECK (
        source_content_status IS NULL OR source_content_status IN (
            'MINEDU_SUMMARY', 'MINEDU_TRANSCRIPTION',
            'INSTITUTIONAL_ADAPTATION', 'LEGACY_UNVERIFIED'
        )
    ),
    ADD CONSTRAINT chk_capacities_source_reference CHECK (
        (source_catalog_version IS NULL AND source_catalog_code IS NULL
            AND source_content_status IS NULL)
        OR (source_catalog_version IS NOT NULL AND source_catalog_code IS NOT NULL
            AND source_content_status IS NOT NULL)
    );

-- Existing MINEDU-origin rows predate source versioning. Keep them visible,
-- but label their provenance as unverified instead of assigning a false version.
UPDATE edushift.competencies
SET source_catalog_version = 'LEGACY_UNVERIFIED',
    source_catalog_code = code,
    source_content_status = 'LEGACY_UNVERIFIED'
WHERE origin = 'MINEDU'
  AND deleted = false;

UPDATE edushift.capacities c
SET source_catalog_version = 'LEGACY_UNVERIFIED',
    source_catalog_code = c.code,
    source_content_status = 'LEGACY_UNVERIFIED'
FROM edushift.competencies competency
WHERE c.competency_id = competency.id
  AND competency.origin = 'MINEDU'
  AND c.deleted = false;

COMMENT ON COLUMN edushift.competencies.source_catalog_version IS
    'Versioned curriculum source key; null for fully institutional entries.';
COMMENT ON COLUMN edushift.competencies.source_content_status IS
    'Distinguishes curated source summary, transcription, school adaptation, or unverified legacy data.';
COMMENT ON COLUMN edushift.competencies.source_level_code IS
    'Education level for the source entry, independent of tenant-specific level labels.';
COMMENT ON COLUMN edushift.competencies.source_cycle_codes IS
    'Comma-separated CNEB cycle scope for the source entry.';
COMMENT ON COLUMN edushift.capacities.source_catalog_version IS
    'Versioned curriculum source key; null for fully institutional entries.';
