package com.edushift.modules.academic.course.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.dto.CourseListItem;
import com.edushift.modules.academic.course.dto.CourseResponse;
import com.edushift.modules.academic.course.dto.CreateCourseRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseLevelsRequest;
import com.edushift.modules.academic.course.dto.UpdateCourseRequest;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.entity.CourseLevel;
import com.edushift.modules.academic.course.mapper.CourseMapper;
import com.edushift.modules.academic.course.repository.CourseLevelRepository;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

	@Mock private CourseRepository courseRepository;
	@Mock private CourseLevelRepository courseLevelRepository;
	@Mock private AcademicLevelRepository levelRepository;
	@Mock private TeacherAssignmentRepository teacherAssignmentRepository;
	@Spy private CourseMapper mapper = new CourseMapper();

	@InjectMocks private CourseServiceImpl service;

	// =========================================================================
	// listCourses
	// =========================================================================

	@Nested
	@DisplayName("listCourses")
	class List_ {

		@Test
		@DisplayName("no filters → all courses sorted")
		void happyPath() {
			Course mat = newCourse("MAT", "Matematica");
			Course com = newCourse("COMU", "Comunicacion");
			when(courseRepository.findAllSorted()).thenReturn(List.of(com, mat));
			when(courseLevelRepository.findAllByCourses(List.of(com, mat)))
					.thenReturn(List.of());

			List<CourseListItem> result = service.listCourses(null, null);

			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("?levelId → uses level filter query")
		void filterByLevel() {
			UUID levelId = UUID.randomUUID();
			when(courseRepository.findAllByLevelPublicUuid(levelId))
					.thenReturn(List.of(newCourse("MAT", "Matematica")));
			when(courseLevelRepository.findAllByCourses(any())).thenReturn(List.of());

			List<CourseListItem> result = service.listCourses(levelId, null);

			assertThat(result).hasSize(1);
			verify(courseRepository).findAllByLevelPublicUuid(levelId);
		}

		@Test
		@DisplayName("?isActive=true → uses active query")
		void filterByActive() {
			when(courseRepository.findAllByIsActiveSorted(true))
					.thenReturn(List.of(newCourse("MAT", "Matematica")));
			when(courseLevelRepository.findAllByCourses(any())).thenReturn(List.of());

			service.listCourses(null, true);

			verify(courseRepository).findAllByIsActiveSorted(true);
		}

		@Test
		@DisplayName("empty list → empty result without batch fetch")
		void emptyResult() {
			when(courseRepository.findAllSorted()).thenReturn(List.of());

			List<CourseListItem> result = service.listCourses(null, null);

			assertThat(result).isEmpty();
			verify(courseLevelRepository, never()).findAllByCourses(any());
		}
	}

	// =========================================================================
	// getCourse
	// =========================================================================

	@Nested
	@DisplayName("getCourse")
	class Get {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			Course course = newCourse("MAT", "Matematica");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

			CourseResponse response = service.getCourse(course.getPublicUuid());

			assertThat(response.code()).isEqualTo("MAT");
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(courseRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getCourse(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createCourse
	// =========================================================================

	@Nested
	@DisplayName("createCourse")
	class Create {

		@Test
		@DisplayName("happy path — code free, valid levels, persists course + pivot rows")
		void happyPath() {
			AcademicLevel primaria = newLevel("PRIMARIA", "Primaria", 2);
			AcademicLevel secundaria = newLevel("SECUNDARIA", "Secundaria", 3);
			when(levelRepository.findByPublicUuid(primaria.getPublicUuid()))
					.thenReturn(Optional.of(primaria));
			when(levelRepository.findByPublicUuid(secundaria.getPublicUuid()))
					.thenReturn(Optional.of(secundaria));
			when(courseRepository.findByCodeIgnoreCase("MAT")).thenReturn(Optional.empty());
			when(courseRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Course c = inv.getArgument(0);
				setField(c, "publicUuid", UUID.randomUUID());
				setField(c, "id", UUID.randomUUID());
				return c;
			});
			when(courseLevelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			CourseResponse response = service.createCourse(new CreateCourseRequest(
					"MAT", "Matematica", null, 4, 5, true,
					List.of(primaria.getPublicUuid(), secundaria.getPublicUuid())));

			assertThat(response.code()).isEqualTo("MAT");
			verify(courseLevelRepository, atLeastOnce()).save(any());
		}

		@Test
		@DisplayName("code already taken → 409 COURSE_CODE_TAKEN")
		void codeTaken() {
			Course existing = newCourse("MAT", "Existing");
			when(courseRepository.findByCodeIgnoreCase("MAT")).thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createCourse(new CreateCourseRequest(
					"MAT", "Matematica", null, null, null, null,
					List.of(UUID.randomUUID()))))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("MAT");
		}

		@Test
		@DisplayName("level not found → 404 (anti-enumeration for cross-tenant)")
		void levelNotFound() {
			when(courseRepository.findByCodeIgnoreCase("MAT")).thenReturn(Optional.empty());
			UUID strangerLevel = UUID.randomUUID();
			when(levelRepository.findByPublicUuid(strangerLevel)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createCourse(new CreateCourseRequest(
					"MAT", "Matematica", null, null, null, null, List.of(strangerLevel))))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("empty levelPublicUuids → 422 COURSE_NEEDS_AT_LEAST_ONE_LEVEL")
		void noLevels() {
			when(courseRepository.findByCodeIgnoreCase("MAT")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createCourse(new CreateCourseRequest(
					"MAT", "Matematica", null, null, null, null, List.of())))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("at least one");
		}

		@Test
		@DisplayName("DB unique violation on course → 409 COURSE_CODE_TAKEN")
		void raceCondition() {
			AcademicLevel primaria = newLevel("PRIMARIA", "Primaria", 2);
			when(levelRepository.findByPublicUuid(primaria.getPublicUuid()))
					.thenReturn(Optional.of(primaria));
			when(courseRepository.findByCodeIgnoreCase("MAT")).thenReturn(Optional.empty());
			when(courseRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_courses_tenant_code_active"));

			assertThatThrownBy(() -> service.createCourse(new CreateCourseRequest(
					"MAT", "Matematica", null, null, null, null,
					List.of(primaria.getPublicUuid()))))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// updateCourse
	// =========================================================================

	@Nested
	@DisplayName("updateCourse")
	class Update {

		@Test
		@DisplayName("happy path — partial merge")
		void happyPath() {
			Course course = newCourse("MAT", "Matematica");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(courseRepository.saveAndFlush(course)).thenReturn(course);
			when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

			CourseResponse response = service.updateCourse(course.getPublicUuid(),
					new UpdateCourseRequest(null, "Matemática Avanzada", null, null, null, false));

			assertThat(response.name()).isEqualTo("Matemática Avanzada");
			assertThat(response.isActive()).isFalse();
		}

		@Test
		@DisplayName("empty patch → no save")
		void emptyPatch() {
			Course course = newCourse("MAT", "Matematica");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

			service.updateCourse(course.getPublicUuid(),
					new UpdateCourseRequest(null, null, null, null, null, null));

			verify(courseRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("description blank → cleared")
		void clearDescription() {
			Course course = newCourse("MAT", "Matematica");
			course.setDescription("legacy");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(courseRepository.saveAndFlush(course)).thenReturn(course);
			when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of());

			service.updateCourse(course.getPublicUuid(),
					new UpdateCourseRequest(null, null, "  ", null, null, null));

			assertThat(course.getDescription()).isNull();
		}
	}

	// =========================================================================
	// replaceLevels
	// =========================================================================

	@Nested
	@DisplayName("replaceLevels")
	class ReplaceLevels {

		@Test
		@DisplayName("replaces atomically by computing diff")
		void replaceWithDiff() {
			Course course = newCourse("MAT", "Matematica");
			AcademicLevel primaria = newLevel("PRIMARIA", "Primaria", 2);
			AcademicLevel secundaria = newLevel("SECUNDARIA", "Secundaria", 3);
			AcademicLevel inicial = newLevel("INICIAL", "Inicial", 1);

			CourseLevel linkPrim = newLink(course, primaria);
			CourseLevel linkSec = newLink(course, secundaria);

			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			// First call returns the current set, second call (after diff) returns final
			when(courseLevelRepository.findAllByCourse(course))
					.thenReturn(List.of(linkPrim, linkSec))
					.thenReturn(List.of(linkPrim, newLink(course, inicial)));
			when(levelRepository.findByPublicUuid(inicial.getPublicUuid()))
					.thenReturn(Optional.of(inicial));
			when(levelRepository.findByPublicUuid(primaria.getPublicUuid()))
					.thenReturn(Optional.of(primaria));
			when(courseLevelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// Target = [INICIAL, PRIMARIA] -> remove SECUNDARIA, add INICIAL.
			service.replaceLevels(course.getPublicUuid(),
					new UpdateCourseLevelsRequest(
							List.of(inicial.getPublicUuid(), primaria.getPublicUuid())));

			// Removed exactly the rows that left the set
			verify(courseLevelRepository).deleteAll(List.of(linkSec));
			// Added exactly the new ones
			verify(courseLevelRepository, atLeastOnce()).save(any());
		}

		@Test
		@DisplayName("empty levels → 422 COURSE_NEEDS_AT_LEAST_ONE_LEVEL")
		void emptyLevelsRejected() {
			Course course = newCourse("MAT", "Matematica");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));

			assertThatThrownBy(() -> service.replaceLevels(course.getPublicUuid(),
					new UpdateCourseLevelsRequest(List.of())))
					.isInstanceOf(BusinessException.class);
		}

		@Test
		@DisplayName("level not found → 404")
		void levelNotFound() {
			Course course = newCourse("MAT", "Matematica");
			UUID strangerLevel = UUID.randomUUID();
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(levelRepository.findByPublicUuid(strangerLevel))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.replaceLevels(course.getPublicUuid(),
					new UpdateCourseLevelsRequest(List.of(strangerLevel))))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// deleteCourse
	// =========================================================================

	@Nested
	@DisplayName("deleteCourse")
	class Delete {

		@Test
		@DisplayName("happy path — cascades soft-delete on pivot rows when no active assignments")
		void happyPath() {
			Course course = newCourse("MAT", "Matematica");
			AcademicLevel primaria = newLevel("PRIMARIA", "Primaria", 2);
			CourseLevel link = newLink(course, primaria);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(teacherAssignmentRepository.existsActiveByCourse(course)).thenReturn(false);
			when(courseLevelRepository.findAllByCourse(course)).thenReturn(List.of(link));

			service.deleteCourse(course.getPublicUuid());

			verify(courseLevelRepository).deleteAll(List.of(link));
			verify(courseRepository).delete(course);
		}

		@Test
		@DisplayName("refused with 409 COURSE_IN_USE_BY_ASSIGNMENTS when active assignments exist")
		void refusedWhenActiveAssignmentsExist() {
			Course course = newCourse("MAT", "Matematica");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(teacherAssignmentRepository.existsActiveByCourse(course)).thenReturn(true);

			assertThatThrownBy(() -> service.deleteCourse(course.getPublicUuid()))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("COURSE_IN_USE_BY_ASSIGNMENTS"));

			verify(courseRepository, never()).delete(any());
		}
	}

	// =========================================================================
	// helpers
	// =========================================================================

	private static Course newCourse(String code, String name) {
		Course c = new Course();
		c.setCode(code);
		c.setName(name);
		c.setIsActive(true);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static AcademicLevel newLevel(String code, String name, int ordinal) {
		AcademicLevel l = new AcademicLevel();
		l.setCode(code);
		l.setName(name);
		l.setOrdinal(ordinal);
		setField(l, "publicUuid", UUID.randomUUID());
		setField(l, "id", UUID.randomUUID());
		return l;
	}

	private static CourseLevel newLink(Course c, AcademicLevel l) {
		CourseLevel link = new CourseLevel();
		link.setCourse(c);
		link.setLevel(l);
		setField(link, "publicUuid", UUID.randomUUID());
		setField(link, "id", UUID.randomUUID());
		return link;
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
}
