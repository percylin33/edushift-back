package com.edushift.modules.academic.competency.service;

import com.edushift.modules.academic.competency.dto.SeedCompetenciesResponse;
import java.util.UUID;

/**
 * On-demand seed of the MINEDU minimal catalog into a single course
 * (Sprint 5A / BE-5A.2).
 *
 * <p>Unlike
 * {@link com.edushift.modules.academic.levelgrade.service.AcademicSeedService}
 * (which runs during self-signup), this seed is invoked manually from
 * the FE via {@code POST /v1/academic/courses/{courseUuid}/competencies/seed-defaults}.
 * Reason: the seed bundle is keyed by {@code Course.code}; courses
 * don't exist yet at signup time, so there's nothing to seed.</p>
 *
 * <h3>Idempotency contract</h3>
 * <ul>
 *   <li>If the target course already has at least one competency, the
 *       call is a no-op and returns {@code seeded=false} with the
 *       counts at zero. This makes the endpoint safe to retry.</li>
 *   <li>If the course's {@code code} is not recognised by
 *       {@link com.edushift.modules.academic.competency.config.CompetencyDefaults},
 *       the call is also a no-op but returns
 *       {@code unsupportedCourseCode=true} so the FE can hint the admin
 *       (e.g. "Crea competencias manualmente para INGLES, no hay
 *       catálogo predeterminado").</li>
 * </ul>
 */
public interface CompetencySeedService {

	SeedCompetenciesResponse seedForCourse(UUID courseUuid);
}
