package com.edushift.modules.me.service;

import com.edushift.modules.me.dto.MeGradeResponse;
import com.edushift.modules.me.dto.MeSectionDetailResponse;
import com.edushift.modules.me.dto.MeSectionResponse;
import java.util.List;
import java.util.UUID;

/**
 * STUDENT-facing academic service surface (DEBT-STUDENT-PRIVACY / Fase 2).
 *
 * <p>Returns the academic surface visible to the authenticated student:
 * the sections they are currently enrolled in, the per-section content
 * (materials / tasks / quizzes) and their grades. Every entry point
 * derives the student identity from the JWT via {@code CurrentUserProvider}
 * and rejects the call when no {@code Student} row is linked.</p>
 *
 * <p>Authorization model:
 * <ul>
 *   <li>{@link #listMySections()} — only the caller's own sections.</li>
 *   <li>{@link #getMySection(UUID)} — only if the caller is ACTIVE-enrolled
 *       on that section, otherwise {@code NotFoundException} (anti-enumeration).</li>
 *   <li>{@link #listMyGrades()} — only the caller's own grades.</li>
 * </ul>
 *
 * <p>Implementations MUST enforce tenant isolation through the
 * {@code @TenantId} discriminator on every entity they read.</p>
 */
public interface MeAcademicService {

    /**
     * Returns the sections the authenticated student is currently enrolled
     * in (one per active enrollment). Each row carries the lightweight
     * counts (materials / tasks / quizzes) the FE needs to render the
     * "Mis cursos" landing without a second round-trip.
     */
    List<MeSectionResponse> listMySections();

    /**
     * Returns the full section detail (header + materials + tasks + quizzes)
     * for the given public UUID. Throws {@code NotFoundException} when
     * the caller has no ACTIVE enrollment on the section.
     */
    MeSectionDetailResponse getMySection(UUID sectionPublicUuid);

    /**
     * Returns every grade recorded for the authenticated student, ordered
     * by the parent evaluation's scheduled date desc. Empty list when the
     * student has no grades yet.
     */
    List<MeGradeResponse> listMyGrades();
}
