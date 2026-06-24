-- =============================================================================
-- V47 - Add audit columns + public_uuid to tables created in V42-V46 (DEBT-FK-BUGS-1 sweep).
--
-- Contexto:
--   Las migraciones V42 (Sprint 8 / BE-8.3 AI chat), V43 (Sprint 9 / BE-9.1
--   notifications), V44 (Sprint 9 / BE-9.2 announcements), V45 (Sprint 9 /
--   BE-9.3 reports) y V46 (Sprint 11 / BE-11.7 payments) crearon sus tablas
--   con un shape incompleto respecto a las entidades JPA (que heredan de
--   BaseEntity + AuditableEntity). Las columnas que faltan:
--
--       created_at, updated_at, deleted, deleted_at
--        FALTA: created_by, updated_by
--
--   Esto es valido para entidades que extienden BaseEntity (TenantAiUsage,
--   etc), pero FALLA en arranque con spring.jpa.hibernate.ddl-auto=validate
--   para cualquier entidad que extienda AuditableEntity o TenantAwareEntity
--   (AiChatMessage, AiChatSession, Announcement, AnnouncementRecipient,
--   Notification, etc.).
--
--   El error tipico es:
--     Schema-validation: missing column [created_by] in table [edushift.X]
--     Schema-validation: missing column [updated_by] in table [edushift.X]
--     Schema-validation: missing column [deleted]     in table [edushift.X]
--     Schema-validation: missing column [updated_at]  in table [edushift.X]
--
--   Esta migracion cierra el capitulo para TODAS las tablas afectadas
--   (V42-V45). V46 (payments) ya tiene created_by/updated_by en su
--   diseno original y se omite.
--
-- Decision (forward-only):
--   No se modifican V42-V45 (regla de Flyway del proyecto: V<n> es inmutable
--   una vez aplicada). Se anaden las columnas con ALTER TABLE + defaults
--   seguros, y se crean los triggers set_updated_at_* que tambien faltaban
--   como defensa-en-profundidad para UPDATEs hechos por SQL directo / DBA.
--
-- Idempotencia: usa IF NOT EXISTS / pg_constraint checks para que sea
-- seguro re-correr en entornos donde alguien haya arreglado parcialmente
-- a mano antes de esta migracion.
-- =============================================================================

-- =============================================================================
-- 1. AI chat tables (V42)
-- =============================================================================

-- ai_chat_sessions (extends TenantAwareEntity)
ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.ai_chat_sessions
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_ai_chat_sessions ON edushift.ai_chat_sessions;
CREATE TRIGGER set_updated_at_ai_chat_sessions
    BEFORE UPDATE ON edushift.ai_chat_sessions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ai_chat_sessions_deleted_at'
    ) THEN
        ALTER TABLE edushift.ai_chat_sessions
            ADD CONSTRAINT chk_ai_chat_sessions_deleted_at
            CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL));
    END IF;
END $$;

-- ai_chat_messages (extends TenantAwareEntity)
ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_ai_chat_messages ON edushift.ai_chat_messages;
CREATE TRIGGER set_updated_at_ai_chat_messages
    BEFORE UPDATE ON edushift.ai_chat_messages
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ai_chat_messages_deleted_at'
    ) THEN
        ALTER TABLE edushift.ai_chat_messages
            ADD CONSTRAINT chk_ai_chat_messages_deleted_at
            CHECK ((deleted = false AND deleted_at IS NULL) OR (deleted = true AND deleted_at IS NOT NULL));
    END IF;
END $$;

-- =============================================================================
-- 2. Notifications tables (V43)
-- =============================================================================

