-- =====================================================================
-- V32 - Sprint 7a / BE-7a.1 - LMS materials module
--
-- Materiales educativos que un teacher sube a una seccion. Un material
-- es un binario (PDF, slide, etc.) o un link externo (YouTube, Vimeo).
-- El binario, si existe, vive en la tabla lms_file_objects (BE-7a.0);
-- esta fila guarda el puntero (file_public_uuid) y la metadata.
--
-- Tabla:
--   lms_materials - Recurso educativo por (seccion, owner).
--
-- Decisiones (ver docs/modules/materials.md y sprint-07a-lms-foundations):
--   * D-MAT-01 - Una fila por upload. Soft-delete + new row = "v2".
--   * D-MAT-02 - file_public_uuid es un soft FK a lms_file_objects.public_uuid
--                (referencia simbolica, no FK fisica: simplifica cascade).
--   * D-MAT-03 - kind es enum embebido (FILE | VIDEO_LINK).
--   * D-MAT-04 - kind=FILE requiere file_public_uuid; kind=VIDEO_LINK
--                requiere external_url. CHECK constraint enforces.
--   * D-MAT-06 - Section-enrollment check vive en el service, no en DB.
--
-- Multi-tenant: tenant_id + Hibernate @TenantId discriminator.
-- Soft-delete: flag `deleted` + `deleted_at` con CHECK de coherencia.
-- =====================================================================

CREATE TABLE edushift.lms_materials (
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL,
    public_uuid                 uuid          NOT NULL,
    created_at                  timestamptz   NOT NULL,
    updated_at                  timestamptz   NOT NULL,
    created_by                  uuid,
    updated_by                  uuid,
    deleted                     boolean       NOT NULL DEFAULT false,
    deleted_at                  timestamptz,

    section_id                  uuid          NOT NULL,
    file_public_uuid            uuid,                                     -- nullable: VIDEO_LINK no tiene file
    title                       varchar(200)  NOT NULL,
    description                 varchar(2000),
    kind                        varchar(16)   NOT NULL,
    external_url                varchar(2048),                            -- nullable: FILE no tiene url
    owner_user_id               uuid          NOT NULL,

    CONSTRAINT uk_lms_materials_public_uuid
        UNIQUE (public_uuid),

    CONSTRAINT chk_lms_materials_kind
        CHECK (kind IN ('FILE', 'VIDEO_LINK')),

    -- (D-MAT-04) kind=FILE requiere file_public_uuid;
    -- kind=VIDEO_LINK requiere external_url.
    CONSTRAINT chk_lms_materials_kind_payload_consistent
        CHECK (
            (kind = 'FILE'       AND file_public_uuid IS NOT NULL AND external_url IS NULL)
         OR (kind = 'VIDEO_LINK' AND file_public_uuid IS NULL     AND external_url IS NOT NULL)
        ),

    CONSTRAINT chk_lms_materials_deleted_at_consistent
        CHECK (
            (deleted = false AND deleted_at IS NULL)
         OR (deleted = true  AND deleted_at IS NOT NULL)
        ),

    -- FK logica a sections (RESTRICT: no se borra una seccion con materials).
    CONSTRAINT fk_lms_materials_section
        FOREIGN KEY (section_id)
        REFERENCES edushift.sections (id)
        ON DELETE RESTRICT,

    -- owner_user_id referencia users.public_uuid (mismo patron que V29).
    CONSTRAINT fk_lms_materials_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES edushift.users (public_uuid)
        ON DELETE RESTRICT
);

-- Hot path: listado de materiales de una seccion ordenado por fecha desc.
CREATE INDEX idx_lms_materials_tenant_section_created
    ON edushift.lms_materials (tenant_id, section_id, created_at DESC)
    WHERE NOT deleted;

-- Hot path: "mis materiales subidos" (dashboard del teacher).
CREATE INDEX idx_lms_materials_tenant_owner_created
    ON edushift.lms_materials (tenant_id, owner_user_id, created_at DESC)
    WHERE NOT deleted;

-- (D-MAT-04) file_public_uuid es un soft FK a lms_file_objects; lo indexamos
-- para la cascade del files module (releaseReference scan).
CREATE INDEX idx_lms_materials_tenant_file
    ON edushift.lms_materials (tenant_id, file_public_uuid)
    WHERE NOT deleted AND file_public_uuid IS NOT NULL;

CREATE TRIGGER set_updated_at_lms_materials
    BEFORE UPDATE ON edushift.lms_materials
    FOR EACH ROW EXECUTE FUNCTION edushift.set_updated_at();

COMMENT ON TABLE  edushift.lms_materials                  IS 'LMS materials: per-section resource uploaded by TEACHER or TENANT_ADMIN. Sprint 7a / BE-7a.1.';
COMMENT ON COLUMN edushift.lms_materials.section_id       IS 'FK -> sections(id). One material belongs to exactly one section.';
COMMENT ON COLUMN edushift.lms_materials.file_public_uuid IS 'Soft FK -> lms_file_objects(public_uuid). Set when kind=FILE; null for VIDEO_LINK. Cascade handled by files module reference_count.';
COMMENT ON COLUMN edushift.lms_materials.kind              IS 'FILE (binary uploaded to files module) | VIDEO_LINK (external URL, no bytes).';
COMMENT ON COLUMN edushift.lms_materials.external_url     IS 'Required when kind=VIDEO_LINK. Validation (host whitelist) lives in service (DEBT-7A-5).';
COMMENT ON COLUMN edushift.lms_materials.owner_user_id    IS 'public_uuid of the user that created the material. FK -> users.public_uuid.';
