package com.edushift.modules.schedule.daytemplate.service.impl;

import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.schedule.daytemplate.dto.TeacherWorkloadItem;
import com.edushift.modules.schedule.daytemplate.service.TeacherWorkloadService;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherWorkloadServiceImpl implements TeacherWorkloadService {

	private final TimeSlotRepository timeSlotRepository;
	private final AcademicPeriodRepository periodRepository;
	private final TeacherRepository teacherRepository;

	@Override
	@Transactional(readOnly = true)
	public List<TeacherWorkloadItem> listWorkload(UUID periodUuid) {
		requirePeriod(periodUuid);
		List<TimeSlot> slots = timeSlotRepository
				.findActiveByPeriodAndOptionalTeacher(periodUuid, null);
		return aggregate(slots);
	}

	@Override
	@Transactional(readOnly = true)
	public TeacherWorkloadItem getWorkload(UUID teacherUuid, UUID periodUuid) {
		requirePeriod(periodUuid);
		Teacher teacher = teacherRepository.findByPublicUuid(teacherUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Teacher", teacherUuid));
		List<TimeSlot> slots = timeSlotRepository
				.findActiveByPeriodAndOptionalTeacher(periodUuid, teacherUuid);
		List<TeacherWorkloadItem> items = aggregate(slots);
		if (!items.isEmpty()) {
			return items.get(0);
		}
		return new TeacherWorkloadItem(
				teacher.getPublicUuid(),
				teacher.getFirstName(),
				teacher.getLastName(),
				0, 0, 0, 0);
	}

	private void requirePeriod(UUID periodUuid) {
		if (periodUuid == null) {
			throw new BadRequestException("PERIOD_REQUIRED", "periodId is required");
		}
		periodRepository.findByPublicUuid(periodUuid)
				.orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", periodUuid));
	}

	private static List<TeacherWorkloadItem> aggregate(List<TimeSlot> slots) {
		Map<UUID, Acc> byTeacher = new LinkedHashMap<>();
		for (TimeSlot slot : slots) {
			TeacherAssignment assignment = slot.getTeacherAssignment();
			if (assignment == null || assignment.getTeacher() == null) {
				continue;
			}
			Teacher teacher = assignment.getTeacher();
			Acc acc = byTeacher.computeIfAbsent(teacher.getPublicUuid(),
					id -> new Acc(teacher));
			acc.minutes += minutesBetween(slot.getStartTime(), slot.getEndTime());
			acc.slotCount++;
			if (assignment.getSection() != null) {
				acc.sectionUuids.add(assignment.getSection().getPublicUuid());
			}
			if (assignment.getCourse() != null) {
				acc.courseUuids.add(assignment.getCourse().getPublicUuid());
			}
		}
		List<TeacherWorkloadItem> result = new ArrayList<>();
		for (Acc acc : byTeacher.values()) {
			result.add(new TeacherWorkloadItem(
					acc.teacher.getPublicUuid(),
					acc.teacher.getFirstName(),
					acc.teacher.getLastName(),
					acc.minutes,
					acc.slotCount,
					acc.sectionUuids.size(),
					acc.courseUuids.size()));
		}
		return result;
	}

	private static int minutesBetween(LocalTime start, LocalTime end) {
		if (start == null || end == null || !end.isAfter(start)) {
			return 0;
		}
		return (int) Duration.between(start, end).toMinutes();
	}

	private static final class Acc {
		final Teacher teacher;
		int minutes;
		int slotCount;
		final Set<UUID> sectionUuids = new HashSet<>();
		final Set<UUID> courseUuids = new HashSet<>();

		Acc(Teacher teacher) {
			this.teacher = teacher;
		}
	}
}
