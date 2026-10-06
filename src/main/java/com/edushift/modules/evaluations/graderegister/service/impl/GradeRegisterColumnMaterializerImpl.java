package com.edushift.modules.evaluations.graderegister.service.impl;

import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategoryColumnMode;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import com.edushift.modules.evaluations.graderegister.repository.GradeCategoryRepository;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterColumnMaterializer;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterTemplateService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class GradeRegisterColumnMaterializerImpl implements GradeRegisterColumnMaterializer {

	private final GradeCategoryRepository categoryRepository;
	private final EvaluationRepository evaluationRepository;
	private final GradeRegisterTemplateService templateService;

	@Override
	@Transactional
	public void ensureColumns(TeacherAssignment assignment) {
		Optional<GradeRegisterTemplate> templateOpt = templateService.resolveTemplate(assignment);
		if (templateOpt.isEmpty()) {
			return;
		}
		GradeRegisterTemplate template = templateOpt.get();
		List<GradeCategory> categories =
				categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(template);

		LocalDate scheduled = resolveScheduledDate(assignment);
		List<Evaluation> existing = evaluationRepository.findAllByAssignment(assignment);
		int created = 0;
		for (GradeCategory category : categories) {
			GradeCategoryColumnMode mode = category.getColumnMode() != null
					? category.getColumnMode()
					: GradeCategoryColumnMode.OPEN;
			if (mode == GradeCategoryColumnMode.OPEN) {
				continue;
			}
			List<String> labels = category.resolvedSlotLabels();
			if (labels.isEmpty()) {
				continue;
			}
			boolean anyInCategory = existing.stream()
					.anyMatch(e -> e.getCategory() != null
							&& category.getId().equals(e.getCategory().getId()));
			if (anyInCategory) {
				/* Ya materializado para esta categoría (idempotente). */
				continue;
			}
			if (mode == GradeCategoryColumnMode.SINGLE) {
				String label = labels.getFirst();
				if (hasNameInCategory(existing, category, label)) {
					continue;
				}
				Evaluation createdEval = createDraft(assignment, category, label, scheduled);
				existing.add(createdEval);
				created++;
				continue;
			}
			/* SERIES: S1…Sn pueden repetirse en otra categoría (unicidad por categoría). */
			for (String label : labels) {
				if (hasNameInCategory(existing, category, label)) {
					continue;
				}
				Evaluation createdEval = createDraft(assignment, category, label, scheduled);
				existing.add(createdEval);
				created++;
			}
		}
		if (created > 0) {
			log.info("[grade-register] materialised {} slot column(s) for assignment={}",
					created, assignment.getPublicUuid());
		}
	}

	/** Unicidad de nombre solo dentro de la misma categoría del assignment. */
	private static boolean hasNameInCategory(
			List<Evaluation> existing,
			GradeCategory category,
			String label
	) {
		for (Evaluation e : existing) {
			if (e.getName() == null || !e.getName().equalsIgnoreCase(label)) {
				continue;
			}
			if (e.getCategory() == null) {
				continue;
			}
			if (category.getId().equals(e.getCategory().getId())) {
				return true;
			}
		}
		return false;
	}

	private static LocalDate resolveScheduledDate(TeacherAssignment assignment) {
		LocalDate today = LocalDate.now();
		var period = assignment.getAcademicPeriod();
		if (period != null && period.getStartDate() != null) {
			LocalDate start = period.getStartDate();
			return start.isAfter(today) ? today : start;
		}
		return today;
	}

	private Evaluation createDraft(
			TeacherAssignment assignment,
			GradeCategory category,
			String name,
			LocalDate scheduledDate
	) {
		Evaluation evaluation = new Evaluation();
		evaluation.setPublicUuid(UUID.randomUUID());
		evaluation.setTeacherAssignment(assignment);
		evaluation.setCategory(category);
		evaluation.setKind(EvaluationKind.TASK);
		evaluation.setName(name);
		evaluation.setDescription(null);
		evaluation.setWeight(BigDecimal.ONE);
		evaluation.setScheduledDate(scheduledDate);
		evaluation.setDueDate(null);
		evaluation.setScale(scaleOf(category));
		evaluation.setStatus(EvaluationStatus.DRAFT);
		evaluation.setIsActive(Boolean.TRUE);
		return evaluationRepository.saveAndFlush(evaluation);
	}

	private static EvaluationScale scaleOf(GradeCategory category) {
		GradeRegisterTemplate template = category.getTemplate();
		if (template != null && template.getGradingScale() != null) {
			return template.getGradingScale();
		}
		return EvaluationScale.SCORE_0_20;
	}
}
