-- =============================================================================
-- V98__attendance_justification_attachment.sql
-- Optional medical/supporting file on an attendance justification.
-- Stored as the public UUID of an lms_file_objects row (upload via POST /files,
-- then JSON justify). FK is ON DELETE SET NULL so file GC cannot block records.
-- =============================================================================

ALTER TABLE edushift.attendance_records
    ADD COLUMN justification_attachment_public_uuid uuid;

ALTER TABLE edushift.attendance_records
    ADD CONSTRAINT fk_attendance_records_justification_file
        FOREIGN KEY (justification_attachment_public_uuid)
        REFERENCES edushift.lms_file_objects (public_uuid)
        ON DELETE SET NULL;

COMMENT ON COLUMN edushift.attendance_records.justification_attachment_public_uuid IS
    'Optional supporting file (image or PDF) uploaded before POST .../justify.';
