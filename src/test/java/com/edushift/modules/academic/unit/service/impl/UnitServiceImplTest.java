package com.edushift.modules.academic.unit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.unit.dto.CreateUnitRequest;
import com.edushift.modules.academic.unit.dto.UnitListItem;
import com.edushift.modules.academic.unit.dto.UnitReorderRequest;
import com.edushift.modules.academic.unit.dto.UnitResponse;
import com.edushift.modules.academic.unit.dto.UpdateUnitRequest;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.mapper.UnitMapper;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDate;
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
 * Unit tests for {@link UnitServiceImpl} (Sprint 5A — BE-5A.1).
 *
 * <p>Covers each error code in the contract plus the two-pass reorder
 * happy path + DB-collision case. The {@code UNIT_HAS_SESSIONS} branch
 * exercises the current placeholder ({@code countSessionsByUnit → 0L})
 * so the active code path is the soft-delete; once BE-5A.4 wires up
 * {@code LearningSessionRepository}, a new test will cover the > 0 branch.</p>
 */
@ExtendWith(MockitoExtension.class)
class UnitServiceImplTest {

	@Mock private UnitRepository unitRepository;
	@Mock private CourseRepository courseRepository;
	@Mock private LearningSessionRepository sessionRepository;
	@Spy private UnitMapper mapper = new UnitMapper();

	@InjectMocks private UnitServiceImpl service;

	// =========================================================================
	// listUnits
	// =========================================================================

	@Nested
	@DisplayName("listUnits")
	class ListUnits {

		@Test
		@DisplayName("returns units sorted by displayOrder asc")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Unit u1 = newUnit(course, "Unidad I", 1);
			Unit u2 = newUnit(course, "Unidad II", 2);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(u1, u2));

			List<UnitListItem> result = service.listUnits(course.getPublicUuid(), null);

