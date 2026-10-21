-- =============================================================================
-- V157 — Preserve the specific purpose and notice shown for a consent record.
-- Historical rows remain marked as legacy because their exact notice cannot be
-- reconstructed from the old policy version alone.
-- =============================================================================

ALTER TABLE edushift.privacy_consents
    ADD COLUMN purpose_code varchar(80),
    ADD COLUMN notice_snapshot text;

UPDATE edushift.privacy_consents
SET purpose_code = 'LEGACY_UNSCOPED',
    notice_snapshot = 'Consentimiento legado: el texto exacto mostrado no fue conservado.'
WHERE purpose_code IS NULL OR notice_snapshot IS NULL;

ALTER TABLE edushift.privacy_consents
    ALTER COLUMN purpose_code SET NOT NULL,
    ALTER COLUMN notice_snapshot SET NOT NULL;

ALTER TABLE edushift.privacy_consents
    ADD CONSTRAINT chk_privacy_consents_purpose_code
        CHECK (length(btrim(purpose_code)) BETWEEN 1 AND 80),
    ADD CONSTRAINT chk_privacy_consents_notice_snapshot
        CHECK (length(btrim(notice_snapshot)) BETWEEN 1 AND 12000);

COMMENT ON COLUMN edushift.privacy_consents.purpose_code IS
    'School-configured purpose identifier; LEGACY_UNSCOPED marks records created before purpose capture.';
COMMENT ON COLUMN edushift.privacy_consents.notice_snapshot IS
    'Exact notice presented when the guardian recorded this specific consent decision.';
