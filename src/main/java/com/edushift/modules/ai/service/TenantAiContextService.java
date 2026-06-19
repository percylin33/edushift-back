package com.edushift.modules.ai.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant context provider for AI prompts (Sprint 8 / BE-8.3, ADR-8.4).
 *
 * <p>Builds a {@link TenantContextSnapshot} from the current tenant's
 * data: name, top 20 active courses, MINEDU competencies, and active
 * academic year. The snapshot is then assembled by
 * {@code ChatService.buildSystemPrompt} into the system prompt sent
 * to the LLM.
 *
 * <h3>Decisiones</h3>
 * <ul>
 *   <li><b>CTX-01</b> — Top 20 cursos por nombre (orden alfabetico).
 *       No se mandan IDs, solo labels.</li>
 *   <li><b>CTX-02</b> — Competencias MINEDU: lee de la tabla
 *       {@code competencies} y se queda con las primeras 20.</li>
 *   <li><b>CTX-03</b> — Ano academico activo: el marcado como
 *       {@code status='ACTIVE'} en {@code academic_years}.</li>
 *   <li><b>CTX-04</b> — Si la query falla (p.ej. schema incompleto en
 *       dev), devuelve un snapshot vacio en vez de romper el chat.
 *       Esto sigue el principio "graceful degradation" del
 *       ai-rules.mdc.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantAiContextService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public TenantContextSnapshot snapshot(UUID tenantId) {
        String tenantName = safeString(() ->
                (String) em.createNativeQuery("SELECT name FROM edushift.tenants WHERE id = :tid")
                        .setParameter("tid", tenantId)
                        .getSingleResult(),
                "Tu institucion");

        String activeYearName = safeString(() ->
                (String) em.createNativeQuery(
                                "SELECT name FROM edushift.academic_years WHERE tenant_id = :tid AND status = 'ACTIVE' LIMIT 1")
                        .setParameter("tid", tenantId)
                        .getSingleResult(),
                "Sin ano academico activo");

        List<String> courses = safeList(() -> em.createNativeQuery(
                        "SELECT name FROM edushift.courses WHERE tenant_id = :tid AND is_active = true AND deleted = false ORDER BY name LIMIT 20")
                .setParameter("tid", tenantId)
                .getResultList());

        List<String> competencies = safeList(() -> em.createNativeQuery(
                        "SELECT name FROM edushift.competencies WHERE tenant_id = :tid AND deleted = false ORDER BY name LIMIT 20")
                .setParameter("tid", tenantId)
                .getResultList());

        return new TenantContextSnapshot(tenantName, activeYearName, courses, competencies);
    }

    private static String safeString(java.util.function.Supplier<?> supplier, String fallback) {
        try { Object o = supplier.get(); return o == null ? fallback : o.toString(); }
        catch (Exception e) { return fallback; }
    }

    private static List<String> safeList(java.util.function.Supplier<List<String>> supplier) {
        try { return supplier.get(); }
        catch (Exception e) { return List.of(); }
    }

    /**
     * Immutable snapshot of the tenant context that the AI sees.
     */
    public record TenantContextSnapshot(
            String tenantName,
            String activeYearName,
            List<String> courses,
            List<String> competencies
    ) {}
}
