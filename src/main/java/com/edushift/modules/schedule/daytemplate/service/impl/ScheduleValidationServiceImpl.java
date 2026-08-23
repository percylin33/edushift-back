package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.academic.levelgrade.entity.TeachingMode;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.schedule.daytemplate.dto.ScheduleWarningItem;
import com.edushift.modules.schedule.daytemplate.entity.DayBlockType;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleBlock;
import com.edushift.modules.schedule.daytemplate.entity.DayScheduleTemplate;
import com.edushift.modules.schedule.daytemplate.repository.DayScheduleBlockRepository;
import com.edushift.modules.schedule.daytemplate.service.DayScheduleTemplateService;
import com.edushift.modules.schedule.daytemplate.service.ScheduleValidationService;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleValidationServiceImpl implements ScheduleValidationService {

	private final AcademicYearRepository yearRepository;
	private final SectionRepository sectionRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final DayScheduleTemplateService templateService;
	private final DayScheduleBlockRepository blockRepository;
	private final TimeSlotRepository timeSlotRepository;

	@Override
	@Transactional(readOnly = true)
	public List<ScheduleWarningItem> listWarnings(UUID yearUuid) {
		List<AcademicYear> years;
		if (yearUuid != null) {
			AcademicYear year = yearRepository.findByPublicUuid(yearUuid)
					.orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearUuid));
			years = List.of(year);
		} else {
			years = yearRepository.findAllByOrderByStartDateDesc();
		}

		List<ScheduleWarningItem> warnings = new ArrayList<>();
		for (AcademicYear year : years) {
			List<Section> sections = sectionRepository
					.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(year);
			for (Section section : sections) {
				collectMonoMultiTeacher(section, warnings);
				collectMissingHomeroom(section, warnings);
				collectHomeroomOverSpecialist(section, warnings);
			}
		}
		return warnings;
	}

	private void collectMonoMultiTeacher(Section section, List<ScheduleWarningItem> warnings) {
		if (section.getGrade() == null) {
			return;
		}
		TeachingMode mode = section.getGrade().getTeachingMode();
		if (mode != TeachingMode.MONODOCENTE) {
			return;
		}
		List<TeacherAssignment> assignments = assignmentRepository
				.findAllBySectionActive(section, null);
		Set<UUID> teacherUuids = new HashSet<>();
		for (TeacherAssignment a : assignments) {
			if (a.getTeacher() != null) {
				teacherUuids.add(a.getTeacher().getPublicUuid());
			}
		}
		if (teacherUuids.size() > 1) {
			warnings.add(ScheduleWarningItem.warn(
					"MONO_MULTI_TEACHER",
					"Grado monodocente con más de un docente distinto en asignaciones activas de la sección.",
					section.getPublicUuid(),
					section.getGrade().getPublicUuid(),
					null));
		}
	}

	private void collectMissingHomeroom(Section section, List<ScheduleWarningItem> warnings) {
		if (section.getGrade() == null) {
			return;
		}
		TeachingMode mode = section.getGrade().getTeachingMode();
		if (mode != TeachingMode.POLIDOCENTE && mode != TeachingMode.MIXTO) {
			return;
		}
		if (section.getHomeroomTeacher() == null) {
			warnings.add(ScheduleWarningItem.warn(
					"MISSING_HOMEROOM",
					"Sección polidocente/mixta sin profesor de aula (homeroom) asignado.",
					section.getPublicUuid(),
					section.getGrade().getPublicUuid(),
					null));
		}
	}

	private void collectHomeroomOverSpecialist(Section section, List<ScheduleWarningItem> warnings) {
		Teacher homeroom = section.getHomeroomTeacher();
		if (homeroom == null) {
			return;
		}
		Optional<DayScheduleTemplate> templateOpt = templateService.resolveTemplateForSection(section);
		if (templateOpt.isEmpty()) {
			return;
		}
		List<DayScheduleBlock> specialistBlocks = blockRepository
				.findByTemplateOrdered(templateOpt.get()).stream()
				.filter(b -> b.getBlockType() == DayBlockType.SPECIALIST_RESERVED)
				.toList();
		if (specialistBlocks.isEmpty()) {
			return;
		}

		List<TeacherAssignment> assignments = assignmentRepository
				.findAllBySectionActive(section, null).stream()
				.filter(a -> a.getTeacher() != null
						&& a.getTeacher().getPublicUuid().equals(homeroom.getPublicUuid()))
				.toList();
		if (assignments.isEmpty()) {
			return;
		}

		List<TimeSlot> slots = timeSlotRepository.findAllByAssignmentInOrdered(assignments);
		for (TimeSlot slot : slots) {
			for (DayScheduleBlock block : specialistBlocks) {
				if (block.getDayOfWeek() != null
						&& !block.getDayOfWeek().equals(slot.getDayOfWeek())) {
					continue;
				}
				if (overlaps(slot.getStartTime(), slot.getEndTime(),
						block.getStartTime(), block.getEndTime())) {
					warnings.add(ScheduleWarningItem.warn(
							"HOMEROOM_OVER_SPECIALIST",
							"El tutor tiene un slot que se solapa con un bloque SPECIALIST_RESERVED.",
							section.getPublicUuid(),
							section.getGrade() != null ? section.getGrade().getPublicUuid() : null,
							homeroom.getPublicUuid()));
					return;
				}
			}
		}
	}

	private static boolean overlaps(java.time.LocalTime aStart, java.time.LocalTime aEnd,
			java.time.LocalTime bStart, java.time.LocalTime bEnd) {
		return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
	}
}
