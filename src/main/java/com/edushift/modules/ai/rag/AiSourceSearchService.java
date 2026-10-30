package com.edushift.modules.ai.rag;

import com.edushift.modules.ai.service.AiEntitlementService;
import com.edushift.shared.multitenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search published, current, audience-authorized tenant and EduShift source fragments. */
@Service
@RequiredArgsConstructor
public class AiSourceSearchService {

    private final JdbcTemplate jdbc;
    private final AiEntitlementService entitlements;

    /** Recheck historical citations before returning them to the current role. */
    @Transactional(readOnly = true)
    public Set<UUID> visibleSourceIds(Set<UUID> sourceIds, AiSourceDocument.Audience audience) {
        if (sourceIds.isEmpty()) return Set.of();
        var plan = entitlements.current();
        if (!plan.features().contains(AiEntitlementService.RAG_CORE)) return Set.of();
        UUID tenantId = TenantContext.currentRequired();
        return Set.copyOf(new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT d.public_uuid
                  FROM edushift.ai_source_documents d
                  JOIN edushift.lms_file_objects f
                    ON f.public_uuid = d.file_public_uuid AND f.tenant_id = d.tenant_id
                 WHERE d.tenant_id = :tenantId AND d.public_uuid IN (:ids)
                   AND d.deleted = false AND f.deleted = false AND f.status = 'READY'
                   AND d.status = 'PUBLISHED'
                   AND (:schoolDocs OR d.kind = 'OFFICIAL')
                   AND jsonb_exists(d.audiences, :audience)
                   AND (d.effective_at IS NULL OR d.effective_at <=
                        (now() AT TIME ZONE 'UTC')::date)
                   AND (d.review_after IS NULL OR d.review_after >=
                        (now() AT TIME ZONE 'UTC')::date)
                UNION
                SELECT d.public_uuid
                  FROM edushift.ai_platform_source_documents d
                 WHERE d.public_uuid IN (:ids) AND d.deleted = false
                   AND d.status = 'PUBLISHED'
                   AND jsonb_exists(d.audiences, :audience)
                   AND (d.effective_at IS NULL OR d.effective_at <=
                        (now() AT TIME ZONE 'UTC')::date)
                   AND d.review_after >= (now() AT TIME ZONE 'UTC')::date
                """, Map.of("tenantId", tenantId, "ids", sourceIds,
                        "schoolDocs", plan.features().contains(AiEntitlementService.RAG_SCHOOL_DOCS),
                        "audience", audience.name()),
                (rs, row) -> rs.getObject("public_uuid", UUID.class)));
    }

    @Transactional(readOnly = true)
    public List<Citation> search(String question, AiSourceDocument.Audience audience, int topK) {
        var plan = entitlements.require(AiEntitlementService.RAG_CORE);
        UUID tenantId = TenantContext.currentRequired();
        if (question == null || question.isBlank()) return List.of();
        int limit = Math.max(1, Math.min(topK, 12));
        return jdbc.query("""
                WITH eligible_chunks AS (
                    SELECT d.public_uuid, d.file_public_uuid, f.original_name,
                           d.title, d.kind, d.source_url, d.source_version,
                           d.effective_at, c.page_number, c.ordinal, c.content,
                           'SCHOOL'::text AS source_scope,
                           ts_rank_cd(c.search_vector,
                               websearch_to_tsquery('spanish'::regconfig, ?)) AS rank
                      FROM edushift.ai_source_chunks c
                      JOIN edushift.ai_source_documents d
                        ON d.id = c.document_id AND d.tenant_id = c.tenant_id
                      JOIN edushift.lms_file_objects f
                        ON f.public_uuid = d.file_public_uuid AND f.tenant_id = d.tenant_id
                     WHERE c.tenant_id = ? AND d.tenant_id = ?
                       AND c.deleted = false AND d.deleted = false
                       AND f.deleted = false AND f.status = 'READY'
                       AND d.status = 'PUBLISHED'
                       AND (? OR d.kind = 'OFFICIAL')
                       AND jsonb_exists(d.audiences, ?)
                       AND (d.effective_at IS NULL OR d.effective_at <=
                            (now() AT TIME ZONE 'UTC')::date)
                       AND (d.review_after IS NULL OR d.review_after >=
                            (now() AT TIME ZONE 'UTC')::date)
                       AND c.search_vector @@ websearch_to_tsquery('spanish'::regconfig, ?)
                    UNION ALL
                    SELECT d.public_uuid, NULL::uuid AS file_public_uuid,
                           d.original_name, d.title, 'OFFICIAL'::varchar AS kind,
                           d.source_url, d.source_version, d.effective_at,
                           c.page_number, c.ordinal, c.content,
                           'EDUSHIFT'::text AS source_scope,
                           ts_rank_cd(c.search_vector,
                               websearch_to_tsquery('spanish'::regconfig, ?)) AS rank
                      FROM edushift.ai_platform_source_chunks c
                      JOIN edushift.ai_platform_source_documents d
                        ON d.id = c.document_id
                     WHERE c.deleted = false AND d.deleted = false
                       AND d.status = 'PUBLISHED'
                       AND jsonb_exists(d.audiences, ?)
                       AND (d.effective_at IS NULL OR d.effective_at <=
                            (now() AT TIME ZONE 'UTC')::date)
                       AND d.review_after >= (now() AT TIME ZONE 'UTC')::date
                       AND c.search_vector @@ websearch_to_tsquery('spanish'::regconfig, ?)
                )
                SELECT public_uuid, file_public_uuid, original_name, title, kind,
                       source_url, source_version, effective_at, page_number,
                       ordinal, content, source_scope, rank
                  FROM eligible_chunks
                ORDER BY rank DESC,
                         CASE source_scope WHEN 'SCHOOL' THEN 0 ELSE 1 END,
                         title, ordinal
                LIMIT ?
                """, AiSourceSearchService::mapCitation,
                question, tenantId, tenantId,
                plan.features().contains(AiEntitlementService.RAG_SCHOOL_DOCS),
                audience.name(), question,
                question, audience.name(), question, limit);
    }

    private static Citation mapCitation(ResultSet rs, int row) throws SQLException {
        return new Citation(
                rs.getObject("public_uuid", UUID.class),
                rs.getObject("file_public_uuid", UUID.class),
                rs.getString("original_name"),
                rs.getString("source_scope"),
                rs.getString("title"),
                rs.getString("kind"),
                rs.getString("source_url"),
                rs.getString("source_version"),
                rs.getObject("effective_at", java.time.LocalDate.class),
                rs.getObject("page_number", Integer.class),
                rs.getInt("ordinal"),
                rs.getString("content"),
                rs.getDouble("rank"));
    }

    public record Citation(UUID sourcePublicUuid, UUID filePublicUuid,
                           String fileName, String sourceScope,
                           String title, String kind,
                           String sourceUrl, String sourceVersion,
                           java.time.LocalDate effectiveAt, Integer pageNumber,
                           int ordinal, String text, double score) {}
}
