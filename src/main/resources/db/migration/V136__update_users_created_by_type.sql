-- Flyway Migration V136: Update users created_by/updated_by to UUID
-- Compliance with project standards (UUID instead of VARCHAR for audit columns)
-- Updates the users table audit columns to match entity expectations.

ALTER TABLE edushift.users
    ALTER COLUMN created_by TYPE UUID USING created_by::uuid,
    ALTER COLUMN updated_by TYPE UUID USING updated_by::uuid;