-- =============================================================================
-- V44 - Sprint 9 / BE-9.4 - Announcements: rich text + audience targeting.
--
-- 1 tabla nueva + 1 join table:
--
--   1. announcements               - Anuncio creado por TENANT_ADMIN.
--                                     Lleva title + body HTML (sanitizado) +
--                                     audience_type + audience_ids (jsonb).
--                                     Soft-delete + audit cols.
--   2. announcement_recipients     - Resolucion del audience: 1 fila por
--                                     destinatario concreto. Se genera al
--                                     "publicar" el anuncio (no al crearlo).
--                                     Permite "mis anuncios" en el FE con
--                                     una query trivial.
--
-- Decisiones:
--   * ANN-MT-01 - tenant_id UUID NOT NULL en las 2 tablas.
--   * ANN-MT-02 - announcement_recipients.user_id FK a users(id) ON DELETE
--                 CASCADE. Si el user se va del tenant, su fila de
--                 recipient se va con el.
--   * ANN-MT-03 - audience_type: SCHOOL | GRADE | SECTION | COURSE |
--                 ROLE | USER. Las primeras 4 son "masivas" (resuelven a
--                 un set de users); las últimas 2 son directas.
--   * ANN-AUDIT-01 - body_html sanitizado en write por NotificationTemplateEngine
--                    (mismo Safelist jsoup, ADR-9.2).
--   * ANN-PERF-01 - idx (tenant_id, user_id, read_at) en recipients
--                   para "mis anuncios no leidos" del FE.
--   * ANN-PERF-02 - idx (tenant_id, status, published_at) en announcements
--                   para "anuncios publicados recientes".
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. announcements
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.announcements (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    public_uuid     uuid         NOT NULL,
    author_user_id  uuid         NOT NULL,
    title           varchar(200) NOT NULL,
    body_html       text         NOT NULL,
    audience_type   varchar(20)  NOT NULL,
    audience_ids    jsonb        NOT NULL DEFAULT '[]'::jsonb,
    status          varchar(15)  NOT NULL DEFAULT 'DRAFT',
    publish_at      timestamptz,
    published_at    timestamptz,
    pinned          boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT fk_announcements_author
        FOREIGN KEY (author_user_id) REFERENCES edushift.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_announcements_audience_type
        CHECK (audience_type IN ('SCHOOL', 'GRADE', 'SECTION', 'COURSE', 'ROLE', 'USER')),
    CONSTRAINT chk_announcements_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_announcements_public_uuid
    ON edushift.announcements (public_uuid);

CREATE INDEX idx_announcements_tenant_status_published
    ON edushift.announcements (tenant_id, status, published_at DESC)
    WHERE deleted = false;

CREATE INDEX idx_announcements_tenant_author
    ON edushift.announcements (tenant_id, author_user_id, created_at DESC)
    WHERE deleted = false;

COMMENT ON TABLE  edushift.announcements
    IS 'Tenant-wide announcements (BE-9.4). Multi-tenant via tenant_id + Hibernate @TenantId.';
COMMENT ON COLUMN edushift.announcements.audience_type
    IS 'SCHOOL=all tenant users | GRADE/SECTION/COURSE=list of ids in audience_ids | ROLE/USER=direct.';
COMMENT ON COLUMN edushift.announcements.audience_ids
    IS 'JSONB list of UUIDs (empty for SCHOOL). Interpretation depends on audience_type.';

-- -----------------------------------------------------------------------------
-- 2. announcement_recipients
-- -----------------------------------------------------------------------------
CREATE TABLE edushift.announcement_recipients (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    announcement_id uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    delivered_at    timestamptz,
    read_at         timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted         boolean      NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT fk_announcement_recipients_announcement
        FOREIGN KEY (announcement_id) REFERENCES edushift.announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_recipients_user
        FOREIGN KEY (user_id) REFERENCES edushift.users(id) ON DELETE CASCADE,
    CONSTRAINT uk_announcement_recipients_ann_user
        UNIQUE (announcement_id, user_id)
);

-- "Mis anuncios" + "no leidos" para el FE.
CREATE INDEX idx_announcement_recipients_tenant_user
    ON edushift.announcement_recipients (tenant_id, user_id, delivered_at DESC)
    WHERE deleted = false;

CREATE INDEX idx_announcement_recipients_tenant_user_unread
    ON edushift.announcement_recipients (tenant_id, user_id)
    WHERE deleted = false AND read_at IS NULL;

COMMENT ON TABLE  edushift.announcement_recipients
    IS 'Resolved audience per announcement (BE-9.4). Inserted when the announcement is published.';

-- =============================================================================
-- FIN V44
-- =============================================================================
