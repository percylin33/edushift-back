package com.edushift.modules.academic.competency.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.competency.dto.CompetencyListItem;
import com.edushift.modules.academic.competency.dto.CompetencyReorderRequest;
import com.edushift.modules.academic.competency.dto.CompetencyResponse;
import com.edushift.modules.academic.competency.dto.CreateCompetencyRequest;
import com.edushift.modules.academic.competency.dto.UpdateCompetencyRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CompetencyMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link CompetencyServiceImpl} (Sprint 5A — BE-5A.2).
 *
 * <p>Mirrors {@code UnitServiceImplTest}: covers each error code in the
 * service contract plus the two-pass reorder happy path + DB-collision
 * case. The {@code COMPETENCY_IN_USE_BY_SESSIONS} branch exercises the
 * placeholder ({@code countSessionsByCompetency → 0L}) until BE-5A.4
 * wires up.</p>
 */
@ExtendWith(MockitoExtension.class)
class CompetencyServiceImplTest {

	@Mock private CompetencyRepository competencyRepository;
	@Mock private CapacityRepository capacityRepository;
	@Mock private CourseRepository courseRepository;
	@Spy private CompetencyMapper mapper = new CompetencyMapper();

	@InjectMocks private CompetencyServiceImpl service;

	// =========================================================================
	// listCompetencies
	// =========================================================================

	@Nested
	@DisplayName("listCompetencies")
	class ListCompetencies {

		@Test
		@DisplayName("returns competencies sorted by displayOrder asc with capacity counts")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Competency c1 = newCompetency(course, "MAT_C1", 1);
			Competency c2 = newCompetency(course, "MAT_C2", 2);
			Capacity cap1 = newCapacity(c1, "MAT_C1_CAP1", 1);
			Capacity cap2 = newCapacity(c1, "MAT_C1_CAP2", 2);
			Capacity cap3 = newCapacity(c2, "MAT_C2_CAP1", 1);

			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(c1, c2));
			when(capacityRepository.findAllByCompetencyIn(anyList()))
					.thenReturn(List.of(cap1, cap2, cap3));

			List<CompetencyListItem> result = service.listCompetencies(
					course.getPublicUuid(), null);

