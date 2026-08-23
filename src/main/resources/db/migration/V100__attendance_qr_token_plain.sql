-- =============================================================================
-- V100 - Persist the printable QR payload so reprints do not rotate.
--
-- student_attendance_qr only stored SHA-256(token). POST /me/qr/reveal
-- and GET /students/{uuid}/attendance-qr therefore minted a new 12-char
-- token on every click, invalidating the sheet in the student's notebook.
-- token_plain is the OCR-friendly short id already printed in the QR;
-- token_hash remains the check-in lookup key.
-- =============================================================================

ALTER TABLE edushift.student_attendance_qr
    ADD COLUMN IF NOT EXISTS token_plain varchar(12);

COMMENT ON COLUMN edushift.student_attendance_qr.token_plain IS
    'Raw 12-char QR payload for stable reprints. Null on rows issued before V100; first reveal after migrate re-issues once.';
