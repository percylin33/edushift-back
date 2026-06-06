package com.edushift.modules.teachers.assignments.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.teachers.assignments.dto.AssignmentResponse;
import com.edushift.modules.teachers.assignments.dto.CreateAssignmentRequest;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.mapper.TeacherAssignmentMapper;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.EmploymentStatus;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherAssignmentService — create + listing + soft-end")
class TeacherAssignmentServiceImplTest {

	@Mock private TeacherAssignmentRepository assignmentRepository;
	@Mock private TeacherRepository teacherRepository;
	@Mock private SectionRepository sectionRepository;
	@Mock private CourseRepository courseRepository;
	@Mock private AcademicPeriodRepository periodRepository;
	@Mock private CourseLevelRepository courseLevelRepository;
	@Spy private TeacherAssignmentMapper mapper = new TeacherAssignmentMapper();

	@InjectMocks private TeacherAssignmentServiceImpl service;

	private Teacher teacher;
	private Section section;
	private Course course;
	private AcademicPeriod period;
	private AcademicYear year;
	private AcademicLevel primariaLevel;

	@BeforeEach
	void setUp() {
		primariaLevel = newLevel("PRIMARIA", "Primaria");
		Grade grade2 = newGrade("2do Primaria", primariaLevel);
		year = newYear("2026");
		section = newSection("A", grade2, year);
		course = newCourse("MAT", "Matematica");
		period = newPeriod(PeriodType.BIMESTRE, 1, "I Bimestre", year);
		teacher = newTeacher("Ada", "Lovelace", EmploymentStatus.ACTIVE);
	}

	// =========================================================================
	// createAssignment
	// =========================================================================

	@Nested
	@DisplayName("createAssignment")
	class Create {

		@Test
		@DisplayName("happy path — saves and returns persisted projection")
		void happyPath() {
			stubAllLookups();
			when(courseLevelRepository.existsByCourseAndLevel(course, primariaLevel)).thenReturn(true);
			when(assignmentRepository.findActiveTuple(teacher, section, course, period))
					.thenReturn(Optional.empty());
			when(assignmentRepository.saveAndFlush(any(TeacherAssignment.class)))
					.thenAnswer(inv -> {
						TeacherAssignment a = inv.getArgument(0);
						setField(a, "id", UUID.randomUUID());
						a.setPublicUuid(UUID.randomUUID());
						setField(a, "createdAt", Instant.now());
						setField(a, "updatedAt", Instant.now());
						return a;
					});

			AssignmentResponse response = service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(),
							"   "));

