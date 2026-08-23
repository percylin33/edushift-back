package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.schedule.daytemplate.dto.CloneScheduleRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleBlock;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import com.edushift.modules.schedule.daytemplate.mapper.DayScheduleBlockMapper;
import com.edushift.modules.schedule.daytemplate.mapper.DayScheduleTemplateMapper;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleBlockRepository;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleTemplateRepository;
import com.edushift.modules.schedule.daytemplate.service.ScheduleCloneService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
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
public class ScheduleCloneServiceImpl implements ScheduleCloneService {

	private final AcademicYearRepository yearRepository;
	private final AcademicLevelRepository levelRepository;
	private final GradeRepository gradeRepository;
	private final DayScheduleTemplateRepository templateRepository;
	private final DayScheduleBlockRepository blockRepository;
	private final DayScheduleTemplateMapper templateMapper;
	private final DayScheduleBlockMapper blockMapper;

	@Override
	@Transactional
	public List<DayTemplateResponse> cloneSchedule(UUID targetYearUuid, CloneScheduleRequest request) {
		if (request == null || request.sourceYearUuid() == null) {
			throw new BadRequestException("SOURCE_YEAR_REQUIRED", "sourceYearUuid is required");
		}
		AcademicYear target = yearRepository.findByPublicUuid(targetYearUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", targetYearUuid));
		AcademicYear source = yearRepository.findByPublicUuid(request.sourceYearUuid())
				.orElseThrow(() -> new ResourceNotFoundException(
						"AcademicYear", request.sourceYearUuid()));

		if (target.getId().equals(source.getId())) {
			throw new BadRequestException("CLONE_SAME_YEAR",
					"source and target academic years must differ");
		}

		List<DayScheduleTemplate> sourceTemplates = templateRepository.findAllByAcademicYear(source);
		List<DayTemplateResponse> cloned = new ArrayList<>();

		for (DayScheduleTemplate src : sourceTemplates) {
			AcademicLevel level = resolveLevelByCode(src.getAcademicLevel());
			if (level == null) {
				log.warn("[schedule.clone] skip template {} — level code not found in tenant",
						src.getPublicUuid());
				continue;
			}
			Grade grade = null;
			if (src.getGrade() != null) {
				grade = resolveGrade(level, src.getGrade());
				if (grade == null) {
					log.warn("[schedule.clone] skip grade override for template {} — grade missing",
							src.getPublicUuid());
					continue;
				}
			}

			Optional<DayScheduleTemplate> existing = grade != null
					? templateRepository.findGradeSpecific(target, level, grade, src.getShift())
					: templateRepository.findLevelDefault(target, level, src.getShift());
			if (existing.isPresent()) {
				cloned.add(toResponse(existing.get()));
				continue;
			}

			DayScheduleTemplate copy = new DayScheduleTemplate();
			copy.setAcademicYear(target);
			copy.setAcademicLevel(level);
			copy.setGrade(grade);
			copy.setShift(src.getShift());
			copy.setName(src.getName());
			copy.setRecessShareGroup(src.getRecessShareGroup());
			DayScheduleTemplate saved = templateRepository.saveAndFlush(copy);

			for (DayScheduleBlock srcBlock : blockRepository.findByTemplateOrdered(src)) {
				DayScheduleBlock block = new DayScheduleBlock();
				block.setTemplate(saved);
				block.setDayOfWeek(srcBlock.getDayOfWeek());
				block.setStartTime(srcBlock.getStartTime());
				block.setEndTime(srcBlock.getEndTime());
				block.setBlockType(srcBlock.getBlockType());
				block.setLabel(srcBlock.getLabel());
				blockRepository.save(block);
			}
			cloned.add(toResponse(saved));
		}

		if (request.includeTimeSlots()) {
			log.info("[schedule.clone] includeTimeSlots=true but v1 skips TimeSlot copy "
					+ "(requires matching sections/assignments) — templates only. "
					+ "targetYear={}", targetYearUuid);
		}

		log.info("[schedule.clone] cloned {} templates from {} to {}",
				cloned.size(), source.getPublicUuid(), target.getPublicUuid());
		return cloned;
	}

	private AcademicLevel resolveLevelByCode(AcademicLevel sourceLevel) {
		if (sourceLevel == null || sourceLevel.getCode() == null) {
			return null;
		}
		return levelRepository.findByCodeIgnoreCase(sourceLevel.getCode()).orElse(null);
	}

	private Grade resolveGrade(AcademicLevel targetLevel, Grade sourceGrade) {
		if (sourceGrade == null || sourceGrade.getOrdinal() == null) {
			return null;
		}
		return gradeRepository.findByLevelAndOrdinal(targetLevel, sourceGrade.getOrdinal())
				.orElse(null);
	}

	private DayTemplateResponse toResponse(DayScheduleTemplate template) {
		return templateMapper.toResponse(template,
				blockRepository.findByTemplateOrdered(template).stream()
						.map(blockMapper::toResponse)
						.toList());
	}
}
