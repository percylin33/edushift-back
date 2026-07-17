-- =============================================================================
-- V66__create_session_templates.sql
-- Sprint 18 / BE-18.3 — Session Templates
-- =============================================================================
-- Session templates define reusable pedagogical structures (MINEDU archetypes
-- + tenant custom templates). System templates (is_system=true) are seeded
-- by the application and shared across all tenants; tenant templates are
-- editable by TENANT_ADMIN.
--
-- The schema_jsonb column stores a flexible map of suggested activities,
-- phase definitions, evaluation strategies, etc.
-- =============================================================================

CREATE TABLE IF NOT EXISTS edushift.session_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    public_uuid     UUID NOT NULL,
    template_key    VARCHAR(100) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(500),
    schema_jsonb    JSONB DEFAULT '{}'::jsonb,
    is_system       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted         BOOLEAN NOT NULL DEFAULT false
);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS uk_session_templates_public_uuid
    ON edushift.session_templates (public_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS uk_session_templates_tenant_key
    ON edushift.session_templates (tenant_id, template_key)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_session_templates_tenant_system
    ON edushift.session_templates (tenant_id, is_system);

-- Seed MINEDU archetype templates (tenant_id = '00000000-0000-0000-0000-000000000000'
-- means system-wide; each tenant-enumeration seed will clone from these).
INSERT INTO edushift.session_templates (id, tenant_id, public_uuid, template_key, name, description, schema_jsonb, is_system)
VALUES
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000000',
        gen_random_uuid(),
        'minedu-standard',
        'Estándar MINEDU',
        'Estructura pedagógica estándar del MINEDU: inicio, desarrollo y cierre.',
        '{
            "phases": [
                {"name": "Inicio", "duration_min": 15, "description": "Motivación, saberes previos, conflicto cognitivo"},
                {"name": "Desarrollo", "duration_min": 45, "description": "Construcción del aprendizaje, aplicación"},
                {"name": "Cierre", "duration_min": 15, "description": "Metacognición, evaluación, retroalimentación"}
            ],
            "activity_types": ["dialogo", "trabajo_grupal", "trabajo_individual", "exposicion", "lectura", "escritura"],
            "evaluation_methods": ["observacion", "lista_cotejo", "rubrica", "preguntas_orales"],
            "materials_suggested": ["pizarra", "cuaderno", "texto_MINEDU", "material_concreto", "audiovisual"]
        }'::jsonb,
        true
    ),
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000000',
        gen_random_uuid(),
        'minedu-abc',
        'Método ABC MINEDU',
        'Enfoque comunicativo textual para el área de Comunicación: antes, durante y después de la lectura.',
        '{
            "phases": [
                {"name": "Antes de la lectura", "duration_min": 10, "description": "Activación de saberes, predicción, propósito lector"},
                {"name": "Durante la lectura", "duration_min": 30, "description": "Lectura compartida, subrayado, organización de ideas"},
                {"name": "Después de la lectura", "duration_min": 20, "description": "Organizadores gráficos, producción textual, metacognición"}
            ],
            "activity_types": ["lectura_compartida", "subrayado", "organizador_grafico", "produccion_textual", "dialogo"],
            "evaluation_methods": ["rubrica", "lista_cotejo", "produccion_textual"],
            "materials_suggested": ["texto_MINEDU", "fichas_lectura", "cuaderno", "diccionario", "papelote"]
        }'::jsonb,
        true
    ),
    (
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000000',
        gen_random_uuid(),
        'minedu-math',
        'Método Matemático MINEDU',
        'Estructura para sesiones de matemática: vivencial, concreto, gráfico, simbólico y aplicación.',
        '{
            "phases": [
                {"name": "Vivencial", "duration_min": 10, "description": "Situación problemática real del contexto"},
                {"name": "Concreto", "duration_min": 20, "description": "Manipulación de material concreto"},
                {"name": "Gráfico", "duration_min": 15, "description": "Representación pictórica y esquemática"},
                {"name": "Simbólico", "duration_min": 15, "description": "Notación matemática formal"},
                {"name": "Aplicación", "duration_min": 15, "description": "Resolución de ejercicios y problemas"}
            ],
            "activity_types": ["resolucion_problemas", "trabajo_grupal", "taller", "juego_matematico", "demostracion"],
            "evaluation_methods": ["rubrica", "lista_cotejo", "prueba_escrita", "observacion"],
            "materials_suggested": ["material_base10", "regletas", "geoplano", "tangrama", "texto_MINEDU", "cuaderno"]
        }'::jsonb,
        true
    );
