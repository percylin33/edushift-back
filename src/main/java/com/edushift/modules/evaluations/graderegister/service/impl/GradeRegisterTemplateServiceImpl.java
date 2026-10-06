package com.edushift.modules.evaluations.graderegister.service.impl;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.dto.AdoptGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.dto.CreateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeCategoryRequest;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import com.edushift.modules.evaluations.graderegister.mapper.GradeRegisterTemplateMapper;
import com.edushift.modules.evaluations.graderegister.repository.GradeCategoryRepository;
import com.edushift.modules.evaluations.graderegister.repository.GradeRegisterTemplateRepository;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterSeedService;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeRegisterTemplateServiceImpl implements GradeRegisterTemplateService {

	private final GradeRegisterTemplateRepository templateRepository;
	private final GradeCategoryRepository categoryRepository;
	private final AcademicYearRepository academicYearRepository;
	private final AcademicLevelRepository academicLevelRepository;
	private final CourseRepository courseRepository;
	private final GradeRegisterSeedService seedService;
	private final GradeRegisterTemplateMapper mapper;
	private final EvaluationRepository evaluationRepository;
	private final GradeRecordRepository gradeRecordRepository;

	@Override
	@Transactional
	public List<GradeRegisterTemplateListItem> listCatalog() {
		return seedService.materializeCatalog();
	}

	@Override
	@Transactional(readOnly = true)
	public List<GradeRegisterTemplateListItem> listMine(UUID academicYearPublicUuid) {
		AcademicYear year = requireYear(academicYearPublicUuid);
		return templateRepository.findTenantTemplatesForYear(year).stream()
				.map(t -> mapper.toListItem(
						t,
						categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(t).size()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public GradeRegisterTemplateResponse get(UUID publicUuid) {
		GradeRegisterTemplate t = requireTemplate(publicUuid);
		return mapper.toResponse(t, categoriesOf(t));
	}

	@Override
	@Transactional
	public GradeRegisterTemplateResponse adopt(
			UUID catalogPublicUuid,
			AdoptGradeRegisterTemplateRequest request
	) {
		GradeRegisterTemplate source = requireTemplate(catalogPublicUuid);
		if (!Boolean.TRUE.equals(source.getIsSystem())) {
			throw new BadRequestException(
					"GRADE_REGISTER_NOT_CATALOG",
					"Only system catalog templates can be adopted");
		}

		AcademicYear year = requireYear(UUID.fromString(request.academicYearPublicUuid()));
		GradeRegisterTemplateScope scope = request.scope();

		Course course = null;
		AcademicLevel level = null;
		if (scope == GradeRegisterTemplateScope.COURSE) {
			if (request.coursePublicUuid() == null || request.coursePublicUuid().isBlank()) {
				throw new BadRequestException(
						"GRADE_REGISTER_COURSE_REQUIRED",
						"coursePublicUuid is required when scope=COURSE");
			}
			course = courseRepository.findByPublicUuid(UUID.fromString(request.coursePublicUuid()))
					.orElseThrow(() -> new ResourceNotFoundException(
							"Course", request.coursePublicUuid()));
		} else if (scope == GradeRegisterTemplateScope.LEVEL) {
			if (request.levelPublicUuid() == null || request.levelPublicUuid().isBlank()) {
				throw new BadRequestException(
						"GRADE_REGISTER_LEVEL_REQUIRED",
						"levelPublicUuid is required when scope=LEVEL");
			}
			level = academicLevelRepository.findByPublicUuid(UUID.fromString(request.levelPublicUuid()))
					.orElseThrow(() -> new ResourceNotFoundException(
							"AcademicLevel", request.levelPublicUuid()));
		} else if (scope != GradeRegisterTemplateScope.TENANT) {
			throw new BadRequestException(
					"GRADE_REGISTER_SCOPE_UNSUPPORTED",
					"Unsupported grade register scope");
		}

		if (Boolean.TRUE.equals(request.replaceActive())) {
			if (scope == GradeRegisterTemplateScope.TENANT) {
				archiveActiveTenantTemplateIfUnused(year);
			} else if (scope == GradeRegisterTemplateScope.LEVEL) {
				archiveLevelTemplateIfUnused(year, level);
			} else {
				throw new BadRequestException(
						"GRADE_REGISTER_REPLACE_TENANT_ONLY",
						"replaceActive is only supported for TENANT and LEVEL scope");
			}
		} else if (scope == GradeRegisterTemplateScope.LEVEL
				&& templateRepository.findLevelTemplate(year, level).isPresent()) {
			throw new ConflictException(
					"GRADE_REGISTER_LEVEL_EXISTS",
					"This level already has a template. Replace it explicitly.");
		}

		boolean setDefault = scope == GradeRegisterTemplateScope.TENANT
				&& (Boolean.TRUE.equals(request.setAsDefault())
						|| scope == GradeRegisterTemplateScope.TENANT);

		GradeRegisterTemplate copy = new GradeRegisterTemplate();
		copy.setPublicUuid(UUID.randomUUID());
		copy.setName(source.getName());
		copy.setDescription(source.getDescription());
		copy.setScope(scope);
		copy.setIsSystem(Boolean.FALSE);
		copy.setAcademicYear(year);
		copy.setCourse(course);
		copy.setAcademicLevel(level);
		copy.setSourceTemplate(source);
		copy.setCatalogCode(null);
		copy.setIsDefault(Boolean.FALSE);
		copy.setRecommendedLevels(List.of());
		copy.setGradingScale(source.getGradingScale() != null
				? source.getGradingScale()
				: EvaluationScale.SCORE_0_20);
		templateRepository.save(copy);

		for (GradeCategory srcCat : categoriesOf(source)) {
			GradeCategory cat = new GradeCategory();
			cat.setPublicUuid(UUID.randomUUID());
			cat.setTemplate(copy);
			cat.setName(srcCat.getName());
			cat.setColorToken(srcCat.getColorToken());
			cat.setSortOrder(srcCat.getSortOrder());
			cat.setWeight(srcCat.getWeight());
			cat.setContributesToAverage(srcCat.getContributesToAverage());
			cat.setAllowsTeacherColumns(srcCat.getAllowsTeacherColumns());
			cat.setColumnMode(srcCat.getColumnMode() != null
					? srcCat.getColumnMode()
					: GradeCategoryColumnMode.OPEN);
			cat.setSlotCount(srcCat.getSlotCount());
			cat.setSlotLabelPrefix(srcCat.getSlotLabelPrefix());
			cat.setSlotLabels(srcCat.getSlotLabels() != null
					? List.copyOf(srcCat.getSlotLabels())
					: null);
			categoryRepository.save(cat);
		}

		if (setDefault && scope == GradeRegisterTemplateScope.TENANT) {
			clearOtherDefaults(year, copy.getPublicUuid());
			copy.setIsDefault(Boolean.TRUE);
			templateRepository.save(copy);
		}

		log.info("[grade-register] adopted catalog={} -> tenant template={}",
				source.getPublicUuid(), copy.getPublicUuid());
		return mapper.toResponse(copy, categoriesOf(copy));
	}

	@Override
	@Transactional
	public GradeRegisterTemplateResponse update(
			UUID publicUuid,
			UpdateGradeRegisterTemplateRequest request
	) {
		GradeRegisterTemplate t = requireWritable(publicUuid);
		if (request.name() != null && !request.name().isBlank()) {
			t.setName(request.name().trim());
		}
		if (request.description() != null) {
			t.setDescription(request.description());
		}
		if (request.isDefault() != null
				&& t.getScope() == GradeRegisterTemplateScope.TENANT) {
			if (Boolean.TRUE.equals(request.isDefault())) {
				clearOtherDefaults(t.getAcademicYear(), t.getPublicUuid());
				t.setIsDefault(Boolean.TRUE);
			} else {
				t.setIsDefault(Boolean.FALSE);
			}
		}
		if (request.gradingScale() != null) {
			applyGradingScale(t, request.gradingScale());
		}
		templateRepository.save(t);
		return mapper.toResponse(t, categoriesOf(t));
	}

	@Override
	@Transactional
	public void delete(UUID publicUuid) {
		GradeRegisterTemplate t = requireWritable(publicUuid);
		assertUnused(t);
		archive(t);
	}

	@Override
	@Transactional
	public GradeRegisterTemplateResponse addCategory(
			UUID templateUuid,
			CreateGradeCategoryRequest request
	) {
		GradeRegisterTemplate t = requireWritable(templateUuid);
		if (categoryRepository.existsByTemplateAndNameIgnoreCase(t, request.name().trim())) {
			throw new ConflictException(
					"GRADE_CATEGORY_NAME_EXISTS",
					"Category name already exists in this template");
		}
		GradeCategory cat = new GradeCategory();
		cat.setPublicUuid(UUID.randomUUID());
		cat.setTemplate(t);
		cat.setName(request.name().trim());
		cat.setColorToken(request.colorToken() != null ? request.colorToken() : "neutral");
		cat.setSortOrder(request.sortOrder());
		cat.setWeight(request.weight());
		cat.setContributesToAverage(
				request.contributesToAverage() == null || request.contributesToAverage());
		cat.setAllowsTeacherColumns(
				request.allowsTeacherColumns() == null || request.allowsTeacherColumns());
		applyColumnLayout(cat,
				request.columnMode(),
				request.slotCount(),
				request.slotLabelPrefix(),
				request.slotLabels());
		categoryRepository.save(cat);
		return mapper.toResponse(t, categoriesOf(t));
	}

	@Override
	@Transactional
	public GradeRegisterTemplateResponse updateCategory(
			UUID categoryUuid,
			UpdateGradeCategoryRequest request
	) {
		GradeCategory cat = categoryRepository.findByPublicUuid(categoryUuid)
				.orElseThrow(() -> new ResourceNotFoundException("GradeCategory", categoryUuid));
		GradeRegisterTemplate t = requireWritable(cat.getTemplate().getPublicUuid());
		if (request.name() != null && !request.name().isBlank()) {
			cat.setName(request.name().trim());
		}
		if (request.colorToken() != null) {
			cat.setColorToken(request.colorToken());
		}
		if (request.sortOrder() != null) {
			cat.setSortOrder(request.sortOrder());
		}
		if (request.weight() != null) {
			cat.setWeight(request.weight());
		}
		if (request.contributesToAverage() != null) {
			cat.setContributesToAverage(request.contributesToAverage());
		}
		if (request.allowsTeacherColumns() != null) {
			cat.setAllowsTeacherColumns(request.allowsTeacherColumns());
		}
		if (request.columnMode() != null
				|| request.slotCount() != null
				|| request.slotLabelPrefix() != null
				|| request.slotLabels() != null) {
			applyColumnLayout(cat,
					request.columnMode() != null ? request.columnMode() : cat.getColumnMode(),
					request.slotCount() != null ? request.slotCount() : cat.getSlotCount(),
					request.slotLabelPrefix() != null
							? request.slotLabelPrefix()
							: cat.getSlotLabelPrefix(),
					request.slotLabels() != null ? request.slotLabels() : cat.getSlotLabels());
		}
		categoryRepository.save(cat);
		return mapper.toResponse(t, categoriesOf(t));
	}

	@Override
	@Transactional
	public void deleteCategory(UUID categoryUuid) {
		GradeCategory cat = categoryRepository.findByPublicUuid(categoryUuid)
				.orElseThrow(() -> new ResourceNotFoundException("GradeCategory", categoryUuid));
		requireWritable(cat.getTemplate().getPublicUuid());

		List<Evaluation> linked = evaluationRepository.findAllByCategory(cat);
		for (Evaluation evaluation : linked) {
			boolean hasGrades = gradeRecordRepository.existsByEvaluation(evaluation);
			boolean draft = evaluation.getStatus() == EvaluationStatus.DRAFT;
			if (!draft || hasGrades) {
				throw new ConflictException(
						"GRADE_CATEGORY_IN_USE",
						"Cannot delete category '" + cat.getName()
								+ "': it has published/closed evaluations or grades. "
								+ "Delete or close those registros first.");
			}
			evaluationRepository.delete(evaluation);
		}

		cat.markDeleted();
		categoryRepository.save(cat);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<GradeRegisterTemplate> resolveTemplate(TeacherAssignment assignment) {
		var period = assignment.getAcademicPeriod();
		if (period == null || period.getAcademicYear() == null) {
			return Optional.empty();
		}
		AcademicYear year = period.getAcademicYear();
		Course course = assignment.getCourse();

		Optional<GradeRegisterTemplate> courseTpl =
				templateRepository.findCourseTemplate(year, course);
		if (courseTpl.isPresent()) {
			return courseTpl;
		}

		AcademicLevel level = levelOf(assignment);
		if (level != null) {
			Optional<GradeRegisterTemplate> levelTpl =
					templateRepository.findLevelTemplate(year, level);
			if (levelTpl.isPresent()) {
				return levelTpl;
			}
		}

		return templateRepository.findDefaultTenantTemplate(year);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<GradeRegisterTemplateResponse> resolveForAssignment(
			TeacherAssignment assignment
	) {
		return resolveTemplate(assignment)
				.map(t -> mapper.toResponse(t, categoriesOf(t)));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<GradeCategory> findCategoryByPublicUuid(UUID categoryPublicUuid) {
		return categoryRepository.findByPublicUuid(categoryPublicUuid);
	}

	private void applyColumnLayout(
			GradeCategory cat,
			GradeCategoryColumnMode mode,
			Integer slotCount,
			String slotLabelPrefix,
			List<String> slotLabels
	) {
		GradeCategoryColumnMode resolved = mode != null ? mode : GradeCategoryColumnMode.OPEN;
		cat.setColumnMode(resolved);
		cat.setSlotLabelPrefix(
				slotLabelPrefix != null && !slotLabelPrefix.isBlank() ? slotLabelPrefix.trim() : "S");
		if (slotLabels != null) {
			List<String> cleaned = slotLabels.stream()
					.filter(s -> s != null && !s.isBlank())
					.map(String::trim)
					.toList();
			cat.setSlotLabels(cleaned.isEmpty() ? null : cleaned);
		}
		switch (resolved) {
			case OPEN -> {
				cat.setSlotCount(null);
			}
			case SINGLE -> {
				cat.setSlotCount(1);
				if (cat.getSlotLabels() == null || cat.getSlotLabels().isEmpty()) {
					cat.setSlotLabels(null);
				}
			}
			case SERIES -> {
				int n = slotCount != null ? slotCount : 0;
				if (cat.getSlotLabels() != null && !cat.getSlotLabels().isEmpty()) {
					n = cat.getSlotLabels().size();
				}
				if (n < 1 || n > 40) {
					throw new BadRequestException(
							"GRADE_CATEGORY_SLOT_COUNT",
							"SERIES categories require slotCount between 1 and 40 (or a non-empty slotLabels list)");
				}
				cat.setSlotCount(n);
			}
		}
	}

	/**
	 * Soft-deletes the current TENANT default for the year if unused.
	 * Throws {@code GRADE_REGISTER_IN_USE} when evaluations (or grade records)
	 * are linked to its categories.
	 */
	private void archiveActiveTenantTemplateIfUnused(AcademicYear year) {
		Optional<GradeRegisterTemplate> activeOpt = templateRepository.findDefaultTenantTemplate(year);
		if (activeOpt.isEmpty()) {
			return;
		}
		GradeRegisterTemplate active = activeOpt.get();
		assertUnused(active);
		archive(active);
		log.info("[grade-register] archived active template {} for year {}",
				active.getPublicUuid(), year.getPublicUuid());
	}

	/** Soft-deletes the LEVEL copy for this year if unused. */
	private void archiveLevelTemplateIfUnused(AcademicYear year, AcademicLevel level) {
		Optional<GradeRegisterTemplate> activeOpt = templateRepository.findLevelTemplate(year, level);
		if (activeOpt.isEmpty()) {
			return;
		}
		GradeRegisterTemplate active = activeOpt.get();
		assertUnused(active);
		archive(active);
		log.info("[grade-register] archived level template {} for level {}",
				active.getPublicUuid(), level.getPublicUuid());
	}

	private void applyGradingScale(GradeRegisterTemplate template, EvaluationScale scale) {
		if (scale != EvaluationScale.SCORE_0_20 && scale != EvaluationScale.LITERAL_CNEB) {
			throw new BadRequestException(
					"GRADE_REGISTER_SCALE_UNSUPPORTED",
					"Grade register scale must be SCORE_0_20 or LITERAL_CNEB");
		}
		EvaluationScale current = template.getGradingScale() != null
				? template.getGradingScale()
				: EvaluationScale.SCORE_0_20;
		if (current == scale) {
			return;
		}
		assertUnused(template);
		template.setGradingScale(scale);
	}

	private void assertUnused(GradeRegisterTemplate template) {
		AcademicYear year = template.getAcademicYear();
		List<GradeCategory> cats = categoriesOf(template);
		long evaluationCount = 0L;
		long gradeRecordCount = 0L;
		if (year != null && !cats.isEmpty()) {
			evaluationCount = evaluationRepository.countByCategoriesAndYear(cats, year);
			gradeRecordCount = gradeRecordRepository.countByCategoriesAndYear(cats, year);
		}
		if (evaluationCount > 0 || gradeRecordCount > 0) {
			throw new ConflictException(
					"GRADE_REGISTER_IN_USE",
					"Cannot replace active template: "
							+ evaluationCount + " evaluations and "
							+ gradeRecordCount
							+ " grade records are linked to its categories. "
							+ "Adjust categories or wait until the next academic year.");
		}
	}

	private void archive(GradeRegisterTemplate template) {
		for (GradeCategory c : categoriesOf(template)) {
			c.markDeleted();
			categoryRepository.save(c);
		}
		template.markDeleted();
		templateRepository.save(template);
	}

	private void clearOtherDefaults(AcademicYear year, UUID excludeUuid) {
		for (GradeRegisterTemplate other : templateRepository.findOtherDefaults(year, excludeUuid)) {
			other.setIsDefault(Boolean.FALSE);
			templateRepository.save(other);
		}
	}

	private List<GradeCategory> categoriesOf(GradeRegisterTemplate t) {
		return categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(t);
	}

	private GradeRegisterTemplate requireTemplate(UUID publicUuid) {
		return templateRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"GradeRegisterTemplate", publicUuid));
	}

	private GradeRegisterTemplate requireWritable(UUID publicUuid) {
		GradeRegisterTemplate t = requireTemplate(publicUuid);
		if (Boolean.TRUE.equals(t.getIsSystem())) {
			throw new BadRequestException(
					"GRADE_REGISTER_SYSTEM_READONLY",
					"System catalog templates are read-only; adopt them first");
		}
		return t;
	}

	private AcademicYear requireYear(UUID publicUuid) {
		return academicYearRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", publicUuid));
	}

	private static AcademicLevel levelOf(TeacherAssignment assignment) {
		if (assignment.getSection() == null || assignment.getSection().getGrade() == null) {
			return null;
		}
		return assignment.getSection().getGrade().getLevel();
	}
}
