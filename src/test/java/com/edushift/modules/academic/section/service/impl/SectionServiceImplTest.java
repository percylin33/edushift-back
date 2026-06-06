package com.edushift.modules.academic.section.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
import com.edushift.modules.academic.section.dto.CreateSectionRequest;
import com.edushift.modules.academic.section.dto.SectionListItem;
import com.edushift.modules.academic.section.dto.SectionResponse;
import com.edushift.modules.academic.section.dto.UpdateSectionRequest;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.mapper.SectionMapper;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.entity.AcademicYearStatus;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.edushift.shared.multitenancy.TenantContext;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Unit tests for {@link SectionServiceImpl} (Sprint 4 — BE-4.3).
 *
 * <p>The {@link TenantContext} is bound to {@link #TENANT_ID} for every
 * test, mirroring the value the {@code TenantFilter} would set in a
 * real request, and cleared after each test.</p>
 */
@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

	@Mock private SectionRepository sectionRepository;
	@Mock private AcademicYearRepository yearRepository;
	@Mock private GradeRepository gradeRepository;
	@Mock private AcademicLevelRepository levelRepository;
	@Mock private StudentEnrollmentRepository enrollmentRepository;
	@Spy private SectionMapper mapper = new SectionMapper();

	@InjectMocks private SectionServiceImpl service;

	private static final UUID TENANT_ID = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");

	@BeforeEach
	void bindTenant() {
		TenantContext.set(TENANT_ID);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	// =========================================================================
	// listSections
	// =========================================================================

	@Nested
	@DisplayName("listSections")
	class ListSections {

		@Test
		@DisplayName("no params → uses ACTIVE year by default")
		void defaultsToActiveYear() {
			AcademicYear active = newYear("2026", AcademicYearStatus.ACTIVE);
			Section s1 = newSection(active, newGrade("PRIMARIA", "1ro", 1), "A");
			when(yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE))
					.thenReturn(Optional.of(active));
			when(sectionRepository.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(active))
					.thenReturn(List.of(s1));

			List<SectionListItem> result = service.listSections(null, null, null);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).name()).isEqualTo("A");
			assertThat(result.get(0).academicYearStatus()).isEqualTo("ACTIVE");
		}

		@Test
		@DisplayName("no params + no ACTIVE year → empty list (not 404)")
		void noActiveReturnsEmpty() {
			when(yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE))
					.thenReturn(Optional.empty());

			List<SectionListItem> result = service.listSections(null, null, null);

			assertThat(result).isEmpty();
			verify(sectionRepository, never())
					.findAllByAcademicYearOrderByDisplayOrderAscNameAsc(any());
		}

		@Test
		@DisplayName("explicit unknown year → 404")
		void unknownYear() {
			UUID id = UUID.randomUUID();
			when(yearRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listSections(id, null, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("gradeId provided → filters by year + grade")
		void filterByGrade() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));
			when(sectionRepository
					.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade))
					.thenReturn(List.of());

			service.listSections(null, grade.getPublicUuid(), null);

			verify(sectionRepository)
					.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade);
		}

		@Test
		@DisplayName("levelId provided → filters by year + level via grade.level")
		void filterByLevel() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			AcademicLevel level = newLevel("PRIMARIA", "Primaria");
			when(yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE))
					.thenReturn(Optional.of(year));
			when(levelRepository.findByPublicUuid(level.getPublicUuid()))
					.thenReturn(Optional.of(level));
			when(sectionRepository.findAllByYearAndLevel(year, level)).thenReturn(List.of());

			service.listSections(null, null, level.getPublicUuid());

			verify(sectionRepository).findAllByYearAndLevel(year, level);
		}

		@Test
		@DisplayName("gradeId + levelId both provided → gradeId wins (stricter scope)")
		void gradeBeatsLevel() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findFirstByStatus(AcademicYearStatus.ACTIVE))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));
			when(sectionRepository
					.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade))
					.thenReturn(List.of());

			service.listSections(null, grade.getPublicUuid(), UUID.randomUUID());

			verify(sectionRepository)
					.findAllByAcademicYearAndGradeOrderByDisplayOrderAscNameAsc(year, grade);
			verify(sectionRepository, never()).findAllByYearAndLevel(any(), any());
		}
	}

	// =========================================================================
	// getSection
	// =========================================================================

	@Nested
	@DisplayName("getSection")
	class GetSection {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));

			SectionResponse response = service.getSection(section.getPublicUuid());

			assertThat(response.name()).isEqualTo("A");
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(sectionRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getSection(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createSection
	// =========================================================================

	@Nested
	@DisplayName("createSection")
	class CreateSection {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));
			when(sectionRepository.findByYearGradeAndNameIgnoreCase(year, grade, "A"))
					.thenReturn(Optional.empty());
			when(sectionRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Section s = inv.getArgument(0);
				setField(s, "publicUuid", UUID.randomUUID());
				return s;
			});

			SectionResponse response = service.createSection(new CreateSectionRequest(
					year.getPublicUuid(), grade.getPublicUuid(), "A", 30, 1));

			assertThat(response.name()).isEqualTo("A");
			assertThat(response.capacity()).isEqualTo(30);
		}

		@Test
		@DisplayName("year CLOSED → 409 ACADEMIC_YEAR_LOCKED")
		void yearClosed() {
			AcademicYear year = newYear("2024", AcademicYearStatus.CLOSED);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));

			assertThatThrownBy(() -> service.createSection(new CreateSectionRequest(
					year.getPublicUuid(), grade.getPublicUuid(), "A", null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
			verify(sectionRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("name already taken in (year, grade) → 409 SECTION_NAME_TAKEN")
		void nameTaken() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			Section existing = newSection(year, grade, "A");
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));
			when(sectionRepository.findByYearGradeAndNameIgnoreCase(year, grade, "A"))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createSection(new CreateSectionRequest(
					year.getPublicUuid(), grade.getPublicUuid(), "A", null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("'A'");
		}

		@Test
		@DisplayName("year cross-tenant → 404 (anti-enumeration)")
		void crossTenantYear() {
			AcademicYear year = newYearWithTenant("2026", AcademicYearStatus.ACTIVE,
					UUID.randomUUID());
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));

			assertThatThrownBy(() -> service.createSection(new CreateSectionRequest(
					year.getPublicUuid(), grade.getPublicUuid(), "A", null, null)))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("DB unique violation → 409 SECTION_NAME_TAKEN")
		void raceCondition() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			when(yearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid()))
					.thenReturn(Optional.of(grade));
			when(sectionRepository.findByYearGradeAndNameIgnoreCase(year, grade, "A"))
					.thenReturn(Optional.empty());
			when(sectionRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_sections_year_grade_name_active"));

			assertThatThrownBy(() -> service.createSection(new CreateSectionRequest(
					year.getPublicUuid(), grade.getPublicUuid(), "A", null, null)))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// updateSection
	// =========================================================================

	@Nested
	@DisplayName("updateSection")
	class UpdateSection {

		@Test
		@DisplayName("happy path — updates name + capacity")
		void happyPath() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Grade grade = newGrade("PRIMARIA", "1ro", 1);
			Section section = newSection(year, grade, "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));
			when(sectionRepository.findByYearGradeAndNameIgnoreCase(year, grade, "A1"))
					.thenReturn(Optional.empty());
			when(sectionRepository.saveAndFlush(section)).thenReturn(section);

			SectionResponse response = service.updateSection(section.getPublicUuid(),
					new UpdateSectionRequest("A1", 25, null));

			assertThat(response.name()).isEqualTo("A1");
			assertThat(response.capacity()).isEqualTo(25);
		}

		@Test
		@DisplayName("empty patch → no save")
		void emptyPatch() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));

			service.updateSection(section.getPublicUuid(),
					new UpdateSectionRequest(null, null, null));

			verify(sectionRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("year CLOSED → 409 ACADEMIC_YEAR_LOCKED")
		void yearClosed() {
			AcademicYear year = newYear("2024", AcademicYearStatus.CLOSED);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));

			assertThatThrownBy(() -> service.updateSection(section.getPublicUuid(),
					new UpdateSectionRequest("A1", null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
		}
	}

	// =========================================================================
	// deleteSection
	// =========================================================================

	@Nested
	@DisplayName("deleteSection")
	class DeleteSection {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));

			service.deleteSection(section.getPublicUuid());

			verify(sectionRepository).delete(section);
		}

		@Test
		@DisplayName("year CLOSED → 409 ACADEMIC_YEAR_LOCKED")
		void yearClosed() {
			AcademicYear year = newYear("2024", AcademicYearStatus.CLOSED);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));

			assertThatThrownBy(() -> service.deleteSection(section.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
			verify(sectionRepository, never()).delete(any());
		}

		@Test
		@DisplayName("section with active enrollments → 409 SECTION_HAS_ENROLLMENTS (BE-4.8)")
		void sectionHasEnrollments() {
			AcademicYear year = newYear("2026", AcademicYearStatus.ACTIVE);
			Section section = newSection(year, newGrade("PRIMARIA", "1ro", 1), "A");
			when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
					.thenReturn(Optional.of(section));
			when(enrollmentRepository.existsActiveBySection(section)).thenReturn(true);

			assertThatThrownBy(() -> service.deleteSection(section.getPublicUuid()))
					.isInstanceOfSatisfying(ConflictException.class,
							ex -> assertThat(ex.getCode()).isEqualTo("SECTION_HAS_ENROLLMENTS"))
					.hasMessageContaining("active student enrollments");
			verify(sectionRepository, never()).delete(any());
		}
	}

	// =========================================================================
	// helpers
	// =========================================================================

	private static AcademicYear newYear(String name, AcademicYearStatus status) {
		return newYearWithTenant(name, status, TENANT_ID);
	}

	private static AcademicYear newYearWithTenant(String name, AcademicYearStatus status,
			UUID tenantId) {
		AcademicYear y = new AcademicYear();
		y.setName(name);
		y.setStatus(status);
		y.setStartDate(LocalDate.of(2026, 3, 1));
		y.setEndDate(LocalDate.of(2026, 12, 15));
		setField(y, "publicUuid", UUID.randomUUID());
		setField(y, "id", UUID.randomUUID());
		setField(y, "tenantId", tenantId);
		return y;
	}

	private static AcademicLevel newLevel(String code, String name) {
		AcademicLevel l = new AcademicLevel();
		l.setCode(code);
		l.setName(name);
		l.setOrdinal(1);
		setField(l, "publicUuid", UUID.randomUUID());
		setField(l, "id", UUID.randomUUID());
		setField(l, "tenantId", TENANT_ID);
		return l;
	}

	private static Grade newGrade(String levelCode, String name, int ordinal) {
		AcademicLevel level = newLevel(levelCode, levelCode);
		Grade g = new Grade();
		g.setLevel(level);
		g.setName(name);
		g.setOrdinal(ordinal);
		setField(g, "publicUuid", UUID.randomUUID());
		setField(g, "id", UUID.randomUUID());
		setField(g, "tenantId", TENANT_ID);
		return g;
	}

	private static Section newSection(AcademicYear year, Grade grade, String name) {
		Section s = new Section();
		s.setAcademicYear(year);
		s.setGrade(grade);
		s.setName(name);
		s.setDisplayOrder(1);
		setField(s, "publicUuid", UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		setField(s, "tenantId", TENANT_ID);
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
}
