package com.edushift.modules.academic.unit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.unit.dto.SeedUnitsResponse;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.mapper.UnitMapper;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.tenants.service.TenantSettingsService;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.security.CurrentUserProvider;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnitSeedServiceImplTest {

	@Mock private CourseRepository courseRepository;
	@Mock private UnitRepository unitRepository;
	@Mock private TenantSettingsService tenantSettingsService;
	@Mock private CurrentUserProvider currentUserProvider;
	@Spy private UnitMapper unitMapper = new UnitMapper();

	@InjectMocks private UnitSeedServiceImpl service;

	@Test
	@DisplayName("unknown course → 404 RESOURCE_NOT_FOUND")
	void unknownCourse() {
		UUID anyUuid = UUID.randomUUID();
		when(courseRepository.findByPublicUuid(anyUuid)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.seedForCourse(anyUuid, 8, false))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(unitRepository, never()).save(any());
	}

	@Test
	@DisplayName("course already has units → seeded=false (idempotent no-op)")
	void alreadySeededIsNoop() {
		Course course = newCourse("MAT");
		UUID tenantId = UUID.randomUUID();
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
		when(unitRepository.countByCourse(course)).thenReturn(3L);

		SeedUnitsResponse response = service.seedForCourse(course.getPublicUuid(), 8, false);

		assertThat(response.seeded()).isFalse();
		assertThat(response.countRequested()).isEqualTo(8);
		assertThat(response.unitsCreated()).isZero();
		assertThat(response.created()).isEmpty();
		verify(unitRepository, never()).save(any());
	}

	@Test
	@DisplayName("null count → uses tenant defaultUnitCount")
	void usesTenantDefaultWhenCountNull() {
		Course course = newCourse("MAT");
		UUID tenantId = UUID.randomUUID();
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
		when(tenantSettingsService.getDefaultUnitCount(tenantId)).thenReturn(5);
		when(unitRepository.countByCourse(course)).thenReturn(0L);
		stubSave();

		SeedUnitsResponse response = service.seedForCourse(course.getPublicUuid(), null, false);

		assertThat(response.seeded()).isTrue();
		assertThat(response.countRequested()).isEqualTo(5);
		assertThat(response.unitsCreated()).isEqualTo(5);
		assertThat(response.created()).hasSize(5);
		assertThat(response.created().get(0).name()).isEqualTo("Unidad 1");
		assertThat(response.created().get(4).name()).isEqualTo("Unidad 5");
		verify(unitRepository, times(5)).save(any(Unit.class));
		verify(tenantSettingsService, never()).setDefaultUnitCount(any(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("explicit count above max is clamped to 20")
	void clampsHighCount() {
		Course course = newCourse("COMU");
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
		when(unitRepository.countByCourse(course)).thenReturn(0L);
		stubSave();

		SeedUnitsResponse over = service.seedForCourse(course.getPublicUuid(), 99, false);
		assertThat(over.countRequested()).isEqualTo(20);
		assertThat(over.unitsCreated()).isEqualTo(20);
	}

	@Test
	@DisplayName("explicit count below min is clamped to 1")
	void clampsLowCount() {
		Course course = newCourse("COMU");
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
		when(unitRepository.countByCourse(course)).thenReturn(0L);
		stubSave();

		SeedUnitsResponse under = service.seedForCourse(course.getPublicUuid(), 0, false);
		assertThat(under.countRequested()).isEqualTo(1);
		assertThat(under.unitsCreated()).isEqualTo(1);
	}

	@Test
	@DisplayName("saveAsTenantDefault writes settings.academic.defaultUnitCount")
	void saveAsTenantDefaultPersistsSetting() {
		Course course = newCourse("MAT");
		UUID tenantId = UUID.randomUUID();
		when(courseRepository.findByPublicUuid(course.getPublicUuid()))
				.thenReturn(Optional.of(course));
		when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
		when(unitRepository.countByCourse(course)).thenReturn(0L);
		stubSave();

		SeedUnitsResponse response = service.seedForCourse(course.getPublicUuid(), 6, true);

		assertThat(response.seeded()).isTrue();
		assertThat(response.unitsCreated()).isEqualTo(6);
		verify(tenantSettingsService).setDefaultUnitCount(tenantId, 6);

		ArgumentCaptor<Unit> unitCaptor = ArgumentCaptor.forClass(Unit.class);
		verify(unitRepository, times(6)).save(unitCaptor.capture());
		assertThat(unitCaptor.getAllValues())
				.extracting(Unit::getDisplayOrder)
				.containsExactly(1, 2, 3, 4, 5, 6);
	}

	@Test
	@DisplayName("TenantSettingsService.clampUnitCount clamps edges")
	void clampHelper() {
		assertThat(TenantSettingsService.clampUnitCount(8)).isEqualTo(8);
		assertThat(TenantSettingsService.clampUnitCount(0)).isEqualTo(1);
		assertThat(TenantSettingsService.clampUnitCount(-5)).isEqualTo(1);
		assertThat(TenantSettingsService.clampUnitCount(21)).isEqualTo(20);
		assertThat(TenantSettingsService.DEFAULT_UNIT_COUNT).isEqualTo(8);
	}

	private void stubSave() {
		when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> {
			Unit u = inv.getArgument(0);
			setField(u, "publicUuid", UUID.randomUUID());
			setField(u, "id", UUID.randomUUID());
			return u;
		});
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
