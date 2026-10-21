-- Flyway Migration V134: Update claims_book_entries created_by/updated_by to UUID
-- Compliance with project standards (UUID instead of VARCHAR for audit columns)
-- This migration updates the data type of created_by and updated_by from VARCHAR(128) to UUID
-- to match the AuditableEntity/ BaseEntity conventions used across the project.

-- Step 1: Alter the columns from VARCHAR to UUID type
-- Note: This assumes the values currently in the columns are valid UUIDs or can be cast.
-- If there are invalid values, this migration will fail and you may need to clean data first.

ALTER TABLE edushift.claims_book_entries
    ALTER COLUMN created_by TYPE UUID USING created_by::uuid,
    ALTER COLUMN updated_by TYPE UUID USING updated_by::uuid;

-- Step 2: Add NOT NULL constraint if needed (uncomment if your AuditableEntity enforces it)
-- ALTER TABLE edushift.claims_book_entries
--     ALTER COLUMN created_by SET NOT NULL,
--     ALTER COLUMN updated_by SET NOT NULL;