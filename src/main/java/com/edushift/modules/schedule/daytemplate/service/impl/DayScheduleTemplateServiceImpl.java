package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.schedule.daytemplate.dto.ApplyDayPlanRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayBlockResponse;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.NonTeachingBlockItem;
import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleBlock;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import com.edushift.modules.schedule.daytemplate.mapper.DayScheduleBlockMapper;
import com.edushift.modules.schedule.daytemplate.mapper.DayScheduleTemplateMapper;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleBlockRepository;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleTemplateRepository;
import com.edushift.modules.schedule.daytemplate.service.DayPlanCalculator;
import com.edushift.modules.schedule.daytemplate.service.DayScheduleTemplateService;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DayScheduleTemplateServiceImpl implements DayScheduleTemplateService {

	private final DayScheduleTemplateRepository templateRepository;
	private final DayScheduleBlockRepository blockRepository;
	private final AcademicYearRepository yearRepository;
	private final AcademicLevelRepository levelRepository;
	private final GradeRepository gradeRepository;
	private final DayScheduleTemplateMapper templateMapper;
	private final DayScheduleBlockMapper blockMapper;

	@Override
	@Transactional(readOnly = true)
	public List<DayTemplateResponse> listByYear(UUID yearUuid) {
		loadYear(yearUuid);
		return templateRepository.findByAcademicYear_PublicUuidOrderByNameAsc(yearUuid).stream()
				.map(this::toResponseWithBlocks)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public DayTemplateResponse get(UUID templateUuid) {
		return toResponseWithBlocks(loadTemplate(templateUuid));
	}

	@Override
	@Transactional
	public DayTemplateResponse create(CreateDayTemplateRequest request) {
		AcademicYear year = loadYear(request.yearUuid());
		AcademicLevel level = levelRepository.findByPublicUuid(request.levelUuid())
				.orElseThrow(() -> new ResourceNotFoundException("AcademicLevel", request.levelUuid()));
		Grade grade = null;
		if (request.gradeUuid() != null) {
			grade = gradeRepository.findByPublicUuid(request.gradeUuid())
					.orElseThrow(() -> new ResourceNotFoundException("Grade", request.gradeUuid()));
			if (!grade.getLevel().getId().equals(level.getId())) {
				throw new BadRequestException("GRADE_LEVEL_MISMATCH",
						"Grade does not belong to the given academic level");
			}
		}

		DayScheduleTemplate template = templateMapper.fromCreate(request, year, level, grade);
		try {
			DayScheduleTemplate saved = templateRepository.saveAndFlush(template);
			log.info("[schedule.day-template] created -- uuid={} year={} level={}",
					saved.getPublicUuid(), year.getPublicUuid(), level.getCode());
			return toResponseWithBlocks(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException("DAY_TEMPLATE_SCOPE_EXISTS",
					"A template already exists for this year/level/grade/shift scope");
		}
	}

	@Override
	@Transactional
	public DayTemplateResponse update(UUID templateUuid, UpdateDayTemplateRequest request) {
		DayScheduleTemplate template = loadTemplate(templateUuid);
		templateMapper.applyUpdate(request, template);
		DayScheduleTemplate saved = templateRepository.saveAndFlush(template);
		return toResponseWithBlocks(saved);
	}

	@Override
	@Transactional
	public void delete(UUID templateUuid) {
		DayScheduleTemplate template = loadTemplate(templateUuid);
		List<DayScheduleBlock> blocks = blockRepository.findByTemplateOrdered(template);
		for (DayScheduleBlock block : blocks) {
			blockRepository.delete(block);
		}
		templateRepository.delete(template);
		log.info("[schedule.day-template] deleted -- uuid={}", template.getPublicUuid());
	}

	@Override
	@Transactional
	public DayBlockResponse addBlock(UUID templateUuid, CreateDayBlockRequest request) {
		DayScheduleTemplate template = loadTemplate(templateUuid);
		validateTimeRange(request.startTime(), request.endTime());
		DayScheduleBlock block = blockMapper.fromCreate(request, template);
		DayScheduleBlock saved = blockRepository.saveAndFlush(block);
		return blockMapper.toResponse(saved);
	}

	@Override
	@Transactional
	public DayBlockResponse updateBlock(UUID blockUuid, UpdateDayBlockRequest request) {
		DayScheduleBlock block = loadBlock(blockUuid);
		blockMapper.applyUpdate(request, block);
		validateTimeRange(block.getStartTime(), block.getEndTime());
		return blockMapper.toResponse(blockRepository.saveAndFlush(block));
	}

	@Override
	@Transactional
	public void deleteBlock(UUID blockUuid) {
		DayScheduleBlock block = loadBlock(blockUuid);
		blockRepository.delete(block);
	}

	@Override
	@Transactional
	public List<DayTemplateResponse> seedDefaultTemplatesForYear(UUID yearUuid) {
		AcademicYear year = loadYear(yearUuid);
		seedDefaultTemplatesForYear(year);
		return listByYear(yearUuid);
	}

	@Override
	@Transactional
	public void seedDefaultTemplatesForYear(AcademicYear year) {
		List<AcademicLevel> levels = levelRepository.findAllByOrderByOrdinalAsc();
		for (AcademicLevel level : levels) {
			Optional<DayScheduleTemplate> existing = templateRepository
					.findLevelDefault(year, level, null);
			if (existing.isPresent()) {
				ensureDefaultBlocks(existing.get(), level.getCode());
				continue;
			}
			DayScheduleTemplate template = new DayScheduleTemplate();
			template.setAcademicYear(year);
			template.setAcademicLevel(level);
			template.setName("Jornada " + level.getName());
			template.setRecessShareGroup(level.getCode());
			DayScheduleTemplate saved = templateRepository.saveAndFlush(template);
			createDefaultBlocks(saved, level.getCode());
			log.info("[schedule.day-template] seeded default -- year={} level={}",
					year.getPublicUuid(), level.getCode());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<DayScheduleTemplate> resolveTemplateForSection(Section section) {
		if (section == null || section.getAcademicYear() == null || section.getGrade() == null) {
			return Optional.empty();
		}
		Grade grade = section.getGrade();
		AcademicLevel level = grade.getLevel();
		AcademicYear year = section.getAcademicYear();

		Optional<DayScheduleTemplate> gradeSpecific = templateRepository
				.findGradeSpecific(year, level, grade, null);
		if (gradeSpecific.isPresent()) {
			return gradeSpecific;
		}
		return templateRepository.findLevelDefault(year, level, null);
	}

	@Override
	@Transactional(readOnly = true)
	public List<NonTeachingBlockItem> listHardNonTeachingBlocksForSection(
			Section section, Short dayOfWeek) {
		Optional<DayScheduleTemplate> templateOpt = resolveTemplateForSection(section);
		if (templateOpt.isEmpty()) {
			return List.of();
		}
		DayScheduleTemplate template = templateOpt.get();
		String levelCode = template.getAcademicLevel() != null
				? template.getAcademicLevel().getCode() : null;
		String gradeName = template.getGrade() != null
				? template.getGrade().getName()
				: (section.getGrade() != null ? section.getGrade().getName() : null);

		List<NonTeachingBlockItem> result = new ArrayList<>();
		for (DayScheduleBlock block : blockRepository.findByTemplateOrdered(template)) {
			if (!block.isHardNonTeaching()) {
				continue;
			}
			if (dayOfWeek != null
					&& block.getDayOfWeek() != null
					&& !block.getDayOfWeek().equals(dayOfWeek)) {
				continue;
			}
			result.add(new NonTeachingBlockItem(
					block.getPublicUuid(),
					block.getDayOfWeek(),
					block.getStartTime(),
					block.getEndTime(),
					block.getBlockType(),
					block.getLabel(),
					levelCode,
					gradeName
			));
		}
		return result;
	}

	@Override
	@Transactional
	public DayTemplateResponse applyDayPlan(UUID templateUuid, ApplyDayPlanRequest request) {
		DayScheduleTemplate template = loadTemplate(templateUuid);
		DayPlanCalculator.validateDayWindow(
				request.dayStart(), request.dayEnd(), request.periodMinutes());

		List<ApplyDayPlanRequest.DayPlanWindowRequest> recesses =
				request.recesses() == null ? List.of() : request.recesses();
		for (ApplyDayPlanRequest.DayPlanWindowRequest recess : recesses) {
			DayPlanCalculator.validateWindowInsideDay(
					request.dayStart(), request.dayEnd(),
					recess.startTime(), recess.endTime(), "DAY_PLAN_RECESS_INVALID");
		}
		if (request.lunch() != null) {
			DayPlanCalculator.validateWindowInsideDay(
					request.dayStart(), request.dayEnd(),
					request.lunch().startTime(), request.lunch().endTime(),
					"DAY_PLAN_LUNCH_INVALID");
		}

		for (DayScheduleBlock block : blockRepository.findByTemplateOrdered(template)) {
			DayBlockType type = block.getBlockType();
			if (type == DayBlockType.RECESS || type == DayBlockType.LUNCH) {
				blockRepository.delete(block);
			}
		}

		for (ApplyDayPlanRequest.DayPlanWindowRequest recess : recesses) {
			String label = recess.label() == null || recess.label().isBlank()
					? "Recreo" : recess.label().trim();
			saveBlock(template, DayBlockType.RECESS, label, recess.startTime(), recess.endTime());
		}
		if (request.lunch() != null) {
			String label = request.lunch().label() == null || request.lunch().label().isBlank()
					? "Lonchera" : request.lunch().label().trim();
			saveBlock(template, DayBlockType.LUNCH, label,
					request.lunch().startTime(), request.lunch().endTime());
		}

		template.setDayStart(request.dayStart());
		template.setDayEnd(request.dayEnd());
		template.setPeriodMinutes(request.periodMinutes());
		DayScheduleTemplate saved = templateRepository.saveAndFlush(template);
		log.info("[schedule.day-template] apply-day-plan -- uuid={} start={} end={} period={}",
				saved.getPublicUuid(), saved.getDayStart(), saved.getDayEnd(),
				saved.getPeriodMinutes());
		return toResponseWithBlocks(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SuggestedPeriodItem> listSuggestedPeriodsForSection(Section section) {
		Optional<DayScheduleTemplate> templateOpt = resolveTemplateForSection(section);
		if (templateOpt.isEmpty()) {
			return List.of();
		}
		return computeSuggestedPeriods(templateOpt.get());
	}

	private List<SuggestedPeriodItem> computeSuggestedPeriods(DayScheduleTemplate template) {
		if (template.getDayStart() == null
				|| template.getDayEnd() == null
				|| template.getPeriodMinutes() == null) {
			return List.of();
		}
		List<DayPlanCalculator.Interval> hard = new ArrayList<>();
		for (DayScheduleBlock block : blockRepository.findByTemplateOrdered(template)) {
			if (block.isHardNonTeaching()) {
				hard.add(new DayPlanCalculator.Interval(block.getStartTime(), block.getEndTime()));
			}
		}
		return DayPlanCalculator.computeSuggestedPeriods(
				template.getDayStart(),
				template.getDayEnd(),
				template.getPeriodMinutes(),
				hard);
	}

	private void createDefaultBlocks(DayScheduleTemplate template, String levelCode) {
		ensureDefaultBlocks(template, levelCode);
	}

	/**
	 * Ensures the level-default RECESS + LUNCH windows exist. Used both when
	 * creating a new template and when re-seeding an existing one that is
	 * missing either block (e.g. after soft-deletes). Returns how many blocks
	 * were inserted.
	 */
	private int ensureDefaultBlocks(DayScheduleTemplate template, String levelCode) {
		List<DayScheduleBlock> existing = blockRepository.findByTemplateOrdered(template);
		Set<DayBlockType> present = EnumSet.noneOf(DayBlockType.class);
		for (DayScheduleBlock block : existing) {
			if (block.getBlockType() != null) {
				present.add(block.getBlockType());
			}
		}
		String code = levelCode == null ? "" : levelCode.toUpperCase();
		LocalTime recessStart;
		LocalTime recessEnd;
		LocalTime lunchStart;
		LocalTime lunchEnd;
		switch (code) {
			case "INICIAL" -> {
				recessStart = LocalTime.of(10, 0);
				recessEnd = LocalTime.of(10, 30);
				lunchStart = LocalTime.of(12, 0);
				lunchEnd = LocalTime.of(12, 45);
			}
			case "PRIMARIA" -> {
				recessStart = LocalTime.of(10, 15);
				recessEnd = LocalTime.of(10, 35);
				lunchStart = LocalTime.of(12, 30);
				lunchEnd = LocalTime.of(13, 15);
			}
			case "SECUNDARIA" -> {
				recessStart = LocalTime.of(10, 45);
				recessEnd = LocalTime.of(11, 0);
				lunchStart = LocalTime.of(13, 0);
				lunchEnd = LocalTime.of(13, 45);
			}
			default -> {
				recessStart = LocalTime.of(10, 15);
				recessEnd = LocalTime.of(10, 35);
				lunchStart = LocalTime.of(12, 30);
				lunchEnd = LocalTime.of(13, 15);
			}
		}
		int added = 0;
		if (!present.contains(DayBlockType.RECESS)) {
			saveBlock(template, DayBlockType.RECESS, "Recreo", recessStart, recessEnd);
			added++;
		}
		if (!present.contains(DayBlockType.LUNCH)) {
			saveBlock(template, DayBlockType.LUNCH, "Lonchera", lunchStart, lunchEnd);
			added++;
		}
		if (template.getDayStart() == null || template.getDayEnd() == null
				|| template.getPeriodMinutes() == null) {
			applySeedDayWindow(template, code, lunchEnd);
			templateRepository.save(template);
		}
		return added;
	}

	private void applySeedDayWindow(DayScheduleTemplate template, String code, LocalTime lunchEnd) {
		LocalTime start;
		LocalTime end;
		int periodMinutes = 45;
		switch (code) {
			case "INICIAL" -> {
				start = LocalTime.of(8, 0);
				end = lunchEnd.isAfter(LocalTime.of(13, 0)) ? lunchEnd : LocalTime.of(13, 0);
			}
			case "SECUNDARIA" -> {
				start = LocalTime.of(7, 30);
				end = lunchEnd.isAfter(LocalTime.of(15, 30)) ? lunchEnd : LocalTime.of(15, 30);
			}
			default -> {
				start = LocalTime.of(8, 0);
				end = lunchEnd.isAfter(LocalTime.of(13, 15)) ? lunchEnd : LocalTime.of(13, 15);
			}
		}
		template.setDayStart(start);
		template.setDayEnd(end);
		template.setPeriodMinutes(periodMinutes);
	}

	private void saveBlock(DayScheduleTemplate template, DayBlockType type, String label,
			LocalTime start, LocalTime end) {
		DayScheduleBlock block = new DayScheduleBlock();
		block.setTemplate(template);
		block.setBlockType(type);
		block.setLabel(label);
		block.setStartTime(start);
		block.setEndTime(end);
		blockRepository.save(block);
	}

	private DayTemplateResponse toResponseWithBlocks(DayScheduleTemplate template) {
		List<DayBlockResponse> blocks = blockRepository.findByTemplateOrdered(template).stream()
				.map(blockMapper::toResponse)
				.toList();
		return templateMapper.toResponse(template, blocks);
	}

	private AcademicYear loadYear(UUID yearUuid) {
		return yearRepository.findByPublicUuid(yearUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearUuid));
	}

	private DayScheduleTemplate loadTemplate(UUID uuid) {
		return templateRepository.findByPublicUuid(uuid)
				.orElseThrow(() -> new ResourceNotFoundException("DayScheduleTemplate", uuid));
	}

	private DayScheduleBlock loadBlock(UUID uuid) {
		return blockRepository.findByPublicUuid(uuid)
				.orElseThrow(() -> new ResourceNotFoundException("DayScheduleBlock", uuid));
	}

	private static void validateTimeRange(LocalTime start, LocalTime end) {
		if (start == null || end == null) {
			throw new BadRequestException("DAY_BLOCK_TIME_REQUIRED",
					"startTime and endTime are required");
		}
		if (!end.isAfter(start)) {
			throw new BadRequestException("DAY_BLOCK_TIME_INVERTED",
					"endTime must be strictly after startTime");
		}
	}
}
