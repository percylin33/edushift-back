package com.edushift.modules.academic.competency.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.competency.dto.CapacityReorderRequest;
import com.edushift.modules.academic.competency.dto.CapacityResponse;
import com.edushift.modules.academic.competency.dto.CreateCapacityRequest;
import com.edushift.modules.academic.competency.dto.UpdateCapacityRequest;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CapacityMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
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
 * Unit tests for {@link CapacityServiceImpl} (Sprint 5A — BE-5A.2).
 *
 * <p>Mirrors {@link CompetencyServiceImplTest}, one level deeper: each
 * capacity hangs off a competency (which itself hangs off a course),
 * but business rules and error codes are scoped to its parent competency.</p>
 */
@ExtendWith(MockitoExtension.class)
class CapacityServiceImplTest {

	@Mock private CapacityRepository capacityRepository;
	@Mock private CompetencyRepository competencyRepository;
	@Spy private CapacityMapper mapper = new CapacityMapper();

	@InjectMocks private CapacityServiceImpl service;

	// =========================================================================
	// listCapacities
	// =========================================================================

	@Nested
	@DisplayName("listCapacities")
	class ListCapacities {

		@Test
		@DisplayName("returns capacities sorted by displayOrder asc")
		void happyPath() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity cap1 = newCapacity(competency, "MAT_C1_CAP1", 1);
			Capacity cap2 = newCapacity(competency, "MAT_C1_CAP2", 2);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(List.of(cap1, cap2));

			List<CapacityResponse> result = service.listCapacities(
					competency.getPublicUuid(), null);

			assertThat(result).hasSize(2);
			assertThat(result.get(0).code()).isEqualTo("MAT_C1_CAP1");
			assertThat(result.get(0).displayOrder()).isEqualTo(1);
			assertThat(result.get(0).competency().code()).isEqualTo("MAT_C1");
			assertThat(result.get(0).competency().course().code()).isEqualTo("MAT");
		}

		@Test
		@DisplayName("isActive=true narrows to active capacities only")
		void filtersByIsActive() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity active = newCapacity(competency, "MAT_C1_CAP1", 1);
			Capacity inactive = newCapacity(competency, "MAT_C1_CAP2", 2);
			inactive.setIsActive(Boolean.FALSE);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(List.of(active, inactive));

