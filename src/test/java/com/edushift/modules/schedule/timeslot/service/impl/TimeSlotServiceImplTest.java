package com.edushift.modules.schedule.timeslot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.schedule.timeslot.dto.CreateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.dto.ScheduleSlotItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotListItem;
import com.edushift.modules.schedule.timeslot.dto.TimeSlotResponse;
import com.edushift.modules.schedule.timeslot.dto.UpdateTimeSlotRequest;
import com.edushift.modules.schedule.timeslot.entity.TimeSlot;
import com.edushift.modules.schedule.timeslot.mapper.TimeSlotMapper;
import com.edushift.modules.schedule.timeslot.repository.TimeSlotRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TimeSlotServiceImpl} (Sprint 5A — BE-5A.3).
 *
 * <p>Covers the four documented error codes (TIME_SLOT_DATE_INVERTED,
 * TIME_SLOT_OVERLAP, ASSIGNMENT_NOT_ACTIVE, RESOURCE_NOT_FOUND), plus
 * happy paths for CRUD and the two reverse views.</p>
 */
@ExtendWith(MockitoExtension.class)
class TimeSlotServiceImplTest {

	@Mock private TimeSlotRepository timeSlotRepository;
	@Mock private TeacherAssignmentRepository assignmentRepository;
	@Mock private TeacherRepository teacherRepository;
	@Mock private SectionRepository sectionRepository;
	@Mock private AcademicPeriodRepository periodRepository;
	@Spy private TimeSlotMapper mapper = new TimeSlotMapper();

	@InjectMocks private TimeSlotServiceImpl service;

	// =========================================================================
	// listSlotsOfAssignment
	// =========================================================================

	@Nested
	@DisplayName("listSlotsOfAssignment")
	class ListSlots {

