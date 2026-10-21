-- AiQuotaService resolves the tenant key through AiTenantKeyResolver, which
-- returns tenants.public_uuid. Align the reservation FK and existing rows
-- with tenant_ai_settings/tenant_ai_usage without changing applied versions.
ALTER TABLE edushift.ai_token_reservations
    DROP CONSTRAINT IF EXISTS fk_ai_token_reservations_tenant;

UPDATE edushift.ai_token_reservations reservation
   SET tenant_id = tenant.public_uuid
  FROM edushift.tenants tenant
 WHERE reservation.tenant_id = tenant.id;

ALTER TABLE edushift.ai_token_reservations
    ADD CONSTRAINT fk_ai_token_reservations_tenant
        FOREIGN KEY (tenant_id) REFERENCES edushift.tenants(public_uuid) ON DELETE RESTRICT;