			assertThat(result).hasSize(2);
			assertThat(result.get(0).name()).isEqualTo("Unidad I");
			assertThat(result.get(0).displayOrder()).isEqualTo(1);
			assertThat(result.get(0).sessionCount()).isZero();
		}

		@Test
		@DisplayName("isActive=true narrows to active units only")
		void filtersByIsActive() {
			Course course = newCourse("MAT", "Matemática");
			Unit active = newUnit(course, "Unidad I", 1);
			Unit inactive = newUnit(course, "Unidad II", 2);
			inactive.setIsActive(Boolean.FALSE);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(active, inactive));

			List<UnitListItem> result = service.listUnits(course.getPublicUuid(), Boolean.TRUE);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).name()).isEqualTo("Unidad I");
		}

		@Test
		@DisplayName("unknown course → 404 RESOURCE_NOT_FOUND")
		void unknownCourse() {
			UUID anyUuid = UUID.randomUUID();
			when(courseRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listUnits(anyUuid, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createUnit
	// =========================================================================

	@Nested
	@DisplayName("createUnit")
	class CreateUnit {

		@Test
		@DisplayName("happy path with explicit displayOrder")
		void happyPathExplicit() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findByCourseAndNameIgnoreCase(course, "Unidad I"))
					.thenReturn(Optional.empty());
			when(unitRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Unit u = inv.getArgument(0);
				setField(u, "publicUuid", UUID.randomUUID());
				setField(u, "id", UUID.randomUUID());
				return u;
			});

			UnitResponse response = service.createUnit(course.getPublicUuid(),
					new CreateUnitRequest("Unidad I", "Description", 1,
							LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31), null));

			assertThat(response.name()).isEqualTo("Unidad I");
			assertThat(response.displayOrder()).isEqualTo(1);
			assertThat(response.course().code()).isEqualTo("MAT");
			assertThat(response.sessionCount()).isZero();
		}

		@Test
		@DisplayName("happy path without displayOrder → appends to tail (max+1)")
		void happyPathAutoAppend() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findByCourseAndNameIgnoreCase(course, "Unidad III"))
					.thenReturn(Optional.empty());
			when(unitRepository.findMaxDisplayOrderForCourse(course)).thenReturn(2);
			when(unitRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Unit u = inv.getArgument(0);
				setField(u, "publicUuid", UUID.randomUUID());
				setField(u, "id", UUID.randomUUID());
				return u;
			});

			UnitResponse response = service.createUnit(course.getPublicUuid(),
					new CreateUnitRequest("Unidad III", null, null, null, null, null));

			assertThat(response.displayOrder()).isEqualTo(3);
		}

		@Test
		@DisplayName("name duplicated in same course → 409 UNIT_NAME_EXISTS")
		void nameTaken() {
			Course course = newCourse("MAT", "Matemática");
			Unit existing = newUnit(course, "Unidad I", 1);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findByCourseAndNameIgnoreCase(course, "Unidad I"))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createUnit(course.getPublicUuid(),
					new CreateUnitRequest("Unidad I", null, null, null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Unidad I");
			verify(unitRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("endDate < startDate → 400 UNIT_DATE_INVERTED")
		void datesInverted() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));

			assertThatThrownBy(() -> service.createUnit(course.getPublicUuid(),
					new CreateUnitRequest("Unidad I", null, 1,
							LocalDate.of(2026, 5, 1), LocalDate.of(2026, 3, 1), null)))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("must be on or after");
			verify(unitRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DB ordinal collision (concurrent insert) → 409 UNIT_ORDER_TAKEN")
		void dbOrdinalCollision() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findByCourseAndNameIgnoreCase(course, "Unidad I"))
					.thenReturn(Optional.empty());
			when(unitRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException(
							"uk_academic_units_course_order_active"));

			assertThatThrownBy(() -> service.createUnit(course.getPublicUuid(),
					new CreateUnitRequest("Unidad I", null, 1, null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("ordinal 1");
		}
	}

	// =========================================================================
	// updateUnit
	// =========================================================================

	@Nested
	@DisplayName("updateUnit")
	class UpdateUnit {

		@Test
		@DisplayName("happy path with partial-merge")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Unit unit = newUnit(course, "Unidad I", 1);
			when(unitRepository.findByPublicUuid(unit.getPublicUuid()))
					.thenReturn(Optional.of(unit));
			when(unitRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

			UnitResponse response = service.updateUnit(unit.getPublicUuid(),
					new UpdateUnitRequest(null, "Updated description", null, null, null));

			assertThat(response.description()).isEqualTo("Updated description");
			assertThat(response.name()).isEqualTo("Unidad I");
		}

		@Test
		@DisplayName("empty patch returns current state without writing")
		void emptyPatchIsNoop() {
			Course course = newCourse("MAT", "Matemática");
			Unit unit = newUnit(course, "Unidad I", 1);
			when(unitRepository.findByPublicUuid(unit.getPublicUuid()))
					.thenReturn(Optional.of(unit));

			UnitResponse response = service.updateUnit(unit.getPublicUuid(),
					new UpdateUnitRequest(null, null, null, null, null));

			assertThat(response.name()).isEqualTo("Unidad I");
			verify(unitRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("name change collides with sibling → 409 UNIT_NAME_EXISTS")
		void renameCollision() {
			Course course = newCourse("MAT", "Matemática");
			Unit target = newUnit(course, "Unidad I", 1);
			Unit sibling = newUnit(course, "Unidad II", 2);
			when(unitRepository.findByPublicUuid(target.getPublicUuid()))
					.thenReturn(Optional.of(target));
			when(unitRepository.findByCourseAndNameIgnoreCase(course, "Unidad II"))
					.thenReturn(Optional.of(sibling));

			assertThatThrownBy(() -> service.updateUnit(target.getPublicUuid(),
					new UpdateUnitRequest("Unidad II", null, null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Unidad II");
		}

		@Test
		@DisplayName("post-merge endDate < startDate → 400 UNIT_DATE_INVERTED")
		void postMergeDatesInverted() {
			Course course = newCourse("MAT", "Matemática");
			Unit unit = newUnit(course, "Unidad I", 1);
			unit.setStartDate(LocalDate.of(2026, 3, 1));
			when(unitRepository.findByPublicUuid(unit.getPublicUuid()))
					.thenReturn(Optional.of(unit));

			// Patch sets endDate before the existing startDate → invalid post-merge
			assertThatThrownBy(() -> service.updateUnit(unit.getPublicUuid(),
					new UpdateUnitRequest(null, null, null, LocalDate.of(2026, 2, 1), null)))
					.isInstanceOf(BadRequestException.class);
		}
	}

	// =========================================================================
	// reorderUnits
	// =========================================================================

	@Nested
	@DisplayName("reorderUnits")
	class ReorderUnits {

		@Test
		@DisplayName("happy path — applies new ordinals atomically")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Unit u1 = newUnit(course, "Unidad I", 1);
			Unit u2 = newUnit(course, "Unidad II", 2);
			Unit u3 = newUnit(course, "Unidad III", 3);
			List<Unit> sorted = new ArrayList<>(List.of(u1, u2, u3));

			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(sorted);
			when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));

			UnitReorderRequest req = new UnitReorderRequest(List.of(
					new UnitReorderRequest.Item(u1.getPublicUuid(), 3),
					new UnitReorderRequest.Item(u2.getPublicUuid(), 1),
					new UnitReorderRequest.Item(u3.getPublicUuid(), 2)
			));

			service.reorderUnits(course.getPublicUuid(), req);

			assertThat(u1.getDisplayOrder()).isEqualTo(3);
			assertThat(u2.getDisplayOrder()).isEqualTo(1);
			assertThat(u3.getDisplayOrder()).isEqualTo(2);
			verify(unitRepository, atLeastOnce()).save(any(Unit.class));
		}

		@Test
		@DisplayName("payload with unit from another course → 409 UNIT_OUT_OF_COURSE")
		void crossCourseRejected() {
			Course course = newCourse("MAT", "Matemática");
			Unit own = newUnit(course, "Unidad I", 1);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(own));

			UUID stranger = UUID.randomUUID();
			UnitReorderRequest req = new UnitReorderRequest(List.of(
					new UnitReorderRequest.Item(own.getPublicUuid(), 1),
					new UnitReorderRequest.Item(stranger, 2)
			));

			assertThatThrownBy(() -> service.reorderUnits(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("does not belong to course");
		}

		@Test
		@DisplayName("duplicate displayOrder in payload → 409 UNIT_REORDER_INVALID")
		void duplicateOrderRejected() {
			Course course = newCourse("MAT", "Matemática");
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));

			UnitReorderRequest req = new UnitReorderRequest(List.of(
					new UnitReorderRequest.Item(UUID.randomUUID(), 1),
					new UnitReorderRequest.Item(UUID.randomUUID(), 1)
			));

			assertThatThrownBy(() -> service.reorderUnits(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Duplicate displayOrder");
		}

		@Test
		@DisplayName("duplicate publicUuid in payload → 409 UNIT_REORDER_INVALID")
		void duplicateUuidRejected() {
			Course course = newCourse("MAT", "Matemática");
			UUID dup = UUID.randomUUID();
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));

			UnitReorderRequest req = new UnitReorderRequest(List.of(
					new UnitReorderRequest.Item(dup, 1),
					new UnitReorderRequest.Item(dup, 2)
			));

			assertThatThrownBy(() -> service.reorderUnits(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Duplicate unit");
		}

		@Test
		@DisplayName("DB collision on the final flush → 409 UNIT_ORDER_TAKEN")
		void dbCollision() {
			Course course = newCourse("MAT", "Matemática");
			Unit u1 = newUnit(course, "Unidad I", 1);
			Unit u2 = newUnit(course, "Unidad II", 2);
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(unitRepository.findAllByCourseOrderByDisplayOrderAsc(course))
					.thenReturn(List.of(u1, u2));
			when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));
			Mockito.doNothing()
					.doThrow(new DataIntegrityViolationException(
							"uk_academic_units_course_order_active"))
					.when(unitRepository).flush();

			UnitReorderRequest req = new UnitReorderRequest(List.of(
					new UnitReorderRequest.Item(u1.getPublicUuid(), 1),
					new UnitReorderRequest.Item(u2.getPublicUuid(), 2)
			));

			assertThatThrownBy(() -> service.reorderUnits(course.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// deleteUnit
	// =========================================================================

	@Nested
	@DisplayName("deleteUnit")
	class DeleteUnit {

		@Test
		@DisplayName("happy path — placeholder count is 0, soft-deletes")
		void happyPath() {
			Course course = newCourse("MAT", "Matemática");
			Unit unit = newUnit(course, "Unidad I", 1);
			when(unitRepository.findByPublicUuid(unit.getPublicUuid()))
					.thenReturn(Optional.of(unit));

			service.deleteUnit(unit.getPublicUuid());

			verify(unitRepository).delete(unit);
		}

		@Test
		@DisplayName("unknown unit → 404 RESOURCE_NOT_FOUND")
		void unknownUnit() {
			UUID anyUuid = UUID.randomUUID();
			when(unitRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteUnit(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
			verify(unitRepository, never()).delete(any(Unit.class));
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

	private static Unit newUnit(Course course, String name, int displayOrder) {
		Unit u = new Unit();
		u.setCourse(course);
		u.setName(name);
		u.setDisplayOrder(displayOrder);
		u.setIsActive(Boolean.TRUE);
		setField(u, "publicUuid", UUID.randomUUID());
		setField(u, "id", UUID.randomUUID());
		return u;
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
