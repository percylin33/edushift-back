package com.edushift.modules.academic.levelgrade.service.impl;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeReorderRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.mapper.GradeMapper;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.levelgrade.service.GradeService;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link GradeService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

	private final AcademicLevelRepository levelRepository;
	private final GradeRepository gradeRepository;
	private final SectionRepository sectionRepository;
	private final GradeMapper mapper;

	@Override
	@Transactional(readOnly = true)
	public List<GradeResponse> listGrades(UUID levelPublicUuid) {
		AcademicLevel level = loadLevel(levelPublicUuid);
		return gradeRepository.findAllByLevelOrderByOrdinalAsc(level).stream()
				.map(mapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public GradeResponse createGrade(UUID levelPublicUuid, CreateGradeRequest request) {
		AcademicLevel level = loadLevel(levelPublicUuid);
		ensureOrdinalAvailable(level, request.ordinal(), null);

		Grade grade = mapper.fromCreate(request, level);
		try {
			Grade saved = gradeRepository.saveAndFlush(grade);
			log.info("[academic.grade] created -- publicUuid={} levelCode={} name={}",
					saved.getPublicUuid(), level.getCode(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("GRADE_ORDINAL_TAKEN",
					"Another grade in level '" + level.getCode()
							+ "' already uses ordinal " + request.ordinal(), ex);
		}
	}

	@Override
	@Transactional
	public GradeResponse updateGrade(UUID levelPublicUuid, UUID gradePublicUuid,
			UpdateGradeRequest request) {
		AcademicLevel level = loadLevel(levelPublicUuid);
		Grade grade = loadGradeInsideLevel(level, gradePublicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(grade);
		}

		if (request.ordinal() != null && !request.ordinal().equals(grade.getOrdinal())) {
			ensureOrdinalAvailable(level, request.ordinal(), grade.getId());
		}

		mapper.applyUpdate(request, grade);

		try {
			Grade saved = gradeRepository.saveAndFlush(grade);
			log.info("[academic.grade] updated -- publicUuid={} name={}",
					saved.getPublicUuid(), saved.getName());
			return mapper.toResponse(saved);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("GRADE_ORDINAL_TAKEN",
					"Another grade in level '" + level.getCode()
							+ "' already uses ordinal " + request.ordinal(), ex);
		}
	}

	@Override
	@Transactional
	public void deleteGrade(UUID levelPublicUuid, UUID gradePublicUuid) {
		AcademicLevel level = loadLevel(levelPublicUuid);
		Grade grade = loadGradeInsideLevel(level, gradePublicUuid);

		long sectionCount = sectionRepository.countByGrade(grade);
		if (sectionCount > 0) {
			throw new ConflictException("GRADE_HAS_SECTIONS",
					"Cannot delete grade '" + grade.getName()
							+ "': remove its " + sectionCount + " section(s) first");
		}

		gradeRepository.delete(grade);
		log.info("[academic.grade] deleted -- publicUuid={} levelCode={} name={}",
				grade.getPublicUuid(), level.getCode(), grade.getName());
	}

	@Override
	@Transactional
	public List<GradeResponse> reorderGrades(UUID levelPublicUuid, GradeReorderRequest request) {
		AcademicLevel level = loadLevel(levelPublicUuid);

		validateReorderPayload(request);

		List<Grade> levelGrades = gradeRepository.findAllByLevelOrderByOrdinalAsc(level);
		Map<UUID, Grade> byPublic = levelGrades.stream()
				.collect(Collectors.toMap(Grade::getPublicUuid, g -> g));

		// Bind each request item to a grade we own; fail-fast on cross-level
		// or unknown grades.
		for (GradeReorderRequest.Item item : request.items()) {
			Grade target = byPublic.get(item.publicUuid());
			if (target == null) {
				throw new ConflictException("GRADE_REORDER_INVALID",
						"Grade " + item.publicUuid()
								+ " does not belong to level '" + level.getCode() + "'");
			}
		}

		// Two-pass strategy: park each affected grade at a high-numbered
		// temporary ordinal that is guaranteed to be free (above the
		// current max in the level + 1000 buffer). Then in the second
		// pass assign the final ordinals. This avoids tripping the
		// unique partial index mid-update without DEFERRED constraints
		// and keeps every value compatible with the
		// {@code chk_grades_ordinal_positive (ordinal >= 1)} CHECK.
		int existingMax = levelGrades.stream()
				.mapToInt(Grade::getOrdinal)
				.max().orElse(0);
		int parkBase = existingMax + 1000;
		int parkIdx = 0;
		for (GradeReorderRequest.Item item : request.items()) {
			Grade target = byPublic.get(item.publicUuid());
			target.setOrdinal(parkBase + parkIdx++);
			gradeRepository.save(target);
		}
		gradeRepository.flush();

		try {
			for (GradeReorderRequest.Item item : request.items()) {
				Grade target = byPublic.get(item.publicUuid());
				target.setOrdinal(item.ordinal());
				gradeRepository.save(target);
			}
			gradeRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("GRADE_ORDINAL_TAKEN",
					"Reorder produced a duplicate ordinal in level '"
							+ level.getCode() + "'", ex);
		}

		log.info("[academic.grade] reordered -- levelCode={} count={}",
				level.getCode(), request.items().size());

		return gradeRepository.findAllByLevelOrderByOrdinalAsc(level).stream()
				.map(mapper::toResponse)
				.toList();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private AcademicLevel loadLevel(UUID publicUuid) {
		return levelRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicLevel", publicUuid));
	}

	private Grade loadGradeInsideLevel(AcademicLevel level, UUID gradePublicUuid) {
		Grade grade = gradeRepository.findByPublicUuid(gradePublicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Grade", gradePublicUuid));
		if (!grade.getLevel().getId().equals(level.getId())) {
			// Anti-enumeration: a grade that exists but belongs to a sibling
			// level is reported as "not found" instead of leaking the fact
			// that the UUID exists. Same policy as cross-tenant access.
			throw new ResourceNotFoundException("Grade", gradePublicUuid);
		}
		return grade;
	}

	private void ensureOrdinalAvailable(AcademicLevel level, Integer ordinal,
			UUID excludeInternalId) {
		if (ordinal == null) return;
		gradeRepository.findByLevelAndOrdinal(level, ordinal)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException("GRADE_ORDINAL_TAKEN",
							"Another grade in level '" + level.getCode()
									+ "' already uses ordinal " + ordinal);
				});
	}

	private void validateReorderPayload(GradeReorderRequest request) {
		Set<Integer> seenOrdinals = new HashSet<>();
		Set<UUID> seenIds = new HashSet<>();
		for (GradeReorderRequest.Item item : request.items()) {
			if (!seenOrdinals.add(item.ordinal())) {
				throw new ConflictException("GRADE_REORDER_INVALID",
						"Duplicate ordinal " + item.ordinal() + " in reorder payload");
			}
			if (!seenIds.add(item.publicUuid())) {
				throw new ConflictException("GRADE_REORDER_INVALID",
						"Duplicate grade " + item.publicUuid() + " in reorder payload");
			}
		}
	}
}
