package com.edushift.modules.schedule.daytemplate.service;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.schedule.daytemplate.dto.ApplyDayPlanRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayBlockResponse;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.NonTeachingBlockItem;
import com.edushift.modules.schedule.daytemplate.dto.SuggestedPeriodItem;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayBlockRequest;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DayScheduleTemplateService {

	List<DayTemplateResponse> listByYear(UUID yearUuid);

	DayTemplateResponse get(UUID templateUuid);

	DayTemplateResponse create(CreateDayTemplateRequest request);

	DayTemplateResponse update(UUID templateUuid, UpdateDayTemplateRequest request);

	void delete(UUID templateUuid);

	DayBlockResponse addBlock(UUID templateUuid, CreateDayBlockRequest request);

	DayBlockResponse updateBlock(UUID blockUuid, UpdateDayBlockRequest request);

	void deleteBlock(UUID blockUuid);

	List<DayTemplateResponse> seedDefaultTemplatesForYear(UUID yearUuid);

	/**
	 * Grade-specific template for the section's year+level+grade, else
	 * the level default (grade null).
	 */
	Optional<DayScheduleTemplate> resolveTemplateForSection(Section section);

	List<NonTeachingBlockItem> listHardNonTeachingBlocksForSection(
			Section section, Short dayOfWeek);

	void seedDefaultTemplatesForYear(AcademicYear year);

	/**
	 * Applies entrada/salida/periodMinutes and regenerates RECESS/LUNCH
	 * (keeps ASSEMBLY/GUIDANCE/SPECIALIST_RESERVED). ADR-SCH-12.
	 */
	DayTemplateResponse applyDayPlan(UUID templateUuid, ApplyDayPlanRequest request);

	/** Suggested academic periods for a resolved section template. */
	List<SuggestedPeriodItem> listSuggestedPeriodsForSection(Section section);
}
