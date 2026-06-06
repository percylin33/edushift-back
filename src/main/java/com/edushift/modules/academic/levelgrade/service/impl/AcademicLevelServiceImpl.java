package com.edushift.modules.academic.levelgrade.service.impl;

import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.mapper.AcademicLevelMapper;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.AcademicLevelService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AcademicLevelService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicLevelServiceImpl implements AcademicLevelService {

	private final AcademicLevelRepository levelRepository;
	private final GradeRepository gradeRepository;
	private final CourseLevelRepository courseLevelRepository;
	private final AcademicLevelMapper mapper;

	@Override
	@Transactional(readOnly = true)
	public List<AcademicLevelResponse> listLevels() {
		List<AcademicLevel> levels = levelRepository.findAllByOrderByOrdinalAsc();
		return levels.stream()
				.map(level -> {
					List<Grade> grades = gradeRepository.findAllByLevelOrderByOrdinalAsc(level);
					return mapper.toResponse(level, grades);
				})
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AcademicLevelResponse getLevel(UUID publicUuid) {
		AcademicLevel level = loadLevel(publicUuid);
		List<Grade> grades = gradeRepository.findAllByLevelOrderByOrdinalAsc(level);
		return mapper.toResponse(level, grades);
	}

	@Override
	@Transactional
	public AcademicLevelResponse createLevel(CreateAcademicLevelRequest request) {
		ensureCodeAvailable(request.code(), null);

		AcademicLevel level = mapper.fromCreate(request);
		try {
			AcademicLevel saved = levelRepository.saveAndFlush(level);
			log.info("[academic.level] created -- publicUuid={} code={}",
					saved.getPublicUuid(), saved.getCode());
			return mapper.toResponse(saved, List.of());
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("LEVEL_CODE_TAKEN",
					"Another academic level in this tenant already uses the code '"
							+ request.code() + "'", ex);
		}
	}

	@Override
	@Transactional
	public AcademicLevelResponse updateLevel(UUID publicUuid, UpdateAcademicLevelRequest request) {
		AcademicLevel level = loadLevel(publicUuid);

		if (request == null || request.isEmpty()) {
			List<Grade> grades = gradeRepository.findAllByLevelOrderByOrdinalAsc(level);
			return mapper.toResponse(level, grades);
		}

		if (request.code() != null
				&& !request.code().trim().equalsIgnoreCase(level.getCode())) {
			ensureCodeAvailable(request.code(), level.getId());
		}

		mapper.applyUpdate(request, level);

		try {
			AcademicLevel saved = levelRepository.saveAndFlush(level);
			List<Grade> grades = gradeRepository.findAllByLevelOrderByOrdinalAsc(saved);
			log.info("[academic.level] updated -- publicUuid={} code={}",
					saved.getPublicUuid(), saved.getCode());
			return mapper.toResponse(saved, grades);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("LEVEL_CODE_TAKEN",
					"Another academic level in this tenant already uses the code '"
							+ request.code() + "'", ex);
		}
	}

	@Override
	@Transactional
	public void deleteLevel(UUID publicUuid) {
		AcademicLevel level = loadLevel(publicUuid);

		long gradeCount = gradeRepository.countByLevel(level);
		if (gradeCount > 0) {
			throw new ConflictException("LEVEL_HAS_GRADES",
					"Cannot delete level '" + level.getCode()
							+ "': remove its " + gradeCount + " grade(s) first");
		}

		long courseCount = courseLevelRepository.countByLevel(level);
		if (courseCount > 0) {
			throw new ConflictException("LEVEL_IN_USE_BY_COURSES",
					"Cannot delete level '" + level.getCode()
							+ "': it is referenced by " + courseCount + " course(s).");
		}

		levelRepository.delete(level);
		log.info("[academic.level] deleted -- publicUuid={} code={}",
				level.getPublicUuid(), level.getCode());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private AcademicLevel loadLevel(UUID publicUuid) {
		return levelRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicLevel", publicUuid));
	}

	private void ensureCodeAvailable(String code, UUID excludeInternalId) {
		if (code == null) return;
		String normalised = code.trim();
		levelRepository.findByCodeIgnoreCase(normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("LEVEL_CODE_TAKEN",
							"Another academic level in this tenant already uses the code '"
									+ normalised + "'");
				});
	}
}
