-- Reconcile databases where earlier local variants of V144/V145 were applied.
-- Preserve those migration records and bring the physical schema forward here.

ALTER TABLE edushift.ai_source_documents
    ADD COLUMN IF NOT EXISTS index_error_code varchar(60);

ALTER TABLE edushift.ai_token_reservations
    ADD COLUMN IF NOT EXISTS created_by uuid,
    ADD COLUMN IF NOT EXISTS updated_by uuid,
    ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'edushift.ai_token_reservations'::regclass
          AND conname = 'chk_ai_token_reservation_deleted_at'
    ) THEN
        ALTER TABLE edushift.ai_token_reservations
            ADD CONSTRAINT chk_ai_token_reservation_deleted_at
                CHECK ((deleted = false AND deleted_at IS NULL)
                    OR (deleted = true AND deleted_at IS NOT NULL));
    END IF;
END
$$;

-- tenant_id throughout EduShift stores tenants.id (the internal tenant key),
-- not tenants.public_uuid. The table is empty in the affected local database;
-- this FK keeps future reservations aligned with the runtime tenant context.
ALTER TABLE edushift.ai_token_reservations
    DROP CONSTRAINT IF EXISTS ai_token_reservations_tenant_id_fkey;

-- Earlier local V145 referenced tenants.public_uuid. Convert existing rows
-- after dropping that FK; Flyway runs this atomically with the migration.
UPDATE edushift.ai_token_reservations reservation
   SET tenant_id = tenant.id
  FROM edushift.tenants tenant
 WHERE reservation.tenant_id = tenant.public_uuid;

ALTER TABLE edushift.ai_token_reservations
    ADD CONSTRAINT fk_ai_token_reservations_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(id) ON DELETE RESTRICT;