			List<CapacityResponse> result = service.listCapacities(
					competency.getPublicUuid(), Boolean.TRUE);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).code()).isEqualTo("MAT_C1_CAP1");
		}

		@Test
		@DisplayName("unknown competency → 404 RESOURCE_NOT_FOUND")
		void unknownCompetency() {
			UUID anyUuid = UUID.randomUUID();
			when(competencyRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listCapacities(anyUuid, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createCapacity
	// =========================================================================

	@Nested
	@DisplayName("createCapacity")
	class CreateCapacity {

		@Test
		@DisplayName("happy path with explicit displayOrder")
		void happyPathExplicit() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findByCompetencyAndCodeIgnoreCase(
					competency, "MAT_C1_CAP1"))
					.thenReturn(Optional.empty());
			when(capacityRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Capacity c = inv.getArgument(0);
				setField(c, "publicUuid", UUID.randomUUID());
				setField(c, "id", UUID.randomUUID());
				return c;
			});

			CapacityResponse response = service.createCapacity(competency.getPublicUuid(),
					new CreateCapacityRequest("MAT_C1_CAP1", "Traduce cantidades",
							"description", 1, null));

			assertThat(response.code()).isEqualTo("MAT_C1_CAP1");
			assertThat(response.displayOrder()).isEqualTo(1);
			assertThat(response.competency().code()).isEqualTo("MAT_C1");
		}

		@Test
		@DisplayName("happy path without displayOrder → appends to tail (max+1)")
		void happyPathAutoAppend() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findByCompetencyAndCodeIgnoreCase(
					competency, "MAT_C1_CAP3"))
					.thenReturn(Optional.empty());
			when(capacityRepository.findMaxDisplayOrderForCompetency(competency))
					.thenReturn(2);
			when(capacityRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Capacity c = inv.getArgument(0);
				setField(c, "publicUuid", UUID.randomUUID());
				setField(c, "id", UUID.randomUUID());
				return c;
			});

			CapacityResponse response = service.createCapacity(competency.getPublicUuid(),
					new CreateCapacityRequest("MAT_C1_CAP3", "Tercera",
							null, null, null));

			assertThat(response.displayOrder()).isEqualTo(3);
		}

		@Test
		@DisplayName("code duplicated in same competency → 409 CAPACITY_CODE_TAKEN")
		void codeTaken() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity existing = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findByCompetencyAndCodeIgnoreCase(
					competency, "MAT_C1_CAP1"))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createCapacity(competency.getPublicUuid(),
					new CreateCapacityRequest("MAT_C1_CAP1", "Otra", null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("MAT_C1_CAP1");
			verify(capacityRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("DB ordinal collision → 409 CAPACITY_ORDER_TAKEN")
		void dbOrdinalCollision() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findByCompetencyAndCodeIgnoreCase(
					competency, "MAT_C1_CAP1"))
					.thenReturn(Optional.empty());
			when(capacityRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException(
							"uk_capacities_competency_order_active"));

			assertThatThrownBy(() -> service.createCapacity(competency.getPublicUuid(),
					new CreateCapacityRequest("MAT_C1_CAP1", "First", null, 1, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("ordinal 1");
		}
	}

	// =========================================================================
	// updateCapacity
	// =========================================================================

	@Nested
	@DisplayName("updateCapacity")
	class UpdateCapacity {

		@Test
		@DisplayName("happy path with partial-merge")
		void happyPath() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity capacity = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(capacityRepository.findByPublicUuid(capacity.getPublicUuid()))
					.thenReturn(Optional.of(capacity));
			when(capacityRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			CapacityResponse response = service.updateCapacity(capacity.getPublicUuid(),
					new UpdateCapacityRequest(null, null,
							"Updated description", null));

			assertThat(response.description()).isEqualTo("Updated description");
			assertThat(response.code()).isEqualTo("MAT_C1_CAP1");
		}

		@Test
		@DisplayName("empty patch returns current state without writing")
		void emptyPatchIsNoop() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity capacity = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(capacityRepository.findByPublicUuid(capacity.getPublicUuid()))
					.thenReturn(Optional.of(capacity));

			CapacityResponse response = service.updateCapacity(capacity.getPublicUuid(),
					new UpdateCapacityRequest(null, null, null, null));

			assertThat(response.code()).isEqualTo("MAT_C1_CAP1");
			verify(capacityRepository, never()).saveAndFlush(any());
		}
	}

	// =========================================================================
	// reorderCapacities
	// =========================================================================

	@Nested
	@DisplayName("reorderCapacities")
	class Reorder {

		@Test
		@DisplayName("happy path — applies new ordinals atomically")
		void happyPath() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity cap1 = newCapacity(competency, "MAT_C1_CAP1", 1);
			Capacity cap2 = newCapacity(competency, "MAT_C1_CAP2", 2);
			List<Capacity> sorted = new ArrayList<>(List.of(cap1, cap2));

			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(sorted);
			when(capacityRepository.save(any(Capacity.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			CapacityReorderRequest req = new CapacityReorderRequest(List.of(
					new CapacityReorderRequest.Item(cap1.getPublicUuid(), 2),
					new CapacityReorderRequest.Item(cap2.getPublicUuid(), 1)
			));

			service.reorderCapacities(competency.getPublicUuid(), req);

			assertThat(cap1.getDisplayOrder()).isEqualTo(2);
			assertThat(cap2.getDisplayOrder()).isEqualTo(1);
			verify(capacityRepository, atLeastOnce()).save(any(Capacity.class));
		}

		@Test
		@DisplayName("payload with capacity from another competency → 409 CAPACITY_OUT_OF_COMPETENCY")
		void crossCompetencyRejected() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity own = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(List.of(own));

			UUID stranger = UUID.randomUUID();
			CapacityReorderRequest req = new CapacityReorderRequest(List.of(
					new CapacityReorderRequest.Item(own.getPublicUuid(), 1),
					new CapacityReorderRequest.Item(stranger, 2)
			));

			assertThatThrownBy(() -> service.reorderCapacities(competency.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("does not belong to competency");
		}

		@Test
		@DisplayName("DB collision on the final flush → 409 CAPACITY_ORDER_TAKEN")
		void dbCollision() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity cap1 = newCapacity(competency, "MAT_C1_CAP1", 1);
			Capacity cap2 = newCapacity(competency, "MAT_C1_CAP2", 2);
			when(competencyRepository.findByPublicUuid(competency.getPublicUuid()))
					.thenReturn(Optional.of(competency));
			when(capacityRepository.findAllByCompetencyOrderByDisplayOrderAsc(competency))
					.thenReturn(List.of(cap1, cap2));
			when(capacityRepository.save(any(Capacity.class)))
					.thenAnswer(inv -> inv.getArgument(0));
			Mockito.doNothing()
					.doThrow(new DataIntegrityViolationException(
							"uk_capacities_competency_order_active"))
					.when(capacityRepository).flush();

			CapacityReorderRequest req = new CapacityReorderRequest(List.of(
					new CapacityReorderRequest.Item(cap1.getPublicUuid(), 1),
					new CapacityReorderRequest.Item(cap2.getPublicUuid(), 2)
			));

			assertThatThrownBy(() -> service.reorderCapacities(competency.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// deleteCapacity
	// =========================================================================

	@Nested
	@DisplayName("deleteCapacity")
	class DeleteCapacity {

		@Test
		@DisplayName("happy path — placeholder count is 0, soft-deletes")
		void happyPath() {
			Course course = newCourse();
			Competency competency = newCompetency(course, "MAT_C1", 1);
			Capacity capacity = newCapacity(competency, "MAT_C1_CAP1", 1);
			when(capacityRepository.findByPublicUuid(capacity.getPublicUuid()))
					.thenReturn(Optional.of(capacity));

			service.deleteCapacity(capacity.getPublicUuid());

			verify(capacityRepository).delete(capacity);
		}

		@Test
		@DisplayName("unknown capacity → 404 RESOURCE_NOT_FOUND")
		void unknownCapacity() {
			UUID anyUuid = UUID.randomUUID();
			when(capacityRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteCapacity(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
			verify(capacityRepository, never()).delete(any(Capacity.class));
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Course newCourse() {
		Course c = new Course();
		c.setCode("MAT");
		c.setName("Matemática");
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
