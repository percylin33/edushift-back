-- =============================================================================
-- V88__create_user_device_tokens.sql
--
-- Cierre-C / B8 -- FCM (Firebase Cloud Messaging) push notifications.
--
-- Tabla `user_device_tokens`: mapea userPublicUuid -> FCM registration
-- token. El FcmSender (B8 cierre) consulta los tokens activos del
-- destinatario y los entrega via `sendEachForMulticast`.
--
-- Decisiones:
--   * ADR-Cierre-C.8 -- UNIQUE (token) GLOBAL: un mismo FCM token
--     pertenece a un solo user. Si el usuario reinstala la app y rota
--     el token, hacemos upsert sobre (token) y migramos el userId.
--   * ADR-Cierre-C.8 -- soft-delete via `active` (no `deleted_at`)
--     para que los records expirados sigan siendo visibles a la
--     auditoria. El `last_seen_at` se actualiza en cada heartbeat del FE.
--   * ADR-Cierre-C.8 -- `platform` enum constrained (ANDROID | IOS | WEB)
--     para que el sender pueda enrutar mensajes por plataforma si lo
--     necesitamos en el futuro (ej. sound custom en iOS).
--   * ADR-Cierre-C.8 -- multi-tenant via columna `tenant_id` +
--     Hibernate `@TenantId` discriminator.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.user_device_tokens (
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL,

    -- Audit (BaseEntity contract — required by Hibernate schema-validation
    -- because UserDeviceToken extends TenantAwareEntity which extends
    -- BaseEntity with @SQLDelete soft-delete).
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    deleted                 boolean       NOT NULL DEFAULT false,
    deleted_at              timestamptz,

    user_public_uuid        uuid          NOT NULL,
    token                   varchar(512)  NOT NULL,
    platform                varchar(10)   NOT NULL,
    -- `active` is independent from `deleted`:
    --   * active=false   -> the user opted out / token rotated (FCM API rejected).
    --   * deleted=true   -> the row was hard-removed by the app (audit-trail).
    -- Both transitions keep the row visible to the audit UI.
    active                  boolean       NOT NULL DEFAULT true,
    last_seen_at            timestamptz   NOT NULL DEFAULT NOW(),
    unregistered_at         timestamptz,

    CONSTRAINT uk_user_device_tokens_token UNIQUE (token),

    CONSTRAINT chk_user_device_tokens_platform CHECK (
        platform IN ('ANDROID', 'IOS', 'WEB')
    ),

    CONSTRAINT chk_user_device_tokens_lifecycle CHECK (
        (active = true  AND unregistered_at IS NULL)
     OR (active = false AND unregistered_at IS NOT NULL)
    ),

    CONSTRAINT chk_user_device_tokens_deleted_at_consistent CHECK (
        (deleted = false AND deleted_at IS NULL)
     OR (deleted = true  AND deleted_at IS NOT NULL)
    ),

    CONSTRAINT fk_user_device_tokens_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES edushift.tenants (id)
        ON DELETE RESTRICT
);

-- Hot path: FCM dispatch (B8 cierre). Devuelve todos los tokens
-- activos del destinatario ordenados por last_seen_at DESC (el mas
-- reciente suele ser el dispositivo activo del usuario).
CREATE INDEX idx_user_device_tokens_user_active
    ON edushift.user_device_tokens (tenant_id, user_public_uuid, last_seen_at DESC)
    WHERE active = true;

-- Hot path: limpieza periodica de tokens inactivos.
CREATE INDEX idx_user_device_tokens_active_seen
    ON edushift.user_device_tokens (active, last_seen_at)
    WHERE active = true;

CREATE TRIGGER set_updated_at_user_device_tokens
    BEFORE UPDATE ON edushift.user_device_tokens
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.user_device_tokens                IS 'FCM registration tokens per user (Sprint cierre-C / B8).';
COMMENT ON COLUMN edushift.user_device_tokens.token          IS 'FCM registration token. Globally UNIQUE - a token belongs to exactly one (tenant, user) pair at any moment.';
COMMENT ON COLUMN edushift.user_device_tokens.platform       IS 'ANDROID | IOS | WEB.';
COMMENT ON COLUMN edushift.user_device_tokens.last_seen_at   IS 'Updated by the FE on each app open (heartbeat). Used to GC stale tokens older than 60 days.';
COMMENT ON COLUMN edushift.user_device_tokens.unregistered_at IS 'Set when active flips to false (user logout, token rotation, uninstall). Preserved for audit.';