			assertThat(response).isNotNull();
			assertThat(response.teacherPublicUuid()).isEqualTo(teacher.getPublicUuid());
			assertThat(response.notes()).isNull();
			ArgumentCaptor<TeacherAssignment> captor = ArgumentCaptor.forClass(TeacherAssignment.class);
			verify(assignmentRepository).saveAndFlush(captor.capture());
			TeacherAssignment saved = captor.getValue();
			assertThat(saved.getTeacher()).isSameAs(teacher);
			assertThat(saved.getSection()).isSameAs(section);
			assertThat(saved.getCourse()).isSameAs(course);
			assertThat(saved.getAcademicPeriod()).isSameAs(period);
			assertThat(saved.getAssignedAt()).isNotNull();
			assertThat(saved.getUnassignedAt()).isNull();
		}

		@Test
		@DisplayName("teacher missing → 404 RESOURCE_NOT_FOUND (Teacher)")
		void teacherNotFound() {
			when(teacherRepository.findByPublicUuid(eq(teacher.getPublicUuid())))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(), null)))
					.isInstanceOf(ResourceNotFoundException.class);

			verify(assignmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("RESIGNED teacher → 409 TEACHER_NOT_ACTIVE")
		void teacherNotActive() {
			teacher.setEmploymentStatus(EmploymentStatus.RESIGNED);
			when(teacherRepository.findByPublicUuid(eq(teacher.getPublicUuid())))
					.thenReturn(Optional.of(teacher));
			when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
					.thenReturn(Optional.of(section));
			when(courseRepository.findByPublicUuid(eq(course.getPublicUuid())))
					.thenReturn(Optional.of(course));
			when(periodRepository.findByPublicUuid(eq(period.getPublicUuid())))
					.thenReturn(Optional.of(period));

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(), null)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("TEACHER_NOT_ACTIVE"));
		}

		@Test
		@DisplayName("section.year != period.year → 409 ASSIGNMENT_YEAR_MISMATCH")
		void yearMismatch() {
			AcademicYear otherYear = newYear("2027");
			AcademicPeriod periodInOtherYear = newPeriod(PeriodType.BIMESTRE, 1,
					"I Bimestre 2027", otherYear);
			when(teacherRepository.findByPublicUuid(eq(teacher.getPublicUuid())))
					.thenReturn(Optional.of(teacher));
			when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
					.thenReturn(Optional.of(section));
			when(courseRepository.findByPublicUuid(eq(course.getPublicUuid())))
					.thenReturn(Optional.of(course));
			when(periodRepository.findByPublicUuid(eq(periodInOtherYear.getPublicUuid())))
					.thenReturn(Optional.of(periodInOtherYear));

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							periodInOtherYear.getPublicUuid(), null)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("ASSIGNMENT_YEAR_MISMATCH"));
		}

		@Test
		@DisplayName("course not associated to section's level → 409 COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL")
		void courseNotApplicable() {
			stubAllLookups();
			when(courseLevelRepository.existsByCourseAndLevel(course, primariaLevel))
					.thenReturn(false);

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(), null)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode())
									.isEqualTo("COURSE_NOT_APPLICABLE_TO_SECTION_LEVEL"));
		}

		@Test
		@DisplayName("active duplicate tuple → 409 ASSIGNMENT_ALREADY_ACTIVE")
		void duplicateActive() {
			stubAllLookups();
			when(courseLevelRepository.existsByCourseAndLevel(course, primariaLevel)).thenReturn(true);
			TeacherAssignment existing = new TeacherAssignment();
			existing.setPublicUuid(UUID.randomUUID());
			when(assignmentRepository.findActiveTuple(teacher, section, course, period))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(), null)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("ASSIGNMENT_ALREADY_ACTIVE"));

			verify(assignmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DB integrity race → 409 ASSIGNMENT_ALREADY_ACTIVE")
		void dbRace() {
			stubAllLookups();
			when(courseLevelRepository.existsByCourseAndLevel(course, primariaLevel)).thenReturn(true);
			when(assignmentRepository.findActiveTuple(teacher, section, course, period))
					.thenReturn(Optional.empty());
			when(assignmentRepository.saveAndFlush(any(TeacherAssignment.class)))
					.thenThrow(new DataIntegrityViolationException("dup"));

			assertThatThrownBy(() -> service.createAssignment(teacher.getPublicUuid(),
					new CreateAssignmentRequest(section.getPublicUuid(),
							course.getPublicUuid(),
							period.getPublicUuid(), null)))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("ASSIGNMENT_ALREADY_ACTIVE"));
		}
	}

	// =========================================================================
	// softEnd
	// =========================================================================

	@Nested
	@DisplayName("softEnd")
	class SoftEnd {

		@Test
		@DisplayName("happy path — sets unassignedAt and saves")
		void happyPath() {
			TeacherAssignment a = newAssignment();
			when(assignmentRepository.findByPublicUuid(a.getPublicUuid()))
					.thenReturn(Optional.of(a));
			when(assignmentRepository.saveAndFlush(a)).thenReturn(a);

			service.softEnd(a.getPublicUuid());

			assertThat(a.getUnassignedAt()).isNotNull();
			verify(assignmentRepository).saveAndFlush(a);
		}

		@Test
		@DisplayName("already-ended is idempotent (no save)")
		void idempotent() {
			TeacherAssignment a = newAssignment();
			a.setUnassignedAt(Instant.now().minusSeconds(60));
			when(assignmentRepository.findByPublicUuid(a.getPublicUuid()))
					.thenReturn(Optional.of(a));

			service.softEnd(a.getPublicUuid());

			verify(assignmentRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void notFound() {
			UUID unknown = UUID.randomUUID();
			when(assignmentRepository.findByPublicUuid(unknown))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.softEnd(unknown))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// listForTeacher / listForSection
	// =========================================================================

	@Test
	@DisplayName("listForTeacher — null period filter passes null to repo")
	void listForTeacherNoPeriod() {
		when(teacherRepository.findByPublicUuid(teacher.getPublicUuid()))
				.thenReturn(Optional.of(teacher));
		when(assignmentRepository.findAllByTeacher(teacher, null, true))
				.thenReturn(java.util.List.of(newAssignment()));

		assertThat(service.listForTeacher(teacher.getPublicUuid(), null, true))
				.hasSize(1);
	}

	@Test
	@DisplayName("listForSection — explicit period passed through to repo")
	void listForSectionWithPeriod() {
		when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
				.thenReturn(Optional.of(section));
		when(periodRepository.findByPublicUuid(period.getPublicUuid()))
				.thenReturn(Optional.of(period));
		when(assignmentRepository.findAllBySectionActive(section, period))
				.thenReturn(java.util.List.of(newAssignment()));

		assertThat(service.listForSection(section.getPublicUuid(), period.getPublicUuid()))
				.hasSize(1);
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private void stubAllLookups() {
		when(teacherRepository.findByPublicUuid(eq(teacher.getPublicUuid())))
				.thenReturn(Optional.of(teacher));
		when(sectionRepository.findByPublicUuid(eq(section.getPublicUuid())))
				.thenReturn(Optional.of(section));
		when(courseRepository.findByPublicUuid(eq(course.getPublicUuid())))
				.thenReturn(Optional.of(course));
		when(periodRepository.findByPublicUuid(eq(period.getPublicUuid())))
				.thenReturn(Optional.of(period));
	}

	private TeacherAssignment newAssignment() {
		TeacherAssignment a = new TeacherAssignment();
		a.setPublicUuid(UUID.randomUUID());
		setField(a, "id", UUID.randomUUID());
		a.setTeacher(teacher);
		a.setSection(section);
		a.setCourse(course);
		a.setAcademicPeriod(period);
		a.setAssignedAt(Instant.now());
		setField(a, "createdAt", Instant.now());
		setField(a, "updatedAt", Instant.now());
		return a;
	}

	private static AcademicLevel newLevel(String code, String name) {
		AcademicLevel l = new AcademicLevel();
		l.setCode(code);
		l.setName(name);
		l.setOrdinal(1);
		setField(l, "id", UUID.randomUUID());
		setField(l, "publicUuid", UUID.randomUUID());
		return l;
	}

	private static Grade newGrade(String name, AcademicLevel level) {
		Grade g = new Grade();
		g.setName(name);
		g.setOrdinal(1);
		g.setLevel(level);
		setField(g, "id", UUID.randomUUID());
		setField(g, "publicUuid", UUID.randomUUID());
		return g;
	}

	private static AcademicYear newYear(String name) {
		AcademicYear y = new AcademicYear();
		setField(y, "id", UUID.randomUUID());
		setField(y, "publicUuid", UUID.randomUUID());
		setField(y, "name", name);
		return y;
	}

	private static Section newSection(String name, Grade grade, AcademicYear year) {
		Section s = new Section();
		s.setName(name);
		s.setGrade(grade);
		s.setAcademicYear(year);
		s.setDisplayOrder(1);
		setField(s, "id", UUID.randomUUID());
		s.setPublicUuid(UUID.randomUUID());
		return s;
	}

	private static Course newCourse(String code, String name) {
		Course c = new Course();
		c.setCode(code);
		c.setName(name);
		c.setIsActive(true);
		setField(c, "id", UUID.randomUUID());
		setField(c, "publicUuid", UUID.randomUUID());
		return c;
	}

	private static AcademicPeriod newPeriod(PeriodType type, int ordinal,
			String name, AcademicYear year) {
		AcademicPeriod p = new AcademicPeriod();
		p.setPeriodType(type);
		p.setOrdinal(ordinal);
		p.setName(name);
		p.setAcademicYear(year);
		setField(p, "id", UUID.randomUUID());
		p.setPublicUuid(UUID.randomUUID());
		return p;
	}

	private static Teacher newTeacher(String first, String last, EmploymentStatus status) {
		Teacher t = new Teacher();
		t.setFirstName(first);
		t.setLastName(last);
		t.setEmploymentStatus(status);
		t.setPublicUuid(UUID.randomUUID());
		setField(t, "id", UUID.randomUUID());
		return t;
	}

	private static void setField(Object target, String name, Object value) {
		Class<?> cls = target.getClass();
		while (cls != null) {
			try {
				Field f = cls.getDeclaredField(name);
				f.setAccessible(true);
				f.set(target, value);
				return;
			}
			catch (NoSuchFieldException ignored) {
				cls = cls.getSuperclass();
			}
			catch (IllegalAccessException ex) {
				throw new RuntimeException(ex);
			}
		}
		throw new RuntimeException("Field not found: " + name);
	}
}