		@Test
		@DisplayName("returns slots ordered by (day, start)")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot s1 = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), "Aula 12");
			TimeSlot s2 = newSlot(assignment, (short) 1,
					LocalTime.of(9, 0), LocalTime.of(10, 0), null);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(timeSlotRepository.findAllByAssignmentOrdered(assignment))
					.thenReturn(List.of(s1, s2));

			List<TimeSlotListItem> result = service.listSlotsOfAssignment(
					assignment.getPublicUuid());

			assertThat(result).hasSize(2);
			assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
			assertThat(result.get(0).classroom()).isEqualTo("Aula 12");
		}

		@Test
		@DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
		void unknownAssignment() {
			UUID anyUuid = UUID.randomUUID();
			when(assignmentRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listSlotsOfAssignment(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createSlot
	// =========================================================================

	@Nested
	@DisplayName("createSlot")
	class CreateSlot {

		@Test
		@DisplayName("happy path with no overlap")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(true);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(timeSlotRepository.findOverlapping(eq(assignment), eq((short) 1),
					eq(LocalTime.of(8, 0)), eq(LocalTime.of(9, 0)), isNull()))
					.thenReturn(List.of());
			when(timeSlotRepository.saveAndFlush(any())).thenAnswer(inv -> {
				TimeSlot s = inv.getArgument(0);
				setField(s, "publicUuid", UUID.randomUUID());
				setField(s, "id", UUID.randomUUID());
				return s;
			});

			TimeSlotResponse response = service.createSlot(
					assignment.getPublicUuid(),
					new CreateTimeSlotRequest((short) 1,
							LocalTime.of(8, 0), LocalTime.of(9, 0), "Aula 12"));

			assertThat(response.dayOfWeek()).isEqualTo((short) 1);
			assertThat(response.startTime()).isEqualTo(LocalTime.of(8, 0));
			assertThat(response.classroom()).isEqualTo("Aula 12");
		}

		@Test
		@DisplayName("endTime <= startTime → 400 TIME_SLOT_DATE_INVERTED")
		void datesInverted() {
			TeacherAssignment assignment = newAssignment(true);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));

			assertThatThrownBy(() -> service.createSlot(assignment.getPublicUuid(),
					new CreateTimeSlotRequest((short) 1,
							LocalTime.of(9, 0), LocalTime.of(8, 0), null)))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("must be strictly after");
			verify(timeSlotRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("equal endTime/startTime → 400 TIME_SLOT_DATE_INVERTED")
		void equalTimes() {
			TeacherAssignment assignment = newAssignment(true);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));

			assertThatThrownBy(() -> service.createSlot(assignment.getPublicUuid(),
					new CreateTimeSlotRequest((short) 1,
							LocalTime.of(8, 0), LocalTime.of(8, 0), null)))
					.isInstanceOf(BadRequestException.class);
		}

		@Test
		@DisplayName("overlap with another slot → 409 TIME_SLOT_OVERLAP")
		void overlap() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot existing = newSlot(assignment, (short) 1,
					LocalTime.of(8, 30), LocalTime.of(9, 30), null);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(timeSlotRepository.findOverlapping(eq(assignment), eq((short) 1),
					any(LocalTime.class), any(LocalTime.class), isNull()))
					.thenReturn(List.of(existing));

			assertThatThrownBy(() -> service.createSlot(assignment.getPublicUuid(),
					new CreateTimeSlotRequest((short) 1,
							LocalTime.of(8, 0), LocalTime.of(9, 0), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("overlaps");
			verify(timeSlotRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("soft-ended assignment → 409 ASSIGNMENT_NOT_ACTIVE")
		void assignmentSoftEnded() {
			TeacherAssignment assignment = newAssignment(false);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));

			assertThatThrownBy(() -> service.createSlot(assignment.getPublicUuid(),
					new CreateTimeSlotRequest((short) 1,
							LocalTime.of(8, 0), LocalTime.of(9, 0), null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("soft-ended");
		}
	}

	// =========================================================================
	// updateSlot
	// =========================================================================

	@Nested
	@DisplayName("updateSlot")
	class UpdateSlot {

		@Test
		@DisplayName("happy path with partial-merge")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), "Aula 12");
			when(timeSlotRepository.findByPublicUuid(slot.getPublicUuid()))
					.thenReturn(Optional.of(slot));
			when(timeSlotRepository.findOverlapping(any(), any(),
					any(LocalTime.class), any(LocalTime.class), eq(slot.getId())))
					.thenReturn(List.of());
			when(timeSlotRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			TimeSlotResponse response = service.updateSlot(slot.getPublicUuid(),
					new UpdateTimeSlotRequest(null, null, null, "Aula 99"));

			assertThat(response.classroom()).isEqualTo("Aula 99");
			assertThat(response.startTime()).isEqualTo(LocalTime.of(8, 0));
		}

		@Test
		@DisplayName("empty patch returns current state without writing")
		void emptyPatchIsNoop() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), null);
			when(timeSlotRepository.findByPublicUuid(slot.getPublicUuid()))
					.thenReturn(Optional.of(slot));

			TimeSlotResponse response = service.updateSlot(slot.getPublicUuid(),
					new UpdateTimeSlotRequest(null, null, null, null));

			assertThat(response.startTime()).isEqualTo(LocalTime.of(8, 0));
			verify(timeSlotRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("post-merge endTime <= startTime → 400 TIME_SLOT_DATE_INVERTED")
		void postMergeDatesInverted() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), null);
			when(timeSlotRepository.findByPublicUuid(slot.getPublicUuid()))
					.thenReturn(Optional.of(slot));

			// Patch sets endTime before existing startTime
			assertThatThrownBy(() -> service.updateSlot(slot.getPublicUuid(),
					new UpdateTimeSlotRequest(null, null, LocalTime.of(7, 0), null)))
					.isInstanceOf(BadRequestException.class);
		}

		@Test
		@DisplayName("update of slot whose assignment is soft-ended → 409 ASSIGNMENT_NOT_ACTIVE")
		void assignmentSoftEnded() {
			TeacherAssignment assignment = newAssignment(false);
			TimeSlot slot = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), null);
			when(timeSlotRepository.findByPublicUuid(slot.getPublicUuid()))
					.thenReturn(Optional.of(slot));

			assertThatThrownBy(() -> service.updateSlot(slot.getPublicUuid(),
					new UpdateTimeSlotRequest(null, null, null, "Aula 99")))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("soft-ended");
		}
	}

	// =========================================================================
	// deleteSlot
	// =========================================================================

	@Nested
	@DisplayName("deleteSlot")
	class DeleteSlot {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 1,
					LocalTime.of(8, 0), LocalTime.of(9, 0), null);
			when(timeSlotRepository.findByPublicUuid(slot.getPublicUuid()))
					.thenReturn(Optional.of(slot));

			service.deleteSlot(slot.getPublicUuid());

			verify(timeSlotRepository).delete(slot);
		}

		@Test
		@DisplayName("unknown slot → 404 RESOURCE_NOT_FOUND")
		void unknownSlot() {
			UUID anyUuid = UUID.randomUUID();
			when(timeSlotRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteSlot(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// Reverse views
	// =========================================================================

	@Nested
	@DisplayName("getTeacherSchedule")
	class TeacherSchedule {

		@Test
		@DisplayName("returns flat slots across all active assignments of teacher")
		void happyPath() {
			Teacher teacher = newTeacher();
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 2,
					LocalTime.of(10, 0), LocalTime.of(11, 0), "Aula 5");

			when(teacherRepository.findByPublicUuid(teacher.getPublicUuid()))
					.thenReturn(Optional.of(teacher));
			when(assignmentRepository.findAllByTeacher(teacher, null, true))
					.thenReturn(List.of(assignment));
			when(timeSlotRepository.findAllByAssignmentInOrdered(List.of(assignment)))
					.thenReturn(List.of(slot));

			List<ScheduleSlotItem> result = service.getTeacherSchedule(
					teacher.getPublicUuid(), null);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).dayOfWeek()).isEqualTo((short) 2);
			assertThat(result.get(0).teacher()).as(
					"teacher block is null on teacher-centric view").isNull();
			assertThat(result.get(0).section()).isNotNull();
		}

		@Test
		@DisplayName("teacher with no active assignments → empty list (no extra fetch)")
		void emptyTeacher() {
			Teacher teacher = newTeacher();
			when(teacherRepository.findByPublicUuid(teacher.getPublicUuid()))
					.thenReturn(Optional.of(teacher));
			when(assignmentRepository.findAllByTeacher(teacher, null, true))
					.thenReturn(List.of());

			List<ScheduleSlotItem> result = service.getTeacherSchedule(
					teacher.getPublicUuid(), null);

			assertThat(result).isEmpty();
			verify(timeSlotRepository, never()).findAllByAssignmentInOrdered(any());
		}

		@Test
		@DisplayName("unknown teacher → 404")
		void unknownTeacher() {
			UUID anyUuid = UUID.randomUUID();
			when(teacherRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getTeacherSchedule(anyUuid, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("getSectionSchedule")
	class SectionSchedule {

		@Test
		@DisplayName("returns flat slots for every active assignment of section")
		void happyPath() {
			Section section = newSection();
			TeacherAssignment assignment = newAssignment(true);
			TimeSlot slot = newSlot(assignment, (short) 3,
					LocalTime.of(11, 0), LocalTime.of(12, 0), null);

			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));
			when(assignmentRepository.findAllBySectionActive(section, null))
					.thenReturn(List.of(assignment));
			when(timeSlotRepository.findAllByAssignmentInOrdered(List.of(assignment)))
					.thenReturn(List.of(slot));

			List<ScheduleSlotItem> result = service.getSectionSchedule(
					section.getPublicUuid(), null);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).dayOfWeek()).isEqualTo((short) 3);
			assertThat(result.get(0).section()).as(
					"section block is null on section-centric view").isNull();
			assertThat(result.get(0).teacher()).isNotNull();
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static TeacherAssignment newAssignment(boolean active) {
		TeacherAssignment a = new TeacherAssignment();
		a.setTeacher(newTeacher());
		a.setSection(newSection());
		a.setCourse(newCourse());
		a.setAssignedAt(Instant.now());
		if (!active) {
			a.setUnassignedAt(Instant.now());
		}
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "id", UUID.randomUUID());
		return a;
	}

	private static Teacher newTeacher() {
		Teacher t = new Teacher();
		t.setFirstName("María");
		t.setLastName("García");
		setField(t, "publicUuid", UUID.randomUUID());
		setField(t, "id", UUID.randomUUID());
		return t;
	}

	private static Section newSection() {
		Section s = new Section();
		s.setName("1ro A");
		setField(s, "publicUuid", UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static Course newCourse() {
		Course c = new Course();
		c.setCode("MAT");
		c.setName("Matemática");
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static TimeSlot newSlot(TeacherAssignment assignment, Short day,
			LocalTime start, LocalTime end, String classroom) {
		TimeSlot s = new TimeSlot();
		s.setTeacherAssignment(assignment);
		s.setDayOfWeek(day);
		s.setStartTime(start);
		s.setEndTime(end);
		s.setClassroom(classroom);
		setField(s, "publicUuid", UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static void setField(Object target, String name, Object value) {
		try {
			Field f = findField(target.getClass(), name);
			f.setAccessible(true);
			f.set(target, value);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(name);
			}
			catch (NoSuchFieldException ignore) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	@SuppressWarnings("unused")
	private AcademicPeriod ignore() {
		return null;
	}
}