-- notification_templates (extends AuditableEntity)
ALTER TABLE edushift.notification_templates
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.notification_templates
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.notification_templates
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.notification_templates
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.notification_templates
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_notification_templates ON edushift.notification_templates;
CREATE TRIGGER set_updated_at_notification_templates
    BEFORE UPDATE ON edushift.notification_templates
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- notifications (extends AuditableEntity)
ALTER TABLE edushift.notifications
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.notifications
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.notifications
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.notifications
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.notifications
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_notifications ON edushift.notifications;
CREATE TRIGGER set_updated_at_notifications
    BEFORE UPDATE ON edushift.notifications
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- notification_preferences (extends AuditableEntity)
ALTER TABLE edushift.notification_preferences
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.notification_preferences
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.notification_preferences
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.notification_preferences
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.notification_preferences
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_notification_preferences ON edushift.notification_preferences;
CREATE TRIGGER set_updated_at_notification_preferences
    BEFORE UPDATE ON edushift.notification_preferences
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- email_outbox (extends AuditableEntity)
-- NOTE: V43 forgot both the audit columns AND the public_uuid column that
-- the entity declares on line 57 (EmailOutbox#publicUuid). Hibernate
-- schema-validate fails with: "missing column [public_uuid] in table
-- [edushift.email_outbox]". We backfill both.
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS public_uuid uuid;
UPDATE edushift.email_outbox
    SET public_uuid = gen_random_uuid()
WHERE public_uuid IS NULL;
ALTER TABLE edushift.email_outbox
    ALTER COLUMN public_uuid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_email_outbox_public_uuid
    ON edushift.email_outbox (public_uuid);
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.email_outbox
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_email_outbox ON edushift.email_outbox;
CREATE TRIGGER set_updated_at_email_outbox
    BEFORE UPDATE ON edushift.email_outbox
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 3. Announcements tables (V44)
-- =============================================================================

-- announcements (extends AuditableEntity)
ALTER TABLE edushift.announcements
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.announcements
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.announcements
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.announcements
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.announcements
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_announcements ON edushift.announcements;
CREATE TRIGGER set_updated_at_announcements
    BEFORE UPDATE ON edushift.announcements
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- announcement_recipients (extends AuditableEntity)
ALTER TABLE edushift.announcement_recipients
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.announcement_recipients
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.announcement_recipients
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.announcement_recipients
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.announcement_recipients
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_announcement_recipients ON edushift.announcement_recipients;
CREATE TRIGGER set_updated_at_announcement_recipients
    BEFORE UPDATE ON edushift.announcement_recipients
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 4. Reports tables (V45)
-- =============================================================================

-- report_jobs (extends AuditableEntity)
ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.report_jobs
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

DROP TRIGGER IF EXISTS set_updated_at_report_jobs ON edushift.report_jobs;
CREATE TRIGGER set_updated_at_report_jobs
    BEFORE UPDATE ON edushift.report_jobs
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 5. Payments tables (V46)
-- =============================================================================
--
-- V46 created 4 tables (subscriptions, invoices, invoice_items, payments)
-- all extending TenantAwareEntity. None has the full audit set
-- ({@code created_at, updated_at, deleted, deleted_at, created_by, updated_by})
-- that BaseEntity + AuditableEntity declare. We backfill here.
--
-- V46 also forgot {@code public_uuid} on {@code invoice_items} and the
-- updated_at trigger on all four. invoice_items doesn't have public_uuid
-- in the entity (it's an internal line item, never exposed via API), so
-- we don't add it there.

-- subscriptions (extends TenantAwareEntity)
ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
DROP TRIGGER IF EXISTS set_updated_at_subscriptions ON edushift.subscriptions;
CREATE TRIGGER set_updated_at_subscriptions
    BEFORE UPDATE ON edushift.subscriptions
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- invoices (extends TenantAwareEntity)
ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.invoices
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
DROP TRIGGER IF EXISTS set_updated_at_invoices ON edushift.invoices;
CREATE TRIGGER set_updated_at_invoices
    BEFORE UPDATE ON edushift.invoices
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- invoice_items (extends TenantAwareEntity; no public_uuid per design)
ALTER TABLE edushift.invoice_items
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.invoice_items
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.invoice_items
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.invoice_items
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.invoice_items
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
DROP TRIGGER IF EXISTS set_updated_at_invoice_items ON edushift.invoice_items;
CREATE TRIGGER set_updated_at_invoice_items
    BEFORE UPDATE ON edushift.invoice_items
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- payments (extends TenantAwareEntity)
ALTER TABLE edushift.payments
    ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE edushift.payments
    ADD COLUMN IF NOT EXISTS updated_by uuid;
ALTER TABLE edushift.payments
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE edushift.payments
    ADD COLUMN IF NOT EXISTS deleted    boolean     NOT NULL DEFAULT false;
ALTER TABLE edushift.payments
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;
DROP TRIGGER IF EXISTS set_updated_at_payments ON edushift.payments;
CREATE TRIGGER set_updated_at_payments
    BEFORE UPDATE ON edushift.payments
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

-- =============================================================================
-- 6. Comment block
-- =============================================================================
COMMENT ON TABLE edushift.ai_chat_sessions IS
    'AI chat sessions (BE-8.3). Multi-tenant via tenant_id + Hibernate @TenantId. TTL 7d via ChatSessionSweeper.';
COMMENT ON TABLE edushift.ai_chat_messages IS
    'AI chat messages (BE-8.3). Belongs to ai_chat_sessions. role=user/assistant/system.';
