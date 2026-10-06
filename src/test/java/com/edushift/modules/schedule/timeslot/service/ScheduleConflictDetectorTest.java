package com.edushift.modules.schedule.timeslot.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.classrooms.repository.ClassroomRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.schedule.daytemplate.service.NonTeachingBlockResolver;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Period-scoped conflict rules (ADR-SCH-13): same weekly pattern across
 * bimestres must not conflict; same period still must.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleConflictDetectorTest {

	@Mock private TimeSlotRepository timeSlotRepository;
	@Mock private ClassroomRepository classroomRepository;
	@Mock private TeacherAssignmentRepository teacherAssignmentRepository;
	@Mock private NonTeachingBlockResolver nonTeachingBlockResolver;

	@InjectMocks private ScheduleConflictDetector detector;

	private final UUID tenantId = UUID.randomUUID();
	private final UUID teacherUuid = UUID.randomUUID();
	private final UUID sectionUuid = UUID.randomUUID();
	private final UUID period1Uuid = UUID.randomUUID();
	private final UUID period2Uuid = UUID.randomUUID();
	private final UUID assignmentUuid = UUID.randomUUID();

	@BeforeEach
	void bindTenant() {
		TenantContext.set(tenantId);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Nested
	@DisplayName("teacher dimension")
	class TeacherDimension {

		@Test
		@DisplayName("same period + overlapping window → SCHEDULE_CONFLICT")
		void samePeriodOverlaps() {
			TeacherAssignment assignment = assignmentFor(period1Uuid);
			when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
					.thenReturn(Optional.of(assignment));

			TimeSlot existing = slot(LocalTime.of(8, 0), LocalTime.of(9, 30));
			when(timeSlotRepository.findByTenantIdAndTeacherPeriodAndDay(
					eq(tenantId), eq(teacherUuid), eq(period1Uuid), eq((short) 1)))
					.thenReturn(List.of(existing));

			assertThatThrownBy(() -> detector.assertNoConflicts(
					assignmentUuid, null, null, (short) 1,
					LocalTime.of(8, 45), LocalTime.of(9, 30), null))
					.isInstanceOf(ScheduleConflictException.class)
					.hasMessageContaining("08:00");
		}

		@Test
		@DisplayName("queries are scoped to the assignment period (year replicas OK)")
		void scopesQueryToPeriod() {
			TeacherAssignment assignment = assignmentFor(period2Uuid);
			when(teacherAssignmentRepository.findByPublicUuid(assignmentUuid))
					.thenReturn(Optional.of(assignment));
			when(timeSlotRepository.findByTenantIdAndTeacherPeriodAndDay(
					eq(tenantId), eq(teacherUuid), eq(period2Uuid), eq((short) 1)))
					.thenReturn(List.of());
			when(timeSlotRepository.findByTenantIdAndSectionPeriodAndDay(
					eq(tenantId), eq(sectionUuid), eq(period2Uuid), eq((short) 1)))
					.thenReturn(List.of());

			assertThatCode(() -> detector.assertNoConflicts(
					assignmentUuid, null, null, (short) 1,
					LocalTime.of(8, 45), LocalTime.of(9, 30), null))
					.doesNotThrowAnyException();

			verify(timeSlotRepository).findByTenantIdAndTeacherPeriodAndDay(
					tenantId, teacherUuid, period2Uuid, (short) 1);
			verify(timeSlotRepository, never()).findByTenantIdAndTeacherPeriodAndDay(
					eq(tenantId), eq(teacherUuid), eq(period1Uuid), any());
			verify(nonTeachingBlockResolver).assertNoOverlapWithRecess(
					eq(assignment.getSection()), eq((short) 1),
					eq(LocalTime.of(8, 45)), eq(LocalTime.of(9, 30)));
		}
	}

	private TeacherAssignment assignmentFor(UUID periodUuid) {
		Teacher teacher = new Teacher();
		setField(teacher, "publicUuid", teacherUuid);

		Section section = new Section();
		setField(section, "publicUuid", sectionUuid);

		AcademicPeriod period = new AcademicPeriod();
		setField(period, "publicUuid", periodUuid);

		TeacherAssignment assignment = new TeacherAssignment();
		setField(assignment, "publicUuid", assignmentUuid);
		assignment.setTeacher(teacher);
		assignment.setSection(section);
		assignment.setAcademicPeriod(period);
		return assignment;
	}

	private TimeSlot slot(LocalTime start, LocalTime end) {
		TimeSlot slot = new TimeSlot();
		setField(slot, "publicUuid", UUID.randomUUID());
		slot.setDayOfWeek((short) 1);
		slot.setStartTime(start);
		slot.setEndTime(end);
		return slot;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = target.getClass().getDeclaredField(name);
			f.setAccessible(true);
			f.set(target, value);
		} catch (ReflectiveOperationException ex) {
			// publicUuid may live on a superclass
			Class<?> type = target.getClass().getSuperclass();
			while (type != null) {
				try {
					Field f = type.getDeclaredField(name);
					f.setAccessible(true);
					f.set(target, value);
					return;
				} catch (NoSuchFieldException ignored) {
					type = type.getSuperclass();
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
			throw new IllegalStateException(ex);
		}
	}
}
