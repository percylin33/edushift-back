package com.edushift.modules.academic.levelgrade.service.impl;

import com.edushift.modules.academic.levelgrade.config.AcademicDefaults;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicSeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AcademicSeedService}.
 *
 * <p>The {@code @Transactional(propagation = REQUIRED)} setup means we
 * piggyback on the caller's transaction (the {@code TenantContext.runAs}
 * inside {@code TenantServiceImpl.register} already opens one). If the
 * caller has no active transaction this annotation will start a new one;
 * either way, the writes are atomic with the rest of the registration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicSeedServiceImpl implements AcademicSeedService {

	private final AcademicLevelRepository levelRepository;
	private final GradeRepository gradeRepository;

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public boolean seedDefaults(UUID tenantId) {
		long existing = levelRepository.countBy();
		if (existing > 0) {
			log.debug("[academic.seed] tenantId={} -- skipping (already has {} levels)",
					tenantId, existing);
			return false;
		}

		int totalLevels = 0;
		int totalGrades = 0;
		for (AcademicDefaults.DefaultLevel def : AcademicDefaults.LEVELS) {
			AcademicLevel level = new AcademicLevel();
			level.setCode(def.code());
			level.setName(def.name());
			level.setOrdinal(def.ordinal());
			AcademicLevel persistedLevel = levelRepository.save(level);
			totalLevels++;

			for (AcademicDefaults.DefaultGrade gradeDef : def.grades()) {
				Grade grade = new Grade();
				grade.setLevel(persistedLevel);
				grade.setName(gradeDef.name());
				grade.setOrdinal(gradeDef.ordinal());
				gradeRepository.save(grade);
				totalGrades++;
			}
		}

		log.info("[academic.seed] tenantId={} -- seeded {} levels and {} grades",
				tenantId, totalLevels, totalGrades);
		return true;
	}
}
