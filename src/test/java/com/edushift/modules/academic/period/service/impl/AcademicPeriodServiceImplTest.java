package com.edushift.modules.academic.period.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.period.dto.AcademicPeriodResponse;
import com.edushift.modules.academic.period.dto.CreateAcademicPeriodRequest;
import com.edushift.modules.academic.period.dto.UpdateAcademicPeriodRequest;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.period.entity.PeriodType;
import com.edushift.modules.academic.period.mapper.AcademicPeriodMapper;
import com.edushift.modules.academic.period.repository.AcademicPeriodRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class AcademicPeriodServiceImplTest {

	@Mock private AcademicPeriodRepository periodRepository;
	@Mock private AcademicYearRepository yearRepository;
	@Mock private TeacherAssignmentRepository teacherAssignmentRepository;
	@Spy private AcademicPeriodMapper mapper = new AcademicPeriodMapper();

	@InjectMocks private AcademicPeriodServiceImpl service;

	// =========================================================================
	// Auto-name generator (mapper)
	// =========================================================================

	@Nested
	@DisplayName("auto-name (roman)")
	class AutoName {

		@Test
		@DisplayName("ordinal 1..4 + BIMESTRE")
		void bimestres() {
			AcademicPeriodMapper m = new AcademicPeriodMapper();
			assertThat(m.generateName(PeriodType.BIMESTRE, 1)).isEqualTo("I Bimestre");
			assertThat(m.generateName(PeriodType.BIMESTRE, 2)).isEqualTo("II Bimestre");
			assertThat(m.generateName(PeriodType.BIMESTRE, 3)).isEqualTo("III Bimestre");
			assertThat(m.generateName(PeriodType.BIMESTRE, 4)).isEqualTo("IV Bimestre");
		}

		@Test
		@DisplayName("ordinal 1 + ANUAL")
		void anual() {
			AcademicPeriodMapper m = new AcademicPeriodMapper();
			assertThat(m.generateName(PeriodType.ANUAL, 1)).isEqualTo("I Anual");
		}

		@Test
		@DisplayName("rejects ordinal < 1 or > 99")
		void rejectsOutOfRange() {
			AcademicPeriodMapper m = new AcademicPeriodMapper();
			assertThatThrownBy(() -> m.generateName(PeriodType.BIMESTRE, 0))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> m.generateName(PeriodType.BIMESTRE, 100))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// =========================================================================
	// createPeriod
	// =========================================================================

	@Nested
	@DisplayName("createPeriod")
	class Create {

		@Test
		@DisplayName("happy path — first BIMESTRE auto-names + persists")
		void happyPath() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(0);
			when(periodRepository.findOverlap(any(), eq("BIMESTRE"), any(), any(), isNull()))
					.thenReturn(Optional.empty());
			when(periodRepository.saveAndFlush(any())).thenAnswer(inv -> {
				AcademicPeriod p = inv.getArgument(0);
				setField(p, "publicUuid", UUID.randomUUID());
				setField(p, "id", UUID.randomUUID());
				return p;
			});

			AcademicPeriodResponse response = service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2026-03-01"),
					LocalDate.parse("2026-05-15")));

			assertThat(response.name()).isEqualTo("I Bimestre");
			assertThat(response.ordinal()).isEqualTo(1);
		}

		@Test
		@DisplayName("year not found → 404")
		void yearNotFound() {
			when(yearRepository.findByPublicUuid(any())).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					UUID.randomUUID(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2026-03-01"),
					LocalDate.parse("2026-05-15"))))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("year CLOSED → 409 ACADEMIC_YEAR_LOCKED")
		void yearLocked() {
			AcademicYear year = newActiveYear("2025");
			year.setStatus(AcademicYearStatus.CLOSED);
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2025-03-01"),
					LocalDate.parse("2025-05-15"))))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
		}

		@Test
		@DisplayName("endDate < startDate → 400 PERIOD_DATE_INVERTED")
		void inverted() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2026-05-15"),
					LocalDate.parse("2026-03-01"))))
					.isInstanceOf(BusinessException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_DATE_INVERTED");
		}

		@Test
		@DisplayName("range outside year → 409 PERIOD_OUT_OF_YEAR_RANGE")
		void outOfRange() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2026-02-01"),  // before year start (2026-03-01)
					LocalDate.parse("2026-05-15"))))
					.isInstanceOf(ConflictException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_OUT_OF_YEAR_RANGE");
		}

		@Test
		@DisplayName("ordinal taken → 409 PERIOD_ORDINAL_TAKEN")
		void ordinalTaken() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(2);

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 2, null,
					LocalDate.parse("2026-03-01"),
					LocalDate.parse("2026-05-15"))))
					.isInstanceOf(ConflictException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_ORDINAL_TAKEN");
		}

		@Test
		@DisplayName("ordinal gap → 400 PERIOD_ORDINAL_GAP")
		void ordinalGap() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(2);  // existing 1 and 2

			// Try to create ordinal 4, leaving a gap at 3
			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 4, null,
					LocalDate.parse("2026-08-01"),
					LocalDate.parse("2026-09-30"))))
					.isInstanceOf(BusinessException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_ORDINAL_GAP");
		}

		@Test
		@DisplayName("date overlap → 409 PERIOD_DATE_OVERLAP")
		void overlap() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(0);
			when(periodRepository.findOverlap(any(), eq("BIMESTRE"), any(), any(), isNull()))
					.thenReturn(Optional.of(UUID.randomUUID()));

			assertThatThrownBy(() -> service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, null,
					LocalDate.parse("2026-03-01"),
					LocalDate.parse("2026-05-15"))))
					.isInstanceOf(ConflictException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_DATE_OVERLAP");
		}

		@Test
		@DisplayName("explicit name overrides auto-generation")
		void explicitName() {
			AcademicYear year = newActiveYear("2026");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(0);
			when(periodRepository.findOverlap(any(), any(), any(), any(), any()))
					.thenReturn(Optional.empty());
			when(periodRepository.saveAndFlush(any())).thenAnswer(inv -> {
				AcademicPeriod p = inv.getArgument(0);
				setField(p, "publicUuid", UUID.randomUUID());
				return p;
			});

			AcademicPeriodResponse response = service.createPeriod(new CreateAcademicPeriodRequest(
					year.getPublicUuid(), PeriodType.BIMESTRE, 1, "  Bimestre Inicial  ",
					LocalDate.parse("2026-03-01"),
					LocalDate.parse("2026-05-15")));

			assertThat(response.name()).isEqualTo("Bimestre Inicial");
		}
	}

	// =========================================================================
	// updatePeriod
	// =========================================================================

	@Nested
	@DisplayName("updatePeriod")
	class Update {

		@Test
		@DisplayName("update name only — no overlap call needed")
		void renameOnly() {
			AcademicYear year = newActiveYear("2026");
			AcademicPeriod period = newPeriod(year, PeriodType.BIMESTRE, 1,
					LocalDate.parse("2026-03-01"), LocalDate.parse("2026-05-15"));

			when(periodRepository.findByPublicUuid(period.getPublicUuid()))
					.thenReturn(Optional.of(period));
			when(periodRepository.findOverlap(any(), any(), any(), any(),
					eq(period.getId()))).thenReturn(Optional.empty());
			when(periodRepository.saveAndFlush(period)).thenReturn(period);

			service.updatePeriod(period.getPublicUuid(),
					new UpdateAcademicPeriodRequest("Renombrado", null, null));

			assertThat(period.getName()).isEqualTo("Renombrado");
		}

		@Test
		@DisplayName("empty patch → no save")
		void emptyPatch() {
			AcademicYear year = newActiveYear("2026");
			AcademicPeriod period = newPeriod(year, PeriodType.BIMESTRE, 1,
					LocalDate.parse("2026-03-01"), LocalDate.parse("2026-05-15"));
			when(periodRepository.findByPublicUuid(period.getPublicUuid()))
					.thenReturn(Optional.of(period));

			service.updatePeriod(period.getPublicUuid(),
					new UpdateAcademicPeriodRequest(null, null, null));

			verify(periodRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("year CLOSED rejects edit")
		void yearLocked() {
			AcademicYear year = newActiveYear("2025");
			year.setStatus(AcademicYearStatus.CLOSED);
			AcademicPeriod period = newPeriod(year, PeriodType.BIMESTRE, 1,
					LocalDate.parse("2025-03-01"), LocalDate.parse("2025-05-15"));
			when(periodRepository.findByPublicUuid(period.getPublicUuid()))
					.thenReturn(Optional.of(period));

			assertThatThrownBy(() -> service.updatePeriod(period.getPublicUuid(),
					new UpdateAcademicPeriodRequest("X", null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
		}
	}

	// =========================================================================
	// deletePeriod
	// =========================================================================

	@Nested
	@DisplayName("deletePeriod")
	class Delete {

		@Test
		@DisplayName("highest ordinal → ok")
		void deletesLastOrdinal() {
			AcademicYear year = newActiveYear("2026");
			AcademicPeriod period = newPeriod(year, PeriodType.BIMESTRE, 4,
					LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-15"));
			when(periodRepository.findByPublicUuid(period.getPublicUuid()))
					.thenReturn(Optional.of(period));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(4);

			service.deletePeriod(period.getPublicUuid());

			verify(periodRepository).delete(period);
		}

		@Test
		@DisplayName("middle ordinal → 409 PERIOD_NOT_LAST_ORDINAL")
		void middleOrdinalRejected() {
			AcademicYear year = newActiveYear("2026");
			AcademicPeriod period = newPeriod(year, PeriodType.BIMESTRE, 2,
					LocalDate.parse("2026-05-16"), LocalDate.parse("2026-07-31"));
			when(periodRepository.findByPublicUuid(period.getPublicUuid()))
					.thenReturn(Optional.of(period));
			when(periodRepository.findMaxOrdinalByYearAndType(year, PeriodType.BIMESTRE))
					.thenReturn(4);

			assertThatThrownBy(() -> service.deletePeriod(period.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasFieldOrPropertyWithValue("code", "PERIOD_NOT_LAST_ORDINAL");
			verify(periodRepository, never()).delete(any(AcademicPeriod.class));
		}
	}

	// =========================================================================
	// helpers
	// =========================================================================

	private static AcademicYear newActiveYear(String name) {
		AcademicYear y = new AcademicYear();
		y.setName(name);
		y.setStartDate(LocalDate.parse(name + "-03-01"));
		y.setEndDate(LocalDate.parse(name + "-12-15"));
		y.setStatus(AcademicYearStatus.ACTIVE);
		setField(y, "publicUuid", UUID.randomUUID());
		setField(y, "id", UUID.randomUUID());
		return y;
	}

	private static AcademicPeriod newPeriod(AcademicYear year, PeriodType type, int ordinal,
			LocalDate start, LocalDate end) {
		AcademicPeriod p = new AcademicPeriod();
		p.setAcademicYear(year);
		p.setPeriodType(type);
		p.setOrdinal(ordinal);
		p.setName(type.displayLabel() + " " + ordinal);
		p.setStartDate(start);
		p.setEndDate(end);
		setField(p, "publicUuid", UUID.randomUUID());
		setField(p, "id", UUID.randomUUID());
		return p;
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