			assertThat(result).hasSize(2);
			assertThat(result.get(0).code()).isEqualTo("MAT_C1");
			assertThat(result.get(0).displayOrder()).isEqualTo(1);
			assertThat(result.get(0).capacityCount()).isEqualTo(2L);
			assertThat(result.get(1).code()).isEqualTo("MAT_C2");
			assertThat(result.get(1).capacityCount()).isEqualTo(1L);
		}

		@Test
		@DisplayName("isActive=false narrows to inactive competencies only")
		void filtersByIsActive() {
			Course course = newCourse("MAT", "Matemática");
			Competency active = newCompetency(course, "MAT_C1", 1);
			Competency inactive = newCompetency(course, "MAT_C2", 2);
			inactive.setIsActive(Boolean.FALSE);

			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(active, inactive));
			when(capacityRepository.findAllByCompetencyIn(anyList()))
					.thenReturn(List.of());

			List<CompetencyListItem> result = service.listCompetencies(
					course.getPublicUuid(), Boolean.FALSE);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).code()).isEqualTo("MAT_C2");
		}

		@Test
		@DisplayName("unknown course → 404 RESOURCE_NOT_FOUND")
		void unknownCourse() {
			UUID anyUuid = UUID.randomUUID();
			when(courseRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listCompetencies(anyUuid, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("empty course returns empty list without batch query")
		void emptyCourse() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of());

			List<CompetencyListItem> result = service.listCompetencies(
					course.getPublicUuid(), null);

			assertThat(result).isEmpty();
			verify(capacityRepository, never()).findAllByCompetencyIn(anyList());
		}
	}

	// =========================================================================
	// createCompetency
	// =========================================================================

	@Nested
	@DisplayName("createCompetency")
	class CreateCompetency {

		@Test
		@DisplayName("happy path with explicit displayOrder")
		void happyPathExplicit() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findByCourseAndCodeIgnoreCase(course, "MAT_C1"))
					.thenReturn(Optional.empty());
			when(competencyRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Competency c = inv.getArgument(0);
				setField(c, "publicUuid", UUID.randomUUID());
				setField(c, "id", UUID.randomUUID());
				return c;
			});

			CompetencyResponse response = service.createCompetency(course.getPublicUuid(),
					new CreateCompetencyRequest("MAT_C1", "Resuelve problemas",
							"description", 1, null));

			assertThat(response.code()).isEqualTo("MAT_C1");
			assertThat(response.displayOrder()).isEqualTo(1);
			assertThat(response.course().code()).isEqualTo("MAT");
			assertThat(response.capacities()).isEmpty();
		}

		@Test
		@DisplayName("happy path without displayOrder → appends to tail (max+1)")
		void happyPathAutoAppend() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findByCourseAndCodeIgnoreCase(course, "MAT_C3"))
					.thenReturn(Optional.empty());
			when(competencyRepository.findMaxDisplayOrderForCourse(course)).thenReturn(2);
			when(competencyRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Competency c = inv.getArgument(0);
				setField(c, "publicUuid", UUID.randomUUID());
				setField(c, "id", UUID.randomUUID());
				return c;
			});

			CompetencyResponse response = service.createCompetency(course.getPublicUuid(),
					new CreateCompetencyRequest("MAT_C3", "Tercera", null, null, null));

			assertThat(response.displayOrder()).isEqualTo(3);
		}

		@Test
		@DisplayName("code duplicated in same course → 409 COMPETENCY_CODE_TAKEN")
		void codeTaken() {
			Course course = newCourse("MAT", "Matemática");
			Competency existing = newCompetency(course, "MAT_C1", 1);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findByCourseAndCodeIgnoreCase(course, "MAT_C1"))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createCompetency(course.getPublicUuid(),
					new CreateCompetencyRequest("MAT_C1", "Otra", null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("MAT_C1");
			verify(competencyRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DB ordinal collision (concurrent insert) → 409 COMPETENCY_ORDER_TAKEN")
		void dbOrdinalCollision() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findByCourseAndCodeIgnoreCase(course, "MAT_C1"))
					.thenReturn(Optional.empty());
			when(competencyRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException(
							"uk_competencies_course_order_active"));

			assertThatThrownBy(() -> service.createCompetency(course.getPublicUuid(),
					new CreateCompetencyRequest("MAT_C1", "First", null, 1, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("ordinal 1");
		}
	}

	// =========================================================================
	// updateCompetency
	// =========================================================================

	@Nested
	@DisplayName("updateCompetency")
	class UpdateCompetency {

		@Test
		@DisplayName("happy path with partial-merge")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Competency competency = newCompetency(course, "MAT_C1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(competencyRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(any()))
					.thenReturn(List.of());

			CompetencyResponse response = service.updateCompetency(competency.getPublicUuid(),
					new UpdateCompetencyRequest(null, null,
							"Updated description", null));

			assertThat(response.description()).isEqualTo("Updated description");
			assertThat(response.code()).isEqualTo("MAT_C1");
		}

		@Test
		@DisplayName("empty patch returns current state without writing")
		void emptyPatchIsNoop() {
			Course course = newCourse("MAT", "Matemática");
			Competency competency = newCompetency(course, "MAT_C1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(any()))
					.thenReturn(List.of());

			CompetencyResponse response = service.updateCompetency(competency.getPublicUuid(),
					new UpdateCompetencyRequest(null, null, null, null));

			assertThat(response.code()).isEqualTo("MAT_C1");
			verify(competencyRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("code change collides with sibling → 409 COMPETENCY_CODE_TAKEN")
		void codeRenameCollision() {
			Course course = newCourse("MAT", "Matemática");
			Competency target = newCompetency(course, "MAT_C1", 1);
			Competency sibling = newCompetency(course, "MAT_C2", 2);
			when(competencyRepository.findByPublicUuid(target.getPublicUuid()))
					.thenReturn(Optional.of(target));
			when(competencyRepository.findByCourseAndCodeIgnoreCase(course, "MAT_C2"))
					.thenReturn(Optional.of(sibling));

			assertThatThrownBy(() -> service.updateCompetency(target.getPublicUuid(),
					new UpdateCompetencyRequest("MAT_C2", null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("MAT_C2");
		}
	}

	// =========================================================================
	// reorderCompetencies
	// =========================================================================

	@Nested
	@DisplayName("reorderCompetencies")
	class Reorder {

		@Test
		@DisplayName("happy path — applies new ordinals atomically (two-pass)")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Competency c1 = newCompetency(course, "MAT_C1", 1);
			Competency c2 = newCompetency(course, "MAT_C2", 2);
			Competency c3 = newCompetency(course, "MAT_C3", 3);
			List<Competency> sorted = new ArrayList<>(List.of(c1, c2, c3));

			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(sorted);
			when(competencyRepository.save(any(Competency.class)))
					.thenAnswer(inv -> inv.getArgument(0));
			lenient().when(capacityRepository.findAllByCompetencyIn(anyList()))
					.thenReturn(List.of());

			CompetencyReorderRequest req = new CompetencyReorderRequest(List.of(
					new CompetencyReorderRequest.Item(c1.getPublicUuid(), 3),
					new CompetencyReorderRequest.Item(c2.getPublicUuid(), 1),
					new CompetencyReorderRequest.Item(c3.getPublicUuid(), 2)
			));

			service.reorderCompetencies(course.getPublicUuid(), req);

			assertThat(c1.getDisplayOrder()).isEqualTo(3);
			assertThat(c2.getDisplayOrder()).isEqualTo(1);
			assertThat(c3.getDisplayOrder()).isEqualTo(2);
			verify(competencyRepository, atLeastOnce()).save(any(Competency.class));
		}

		@Test
		@DisplayName("payload with competency from another course → 409 COMPETENCY_OUT_OF_COURSE")
		void crossCourseRejected() {
			Course course = newCourse("MAT", "Matemática");
			Competency own = newCompetency(course, "MAT_C1", 1);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(own));

			UUID stranger = UUID.randomUUID();
			CompetencyReorderRequest req = new CompetencyReorderRequest(List.of(
					new CompetencyReorderRequest.Item(own.getPublicUuid(), 1),
					new CompetencyReorderRequest.Item(stranger, 2)
			));

			assertThatThrownBy(() -> service.reorderCompetencies(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("does not belong to course");
		}

		@Test
		@DisplayName("duplicate displayOrder in payload → 409 COMPETENCY_REORDER_INVALID")
		void duplicateOrderRejected() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));

			CompetencyReorderRequest req = new CompetencyReorderRequest(List.of(
					new CompetencyReorderRequest.Item(UUID.randomUUID(), 1),
					new CompetencyReorderRequest.Item(UUID.randomUUID(), 1)
			));

			assertThatThrownBy(() -> service.reorderCompetencies(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Duplicate displayOrder");
		}

		@Test
		@DisplayName("DB collision on the final flush → 409 COMPETENCY_ORDER_TAKEN")
		void dbCollision() {
			Course course = newCourse("MAT", "Matemática");
			Competency c1 = newCompetency(course, "MAT_C1", 1);
			Competency c2 = newCompetency(course, "MAT_C2", 2);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(c1, c2));
			when(competencyRepository.save(any(Competency.class)))
					.thenAnswer(inv -> inv.getArgument(0));
			Mockito.doNothing()
					.doThrow(new DataIntegrityViolationException(
							"uk_competencies_course_order_active"))
					.when(competencyRepository).flush();

			CompetencyReorderRequest req = new CompetencyReorderRequest(List.of(
					new CompetencyReorderRequest.Item(c1.getPublicUuid(), 1),
					new CompetencyReorderRequest.Item(c2.getPublicUuid(), 2)
			));

			assertThatThrownBy(() -> service.reorderCompetencies(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// deleteCompetency
	// =========================================================================

	@Nested
	@DisplayName("deleteCompetency")
	class DeleteCompetency {

		@Test
		@DisplayName("happy path — placeholder count is 0, soft-deletes + cascades capacities")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity cap = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(List.of(cap));

			service.deleteCompetency(competency.getPublicUuid());

			verify(capacityRepository).deleteAll(List.of(cap));
			verify(competencyRepository).delete(competency);
		}

		@Test
		@DisplayName("unknown competency → 404 RESOURCE_NOT_FOUND")
		void unknownCompetency() {
			UUID anyUuid = UUID.randomUUID();
			when(competencyRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteCompetency(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
			verify(competencyRepository, never()).delete(any(Competency.class));
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Course newCourse(String code, String name) {
		Course c = new Course();
		c.setCode(code);
		c.setName(name);
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static Competency newCompetency(Course course, String code, int displayOrder) {
		Competency competency = new Competency();
		competency.setCourse(course);
		competency.setCode(code);
		competency.setName("Name " + code);
		competency.setDisplayOrder(displayOrder);
		competency.setIsActive(Boolean.TRUE);
		setField(competency, "publicUuid", UUID.randomUUID());
		setField(competency, "id", UUID.randomUUID());
		return competency;
	}

	private static Capacity newCapacity(Competency competency, String code, int displayOrder) {
		Capacity capacity = new Capacity();
		capacity.setCompetency(competency);
		capacity.setCode(code);
		capacity.setName("Name " + code);
		capacity.setDisplayOrder(displayOrder);
		capacity.setIsActive(Boolean.TRUE);
		setField(capacity, "publicUuid", UUID.randomUUID());
		setField(capacity, "id", UUID.randomUUID());
		return capacity;
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
