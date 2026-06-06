package com.edushift.modules.academic.year.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.year.dto.AcademicYearListItem;
import com.edushift.modules.academic.year.dto.AcademicYearResponse;
import com.edushift.modules.academic.year.dto.CreateAcademicYearRequest;
import com.edushift.modules.academic.year.dto.UpdateAcademicYearRequest;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.mapper.AcademicYearMapper;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDate;
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

/**
 * Unit tests for {@link AcademicYearServiceImpl} (Sprint 4 — BE-4.1).
 *
 * <p>The mapper is wired as a real {@link Spy} so the assertions can read
 * the actual projection without a separate mapper test mock.</p>
 */
@ExtendWith(MockitoExtension.class)
class AcademicYearServiceImplTest {

	@Mock private AcademicYearRepository repository;
	@Spy private AcademicYearMapper mapper = new AcademicYearMapper();

	@InjectMocks private AcademicYearServiceImpl service;

	// ===========================================================================
	// listYears
	// ===========================================================================

	@Nested
	@DisplayName("listYears")
	class ListYears {

		@Test
		@DisplayName("no filter — sorts ACTIVE first then by startDate desc")
		void sortsActiveFirstThenByDateDesc() {
			AcademicYear y2024 = newYear("2024", AcademicYearStatus.CLOSED,
					LocalDate.of(2024, 3, 1), LocalDate.of(2024, 12, 15));
			AcademicYear y2025 = newYear("2025", AcademicYearStatus.CLOSED,
					LocalDate.of(2025, 3, 1), LocalDate.of(2025, 12, 15));
			AcademicYear y2026 = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			AcademicYear yActive = newYear("2025-2", AcademicYearStatus.ACTIVE,
					LocalDate.of(2025, 8, 1), LocalDate.of(2026, 6, 15));
			when(repository.findAllByOrderByStartDateDesc())
					.thenReturn(List.of(y2026, yActive, y2025, y2024));

			List<AcademicYearListItem> result = service.listYears(null);

			assertThat(result).hasSize(4);
			assertThat(result.get(0).status()).isEqualTo(AcademicYearStatus.ACTIVE);
			// Then PLANNING/CLOSED sorted by startDate desc: 2026 → 2025 → 2024
			assertThat(result.get(1).name()).isEqualTo("2026");
			assertThat(result.get(2).name()).isEqualTo("2025");
			assertThat(result.get(3).name()).isEqualTo("2024");
		}

		@Test
		@DisplayName("status filter — delegates to repo")
		void statusFilter() {
			AcademicYear y = newYear("2026", AcademicYearStatus.ACTIVE,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findAllByStatusOrderByStartDateDesc(AcademicYearStatus.ACTIVE))
					.thenReturn(List.of(y));

			List<AcademicYearListItem> result = service.listYears(AcademicYearStatus.ACTIVE);

			assertThat(result).hasSize(1);
			verify(repository, never()).findAllByOrderByStartDateDesc();
		}
	}

	// ===========================================================================
	// getYear
	// ===========================================================================

	@Nested
	@DisplayName("getYear")
	class GetYear {

		@Test
		@DisplayName("happy path — returns full projection")
		void happyPath() {
			AcademicYear y = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			AcademicYearResponse response = service.getYear(y.getPublicUuid());

			assertThat(response.name()).isEqualTo("2026");
			assertThat(response.status()).isEqualTo(AcademicYearStatus.PLANNING);
		}

