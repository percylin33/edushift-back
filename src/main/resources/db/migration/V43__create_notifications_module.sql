-- =============================================================================
-- V43 - Sprint 9 / BE-9.1 - Notifications module: templates + in-app + email
-- outbox + preferences (multi-tenant via tenant_id + Hibernate @TenantId).
--
-- 4 tablas nuevas (todas en schema edushift, mismo patron que notifications
-- module de Phase 9, ADR-9.1 outbox pattern + ADR-9.2 editable templates):
--
--   1. notification_templates - Templates versionados por tenant.
--                              8 seeds: WELCOME_TENANT, STUDENT_ABSENT,
--                              GRADE_PUBLISHED, AI_FEEDBACK_READY,
--                              TASK_RETURNED, QUIZ_PUBLISHED, PAYMENT_DUE,
--                              ANNOUNCEMENT. Solo TENANT_ADMIN puede editar.
--                              Subject + body HTML sanitizable (ADR-9.2).
--   2. notifications          - Historial in-app. Una fila por delivery
--                              (recipient + template + payload + status).
--                              Multi-tenant: tenant_id + Hibernate @TenantId.
--                              Anti-enumeration: FK a users(id) ON DELETE
--                              CASCADE (si el user se borra, sus notifs se
--                              van con el, no quedan huerfanas en la BD).
--   3. notification_preferences - Opt-in/opt-out por user × channel × category.
--                              Una fila por combinacion. Default = enabled.
--                              Si el user no tiene row, default = enabled.
--   4. email_outbox           - Outbox pattern (ADR-9.1). Una fila por email
--                              a enviar. Procesada por EmailOutboxProcessor
--                              (@Scheduled cada 30s). Backoff exponencial
--                              con max 5 retries. status: PENDING|SENT|FAILED.
--
-- Decisiones:
--   * NOTIF-MT-01 - tenant_id UUID NOT NULL en las 4 tablas. Mismo patron
--                   que ai_generations (Hibernate @TenantId lo aplica en
--                   SELECT, no necesitamos FK para aislamiento).
--   * NOTIF-MT-02 - notifications.recipient_user_id FK a users(id) ON DELETE
--                   CASCADE. No queremos historial huerfano si el user
--                   se va del colegio.
--   * NOTIF-MT-03 - notification_templates UNIQUE(tenant_id, template_key,
--                   locale) — no duplicar el mismo template dos veces.
--   * NOTIF-MT-04 - notification_preferences UNIQUE(user_id, channel, category)
--                   — un user no puede tener 2 rows para la misma
--                   combinacion.
--   * NOTIF-AUDIT-01 - notifications.payload JSONB: el event que disparo
--                      la notificacion (e.g. {"evaluationId": "...",
--                      "grade": 18, "courseName": "Historia"}). El
--                      template lo expande con interpolacion simple.
--   * NOTIF-OUTBOX-01 - email_outbox.notification_id FK a notifications(id)
--                       ON DELETE SET NULL (si la notif se borra, el
--                       email queda como huerfano con notification_id=NULL;
--                       el processor lo envia igual y luego lo purga).
--   * NOTIF-INDEX-01 - idx (tenant_id, recipient_user_id, created_at desc)
--                      en notifications para "mis notificaciones" y
--                      "unread count" en el bell.
--   * NOTIF-INDEX-02 - idx (tenant_id, status, next_retry_at) en
--                      email_outbox para el processor: "dame los PENDING
--                      cuyo next_retry_at <= now".
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. notification_templates
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.notification_templates (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    public_uuid     uuid         NOT NULL,
    template_key    varchar(60)  NOT NULL,
    locale          varchar(10)  NOT NULL DEFAULT 'es-PE',
    subject         varchar(200) NOT NULL,
    body_html       text         NOT NULL,
    version         integer      NOT NULL DEFAULT 1,
    is_system       boolean      NOT NULL DEFAULT false,
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_templates_locale
        CHECK (locale IN ('es-PE', 'en-US'))
);

CREATE UNIQUE INDEX uk_notification_templates_public_uuid
    ON edushift.notification_templates (public_uuid);

CREATE UNIQUE INDEX uk_notification_templates_tenant_key_locale
    ON edushift.notification_templates (tenant_id, template_key, locale)
    WHERE deleted = false;

CREATE INDEX idx_notification_templates_tenant_key
    ON edushift.notification_templates (tenant_id, template_key)
    WHERE deleted = false;

COMMENT ON TABLE  edushift.notification_templates
    IS 'Notification templates (BE-9.1). Multi-tenant + versionados. ADR-9.2: editables con sanitizacion.';
COMMENT ON COLUMN edushift.notification_templates.is_system
    IS 'true = template built-in (no se puede borrar, solo override). false = template custom del tenant.';

