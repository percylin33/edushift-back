-- Flyway Migration V135: Update privacy_consents created_by/updated_by to UUID
-- Compliance with project standards (UUID instead of VARCHAR for audit columns)
-- This migration updates the data type of created_by and updated_by from VARCHAR(128) to UUID
-- in the privacy_consents table, matching the AuditableEntity conventions.

-- Step 1: Alter the columns from VARCHAR to UUID type
ALTER TABLE edushift.privacy_consents
    ALTER COLUMN created_by TYPE UUID USING created_by::uuid,
    ALTER COLUMN updated_by TYPE UUID USING updated_by::uuid;