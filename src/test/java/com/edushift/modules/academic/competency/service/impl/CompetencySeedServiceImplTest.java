package com.edushift.modules.academic.competency.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.competency.dto.SeedCompetenciesResponse;
import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.mapper.CompetencyMapper;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CompetencySeedServiceImpl} (Sprint 5A — BE-5A.2).
 *
 * <p>Covers the four documented branches of the seed contract:</p>
 * <ul>
 *   <li>course not found ⇒ {@link ResourceNotFoundException}</li>
 *   <li>course already has competencies ⇒ {@code seeded=false} (no-op)</li>
 *   <li>course code not in {@code CompetencyDefaults} ⇒
 *       {@code unsupportedCourseCode=true}</li>
 *   <li>happy path on a supported code (MAT) ⇒ persists 2 competencies +
 *       3 capacities and returns the listing</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompetencySeedServiceImplTest {

	@Mock private CourseRepository courseRepository;
	@Mock private CompetencyRepository competencyRepository;
	@Mock private CapacityRepository capacityRepository;
	@Spy private CompetencyMapper competencyMapper = new CompetencyMapper();

	@InjectMocks private CompetencySeedServiceImpl service;

	@Test
	@DisplayName("unknown course → 404 RESOURCE_NOT_FOUND")
	void unknownCourse() {
		UUID anyUuid = UUID.randomUUID();
		when(courseRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.seedForCourse(anyUuid))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(competencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("course already has competencies → seeded=false (no-op idempotent)")
	void alreadySeededIsNoop() {
		Course course = newCourse("MAT");
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(competencyRepository.countByCourse(course)).thenReturn(2L);

		SeedCompetenciesResponse response = service.seedForCourse(course.getPublicUuid());

		assertThat(response.seeded()).isFalse();
		assertThat(response.unsupportedCourseCode()).isFalse();
		assertThat(response.competenciesCreated()).isZero();
		assertThat(response.capacitiesCreated()).isZero();
		assertThat(response.created()).isEmpty();
		verify(competencyRepository, never()).save(any());
		verify(capacityRepository, never()).save(any());
	}

	@Test
	@DisplayName("course code not in CompetencyDefaults → unsupportedCourseCode=true")
	void unsupportedCourseCode() {
		Course course = newCourse("INGLES");
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(competencyRepository.countByCourse(course)).thenReturn(0L);

		SeedCompetenciesResponse response = service.seedForCourse(course.getPublicUuid());

		assertThat(response.seeded()).isFalse();
		assertThat(response.unsupportedCourseCode()).isTrue();
		assertThat(response.courseCode()).isEqualTo("INGLES");
		assertThat(response.competenciesCreated()).isZero();
		verify(competencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("happy path with MAT — persists 2 competencies + 3 capacities")
	void happyPathMat() {
		Course course = newCourse("MAT");
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(competencyRepository.countByCourse(course)).thenReturn(0L);
		when(competencyRepository.save(any(Competency.class))).thenAnswer(inv -> {
			Competency c = inv.getArgument(0);
			setField(c, "publicUuid", UUID.randomUUID());
			setField(c, "id", UUID.randomUUID());
			return c;
		});
		when(capacityRepository.save(any(Capacity.class))).thenAnswer(inv -> {
			Capacity c = inv.getArgument(0);
			setField(c, "publicUuid", UUID.randomUUID());
			setField(c, "id", UUID.randomUUID());
			return c;
		});

		SeedCompetenciesResponse response = service.seedForCourse(course.getPublicUuid());

		assertThat(response.seeded()).isTrue();
		assertThat(response.unsupportedCourseCode()).isFalse();
		assertThat(response.courseCode()).isEqualTo("MAT");
		assertThat(response.competenciesCreated()).isEqualTo(2);
		assertThat(response.capacitiesCreated()).isEqualTo(3);
		assertThat(response.created()).hasSize(2);
		assertThat(response.created().get(0).code()).isEqualTo("MAT_C1");
		assertThat(response.created().get(0).capacityCount()).isEqualTo(2L);
		assertThat(response.created().get(1).code()).isEqualTo("MAT_C2");
		assertThat(response.created().get(1).capacityCount()).isEqualTo(1L);
		verify(competencyRepository, times(2)).save(any(Competency.class));
		verify(capacityRepository, times(3)).save(any(Capacity.class));
	}

	private static Course newCourse(String code) {
		Course c = new Course();
		c.setCode(code);
		c.setName("Course " + code);
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
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
