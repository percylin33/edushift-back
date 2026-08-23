package com.edushift.modules.schedule.daytemplate.mapper;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.schedule.daytemplate.dto.CreateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.dto.DayBlockResponse;
import com.edushift.modules.schedule.daytemplate.dto.DayTemplateResponse;
import com.edushift.modules.schedule.daytemplate.dto.UpdateDayTemplateRequest;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DayScheduleTemplateMapper {

	private final DayScheduleBlockMapper blockMapper;

	public DayScheduleTemplateMapper(DayScheduleBlockMapper blockMapper) {
		this.blockMapper = blockMapper;
	}

	public DayTemplateResponse toResponse(DayScheduleTemplate template, List<DayBlockResponse> blocks) {
		AcademicYear year = template.getAcademicYear();
		AcademicLevel level = template.getAcademicLevel();
		Grade grade = template.getGrade();
		return new DayTemplateResponse(
				template.getPublicUuid(),
				year != null ? year.getPublicUuid() : null,
				level != null ? level.getPublicUuid() : null,
				level != null ? level.getCode() : null,
				grade != null ? grade.getPublicUuid() : null,
				grade != null ? grade.getName() : null,
				template.getShift(),
				template.getName(),
				template.getRecessShareGroup(),
				template.getDayStart(),
				template.getDayEnd(),
				template.getPeriodMinutes(),
				blocks == null ? List.of() : blocks
		);
	}

	public DayScheduleTemplate fromCreate(CreateDayTemplateRequest request,
			AcademicYear year, AcademicLevel level, Grade grade) {
		DayScheduleTemplate template = new DayScheduleTemplate();
		template.setAcademicYear(year);
		template.setAcademicLevel(level);
		template.setGrade(grade);
		template.setShift(blankToNull(request.shift()));
		template.setName(request.name());
		template.setRecessShareGroup(blankToNull(request.recessShareGroup()));
		return template;
	}

	public void applyUpdate(UpdateDayTemplateRequest patch, DayScheduleTemplate template) {
		if (patch == null) {
			return;
		}
		if (patch.shift() != null) {
			template.setShift(blankToNull(patch.shift()));
		}
		if (patch.name() != null && !patch.name().isBlank()) {
			template.setName(patch.name().trim());
		}
		if (patch.recessShareGroup() != null) {
			template.setRecessShareGroup(blankToNull(patch.recessShareGroup()));
		}
		if (patch.dayStart() != null) {
			template.setDayStart(patch.dayStart());
		}
		if (patch.dayEnd() != null) {
			template.setDayEnd(patch.dayEnd());
		}
		if (patch.periodMinutes() != null) {
			template.setPeriodMinutes(patch.periodMinutes());
		}
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