		@Test
		@DisplayName("unknown publicUuid → 404 RESOURCE_NOT_FOUND")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(repository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getYear(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// createYear
	// ===========================================================================

	@Nested
	@DisplayName("createYear")
	class CreateYear {

		@Test
		@DisplayName("happy path — saves with status PLANNING")
		void happyPath() {
			CreateAcademicYearRequest req = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByNameIgnoreCase("2026")).thenReturn(Optional.empty());
			when(repository.saveAndFlush(any())).thenAnswer(inv -> {
				AcademicYear y = inv.getArgument(0);
				setField(y, "id", UUID.randomUUID());
				return y;
			});

			AcademicYearResponse response = service.createYear(req);

			assertThat(response.name()).isEqualTo("2026");
			assertThat(response.status()).isEqualTo(AcademicYearStatus.PLANNING);
		}

		@Test
		@DisplayName("name already taken (case-insensitive) → 409 ACADEMIC_YEAR_NAME_TAKEN")
		void nameTaken() {
			AcademicYear existing = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByNameIgnoreCase("2026")).thenReturn(Optional.of(existing));

			CreateAcademicYearRequest req = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

			assertThatThrownBy(() -> service.createYear(req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("2026");
			verify(repository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("startDate >= endDate → 409 ACADEMIC_YEAR_INVALID_DATE_RANGE")
		void invalidDateRange() {
			CreateAcademicYearRequest req = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 12, 15), LocalDate.of(2026, 3, 1));

			assertThatThrownBy(() -> service.createYear(req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("startDate must be strictly before endDate");
			verify(repository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("race: DB unique violates after pre-check → 409 ACADEMIC_YEAR_NAME_TAKEN with cause")
		void raceCondition() {
			when(repository.findByNameIgnoreCase("2026")).thenReturn(Optional.empty());
			when(repository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_academic_years_tenant_name_active"));

			CreateAcademicYearRequest req = new CreateAcademicYearRequest(
					"2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));

			assertThatThrownBy(() -> service.createYear(req))
					.isInstanceOf(ConflictException.class)
					.hasCauseInstanceOf(DataIntegrityViolationException.class);
		}
	}

	// ===========================================================================
	// updateYear
	// ===========================================================================

	@Nested
	@DisplayName("updateYear")
	class UpdateYear {

		@Test
		@DisplayName("happy path — applies patch")
		void happyPath() {
			AcademicYear y = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));
			when(repository.saveAndFlush(y)).thenReturn(y);

			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(
					"Año 2026", null, null);

			AcademicYearResponse response = service.updateYear(y.getPublicUuid(), patch);

			assertThat(response.name()).isEqualTo("Año 2026");
			assertThat(y.getName()).isEqualTo("Año 2026");
		}

		@Test
		@DisplayName("empty patch — returns current state without persisting")
		void emptyPatchIsNoOp() {
			AcademicYear y = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(null, null, null);

			service.updateYear(y.getPublicUuid(), patch);

			verify(repository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("CLOSED year → 409 ACADEMIC_YEAR_LOCKED")
		void closedIsLocked() {
			AcademicYear y = newYear("2025", AcademicYearStatus.CLOSED,
					LocalDate.of(2025, 3, 1), LocalDate.of(2025, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(
					"2025-renamed", null, null);

			assertThatThrownBy(() -> service.updateYear(y.getPublicUuid(), patch))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("read-only");
		}

		@Test
		@DisplayName("invalid date range using fallback to existing dates → 409")
		void invalidDateRangeWithFallback() {
			AcademicYear y = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			// Only update startDate to AFTER the existing endDate → invalid
			UpdateAcademicYearRequest patch = new UpdateAcademicYearRequest(
					null, LocalDate.of(2027, 1, 1), null);

			assertThatThrownBy(() -> service.updateYear(y.getPublicUuid(), patch))
					.isInstanceOf(ConflictException.class);
		}
	}

	// ===========================================================================
	// activateYear
	// ===========================================================================

	@Nested
	@DisplayName("activateYear")
	class ActivateYear {

		@Test
		@DisplayName("PLANNING → ACTIVE; closes the previous ACTIVE in the same tx")
		void activatesAndClosesPrevious() {
			AcademicYear current = newYear("2025", AcademicYearStatus.ACTIVE,
					LocalDate.of(2025, 3, 1), LocalDate.of(2025, 12, 15));
			AcademicYear target = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(target.getPublicUuid())).thenReturn(Optional.of(target));
			when(repository.findFirstByStatus(AcademicYearStatus.ACTIVE)).thenReturn(Optional.of(current));
			when(repository.saveAndFlush(target)).thenReturn(target);

			AcademicYearResponse response = service.activateYear(target.getPublicUuid());

			assertThat(response.status()).isEqualTo(AcademicYearStatus.ACTIVE);
			assertThat(current.getStatus()).isEqualTo(AcademicYearStatus.CLOSED);
			verify(repository).save(current);
			verify(repository).saveAndFlush(target);
		}

		@Test
		@DisplayName("idempotent on already-ACTIVE target")
		void idempotentOnActive() {
			AcademicYear target = newYear("2026", AcademicYearStatus.ACTIVE,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(target.getPublicUuid())).thenReturn(Optional.of(target));

			AcademicYearResponse response = service.activateYear(target.getPublicUuid());

			assertThat(response.status()).isEqualTo(AcademicYearStatus.ACTIVE);
			verify(repository, never()).saveAndFlush(any());
			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("CLOSED target → 409 ACADEMIC_YEAR_NOT_ACTIVATABLE")
		void closedNotActivatable() {
			AcademicYear target = newYear("2024", AcademicYearStatus.CLOSED,
					LocalDate.of(2024, 3, 1), LocalDate.of(2024, 12, 15));
			when(repository.findByPublicUuid(target.getPublicUuid())).thenReturn(Optional.of(target));

			assertThatThrownBy(() -> service.activateYear(target.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
			verify(repository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("activating when no previous ACTIVE — only saves the target")
		void firstYearActivation() {
			AcademicYear target = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(target.getPublicUuid())).thenReturn(Optional.of(target));
			when(repository.findFirstByStatus(AcademicYearStatus.ACTIVE)).thenReturn(Optional.empty());
			when(repository.saveAndFlush(target)).thenReturn(target);

			service.activateYear(target.getPublicUuid());

			verify(repository, times(1)).saveAndFlush(target);
			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("race: another tx activated concurrently → 409 ACADEMIC_YEAR_ALREADY_ACTIVE")
		void concurrentActivateRace() {
			AcademicYear target = newYear("2026", AcademicYearStatus.PLANNING,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(target.getPublicUuid())).thenReturn(Optional.of(target));
			when(repository.findFirstByStatus(AcademicYearStatus.ACTIVE)).thenReturn(Optional.empty());
			when(repository.saveAndFlush(target))
					.thenThrow(new DataIntegrityViolationException("uk_academic_years_tenant_active"));

			assertThatThrownBy(() -> service.activateYear(target.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("concurrently");
		}
	}

	// ===========================================================================
	// deleteYear
	// ===========================================================================

	@Nested
	@DisplayName("deleteYear")
	class DeleteYear {

		@Test
		@DisplayName("PLANNING year — soft-deletes")
		void softDeletesPlanning() {
			AcademicYear y = newYear("2027", AcademicYearStatus.PLANNING,
					LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			service.deleteYear(y.getPublicUuid());

			verify(repository).delete(y);
		}

		@Test
		@DisplayName("CLOSED year — soft-deletes too (already archived)")
		void softDeletesClosed() {
			AcademicYear y = newYear("2024", AcademicYearStatus.CLOSED,
					LocalDate.of(2024, 3, 1), LocalDate.of(2024, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			service.deleteYear(y.getPublicUuid());

			verify(repository).delete(y);
		}

		@Test
		@DisplayName("ACTIVE year — 409 ACADEMIC_YEAR_IN_USE")
		void activeIsBlocked() {
			AcademicYear y = newYear("2026", AcademicYearStatus.ACTIVE,
					LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 15));
			when(repository.findByPublicUuid(y.getPublicUuid())).thenReturn(Optional.of(y));

			assertThatThrownBy(() -> service.deleteYear(y.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Active");
			verify(repository, never()).delete(y);
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(repository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteYear(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// ===========================================================================
	// helpers
	// ===========================================================================

	private static AcademicYear newYear(String name, AcademicYearStatus status,
			LocalDate start, LocalDate end) {
		AcademicYear y = new AcademicYear();
		y.setName(name);
		y.setStatus(status);
		y.setStartDate(start);
		y.setEndDate(end);
		setField(y, "publicUuid", UUID.randomUUID());
		setField(y, "id", UUID.randomUUID());
		return y;
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