-- -----------------------------------------------------------------------------
-- 2. notifications (in-app history)
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.notifications (
    id                   uuid         PRIMARY KEY,
    tenant_id            uuid         NOT NULL,
    public_uuid          uuid         NOT NULL,
    recipient_user_id    uuid         NOT NULL,
    template_key         varchar(60)  NOT NULL,
    category             varchar(30)  NOT NULL,
    channel              varchar(10)  NOT NULL DEFAULT 'IN_APP',
    payload              jsonb        NOT NULL DEFAULT '{}'::jsonb,
    status               varchar(15)  NOT NULL DEFAULT 'PENDING',
    sent_at              timestamptz,
    read_at              timestamptz,
    error_code           varchar(50),
    error_message        varchar(500),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    deleted              boolean      NOT NULL DEFAULT false,
    deleted_at           timestamptz,
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES edushift.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH')),
    CONSTRAINT chk_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'SKIPPED', 'FAILED')),
    CONSTRAINT chk_notifications_category
        CHECK (category IN ('ABSENCE', 'GRADE', 'QUIZ', 'TASK', 'AI_FEEDBACK',
                            'ANNOUNCEMENT', 'PAYMENT', 'SYSTEM'))
);

CREATE UNIQUE INDEX uk_notifications_public_uuid
    ON edushift.notifications (public_uuid);

-- "Mis notificaciones" (FE-9.1) + "unread count" (badge del bell)
CREATE INDEX idx_notifications_tenant_recipient_created
    ON edushift.notifications (tenant_id, recipient_user_id, created_at DESC)
    WHERE deleted = false;

-- Badge unread (recipient + status= PENDING|SENT|DELIVERED, no READ)
CREATE INDEX idx_notifications_tenant_recipient_unread
    ON edushift.notifications (tenant_id, recipient_user_id, status)
    WHERE deleted = false AND status NOT IN ('READ', 'SKIPPED', 'FAILED');

COMMENT ON TABLE  edushift.notifications
    IS 'In-app notifications (BE-9.1). Multi-tenant via tenant_id + Hibernate @TenantId.';
COMMENT ON COLUMN edushift.notifications.payload
    IS 'Event payload JSONB. Template lo expande con interpolacion {{key}}.';

-- -----------------------------------------------------------------------------
-- 3. notification_preferences
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.notification_preferences (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    channel         varchar(10)  NOT NULL,
    category        varchar(30)  NOT NULL,
    enabled         boolean      NOT NULL DEFAULT true,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_notification_preferences_user_channel_category
        UNIQUE (user_id, channel, category)
);

CREATE INDEX idx_notification_preferences_tenant_user
    ON edushift.notification_preferences (tenant_id, user_id)
    WHERE deleted = false;

COMMENT ON TABLE  edushift.notification_preferences
    IS 'Per-user opt-in/opt-out (BE-9.1). Default enabled si no hay row.';

-- -----------------------------------------------------------------------------
-- 4. email_outbox
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.email_outbox (
    id                uuid         PRIMARY KEY,
    tenant_id         uuid         NOT NULL,
    notification_id   uuid,
    to_email          varchar(255) NOT NULL,
    subject           varchar(500) NOT NULL,
    body_html         text         NOT NULL,
    status            varchar(15)  NOT NULL DEFAULT 'PENDING',
    attempts          integer      NOT NULL DEFAULT 0,
    last_error        varchar(2000),
    next_retry_at     timestamptz  NOT NULL DEFAULT now(),
    sent_at           timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted           boolean      NOT NULL DEFAULT false,
    deleted_at        timestamptz,
    CONSTRAINT fk_email_outbox_notification
        FOREIGN KEY (notification_id) REFERENCES edushift.notifications(id) ON DELETE SET NULL,
    CONSTRAINT chk_email_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- Processor: PENDING cuyo next_retry_at <= now, oldest first, limit 50/batch
CREATE INDEX idx_email_outbox_pending_retry
    ON edushift.email_outbox (status, next_retry_at)
    WHERE deleted = false AND status = 'PENDING';

CREATE INDEX idx_email_outbox_tenant_status
    ON edushift.email_outbox (tenant_id, status)
    WHERE deleted = false;

COMMENT ON TABLE  edushift.email_outbox
    IS 'Outbox pattern (ADR-9.1). Procesada por EmailOutboxProcessor cada 30s.';
COMMENT ON COLUMN edushift.email_outbox.next_retry_at
    IS 'Backoff exponencial: now + 2^attempts minutes, max 5 retries (then FAILED).';

-- -----------------------------------------------------------------------------
-- 5. Seed 8 templates built-in (is_system=true, no borrables)
-- -----------------------------------------------------------------------------
-- Nota: el body_html usa placeholders {{key}} que el TemplateEngine expande
-- al renderizar. Los seeds se aplican al primer tenant creado (via
-- DevDataInitializer en BE-9.1 wiring) — no podemos insertarlos aca porque
-- no hay tenant_id todavia. Ver DevDataInitializer.seedNotificationTemplates().

-- =============================================================================
-- FIN V43
-- =============================================================================
