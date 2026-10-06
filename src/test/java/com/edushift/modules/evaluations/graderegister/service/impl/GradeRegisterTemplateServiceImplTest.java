package com.edushift.modules.evaluations.graderegister.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.year.entity.AcademicYear;
import com.edushift.modules.academic.year.repository.AcademicYearRepository;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.graderegister.dto.AdoptGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.dto.UpdateGradeRegisterTemplateRequest;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateListItem;
import com.edushift.modules.evaluations.graderegister.dto.GradeRegisterTemplateResponse;
import com.edushift.modules.evaluations.graderegister.entity.GradeCategory;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplate;
import com.edushift.modules.evaluations.graderegister.entity.GradeRegisterTemplateScope;
import com.edushift.modules.evaluations.graderegister.mapper.GradeRegisterTemplateMapper;
import com.edushift.modules.evaluations.graderegister.repository.GradeCategoryRepository;
import com.edushift.modules.evaluations.graderegister.repository.GradeRegisterTemplateRepository;
import com.edushift.modules.evaluations.graderegister.service.GradeRegisterSeedService;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GradeRegisterTemplateServiceImpl} (formal grade register F1/F5).
 */
@ExtendWith(MockitoExtension.class)
class GradeRegisterTemplateServiceImplTest {

	@Mock private GradeRegisterTemplateRepository templateRepository;
	@Mock private GradeCategoryRepository categoryRepository;
	@Mock private AcademicYearRepository academicYearRepository;
	@Mock private AcademicLevelRepository academicLevelRepository;
	@Mock private CourseRepository courseRepository;
	@Mock private GradeRegisterSeedService seedService;
	@Mock private EvaluationRepository evaluationRepository;
	@Mock private GradeRecordRepository gradeRecordRepository;
	@Spy private GradeRegisterTemplateMapper mapper = new GradeRegisterTemplateMapper();

	@InjectMocks private GradeRegisterTemplateServiceImpl service;

	@Nested
	@DisplayName("listCatalog")
	class ListCatalog {

		@Test
		@DisplayName("delegates to seedService.materializeCatalog")
		void delegatesToSeedService() {
			GradeRegisterTemplateListItem item = new GradeRegisterTemplateListItem(
					UUID.randomUUID(), "MINEDU", "desc",
					GradeRegisterTemplateScope.TENANT, true, false,
					"MINEDU_STD", null, 3, List.of("PRIMARIA"),
					null, null, null, null, null);
			when(seedService.materializeCatalog()).thenReturn(List.of(item));

			List<GradeRegisterTemplateListItem> result = service.listCatalog();

			assertThat(result).containsExactly(item);
			verify(seedService).materializeCatalog();
			verify(templateRepository, never()).findByIsSystemTrueOrderByNameAsc();
		}
	}

	@Nested
	@DisplayName("adopt")
	class Adopt {

		@Test
		@DisplayName("rejects non-system source → GRADE_REGISTER_NOT_CATALOG")
		void rejectsNonSystemSource() {
			GradeRegisterTemplate tenantCopy = newTemplate("Local copy", false);
			when(templateRepository.findByPublicUuid(tenantCopy.getPublicUuid()))
					.thenReturn(Optional.of(tenantCopy));

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					UUID.randomUUID().toString(),
					GradeRegisterTemplateScope.TENANT,
					null,
					null,
					true,
					null);

			assertThatThrownBy(() -> service.adopt(tenantCopy.getPublicUuid(), req))
					.isInstanceOf(BadRequestException.class)
					.extracting(ex -> ((BadRequestException) ex).getCode())
					.isEqualTo("GRADE_REGISTER_NOT_CATALOG");
			verify(templateRepository, never()).save(any());
			verify(categoryRepository, never()).save(any());
		}

		@Test
		@DisplayName("clones categories when source isSystem=true")
		void clonesCategoriesFromSystemCatalog() {
			GradeRegisterTemplate source = newTemplate("Catalog MINEDU", true);
			source.setCatalogCode("MINEDU_STD");
			GradeCategory srcCat = newCategory(source, "Participación", 1, "40.00");

			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");

			when(templateRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(academicYearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(courseRepository.findByPublicUuid(course.getPublicUuid()))
					.thenReturn(Optional.of(course));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(source))
					.thenReturn(List.of(srcCat));
			when(templateRepository.save(any(GradeRegisterTemplate.class))).thenAnswer(inv -> {
				GradeRegisterTemplate t = inv.getArgument(0);
				if (t.getPublicUuid() == null) {
					t.setPublicUuid(UUID.randomUUID());
				}
				setField(t, "id", UUID.randomUUID());
				return t;
			});
			when(categoryRepository.save(any(GradeCategory.class))).thenAnswer(inv -> {
				GradeCategory c = inv.getArgument(0);
				if (c.getPublicUuid() == null) {
					c.setPublicUuid(UUID.randomUUID());
				}
				setField(c, "id", UUID.randomUUID());
				return c;
			});
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(
					org.mockito.ArgumentMatchers.argThat(
							t -> t != null && !Boolean.TRUE.equals(t.getIsSystem()))))
					.thenAnswer(inv -> {
						GradeRegisterTemplate copy = inv.getArgument(0);
						GradeCategory cloned = newCategory(copy, "Participación", 1, "40.00");
						return List.of(cloned);
					});

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					year.getPublicUuid().toString(),
					GradeRegisterTemplateScope.COURSE,
					course.getPublicUuid().toString(),
					null,
					false,
					null);

			GradeRegisterTemplateResponse response =
					service.adopt(source.getPublicUuid(), req);

			assertThat(response.name()).isEqualTo("Catalog MINEDU");
			assertThat(response.system()).isFalse();
			assertThat(response.scope()).isEqualTo(GradeRegisterTemplateScope.COURSE);
			assertThat(response.sourceTemplatePublicUuid()).isEqualTo(source.getPublicUuid());
			assertThat(response.categories()).hasSize(1);
			assertThat(response.categories().get(0).name()).isEqualTo("Participación");

			ArgumentCaptor<GradeRegisterTemplate> templateCaptor =
					ArgumentCaptor.forClass(GradeRegisterTemplate.class);
			verify(templateRepository, times(1)).save(templateCaptor.capture());
			GradeRegisterTemplate saved = templateCaptor.getValue();
			assertThat(saved.getIsSystem()).isFalse();
			assertThat(saved.getCatalogCode()).isNull();
			assertThat(saved.getSourceTemplate()).isSameAs(source);
			assertThat(saved.getAcademicYear()).isSameAs(year);
			assertThat(saved.getCourse()).isSameAs(course);
			assertThat(saved.getPublicUuid()).isNotNull();

			ArgumentCaptor<GradeCategory> catCaptor =
					ArgumentCaptor.forClass(GradeCategory.class);
			verify(categoryRepository, times(1)).save(catCaptor.capture());
			GradeCategory savedCat = catCaptor.getValue();
			assertThat(savedCat.getName()).isEqualTo("Participación");
			assertThat(savedCat.getWeight()).isEqualByComparingTo("40.00");
			assertThat(savedCat.getTemplate()).isSameAs(saved);
			assertThat(savedCat.getPublicUuid()).isNotEqualTo(srcCat.getPublicUuid());
		}
	}

	@Nested
	@DisplayName("adopt replaceActive")
	class AdoptReplaceActive {

		@Test
		@DisplayName("blocks replace when evaluations linked → GRADE_REGISTER_IN_USE")
		void blocksWhenInUse() {
			GradeRegisterTemplate source = newTemplate("Catalog", true);
			source.setCatalogCode("STD_BIMESTRE");
			AcademicYear year = newYear("2026");

			GradeRegisterTemplate active = newTemplate("Active", false);
			active.setAcademicYear(year);
			active.setScope(GradeRegisterTemplateScope.TENANT);
			active.setIsDefault(Boolean.TRUE);
			GradeCategory cat = newCategory(active, "Participación", 1, "30");

			when(templateRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(academicYearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(templateRepository.findDefaultTenantTemplate(year))
					.thenReturn(Optional.of(active));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(active))
					.thenReturn(List.of(cat));
			when(evaluationRepository.countByCategoriesAndYear(List.of(cat), year))
					.thenReturn(3L);
			when(gradeRecordRepository.countByCategoriesAndYear(List.of(cat), year))
					.thenReturn(10L);

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					year.getPublicUuid().toString(),
					GradeRegisterTemplateScope.TENANT,
					null,
					null,
					true,
					true);

			assertThatThrownBy(() -> service.adopt(source.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.extracting(ex -> ((ConflictException) ex).getCode())
					.isEqualTo("GRADE_REGISTER_IN_USE");
			verify(templateRepository, never()).save(any());
		}

		@Test
		@DisplayName("archives unused active then adopts when replaceActive=true")
		void archivesUnusedThenAdopts() {
			GradeRegisterTemplate source = newTemplate("Catalog", true);
			source.setCatalogCode("STD_BIMESTRE");
			GradeCategory srcCat = newCategory(source, "Participación", 1, "30");
			AcademicYear year = newYear("2026");

			GradeRegisterTemplate active = newTemplate("Old active", false);
			active.setAcademicYear(year);
			active.setScope(GradeRegisterTemplateScope.TENANT);
			active.setIsDefault(Boolean.TRUE);
			GradeCategory oldCat = newCategory(active, "Vieja", 1, "100");

			when(templateRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(academicYearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(templateRepository.findDefaultTenantTemplate(year))
					.thenReturn(Optional.of(active));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(active))
					.thenReturn(List.of(oldCat));
			when(evaluationRepository.countByCategoriesAndYear(List.of(oldCat), year))
					.thenReturn(0L);
			when(gradeRecordRepository.countByCategoriesAndYear(List.of(oldCat), year))
					.thenReturn(0L);
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(source))
					.thenReturn(List.of(srcCat));
			when(templateRepository.findOtherDefaults(any(), any())).thenReturn(List.of());
			when(templateRepository.save(any(GradeRegisterTemplate.class))).thenAnswer(inv -> {
				GradeRegisterTemplate t = inv.getArgument(0);
				if (t.getPublicUuid() == null) {
					t.setPublicUuid(UUID.randomUUID());
				}
				setField(t, "id", UUID.randomUUID());
				return t;
			});
			when(categoryRepository.save(any(GradeCategory.class))).thenAnswer(inv -> {
				GradeCategory c = inv.getArgument(0);
				if (c.getPublicUuid() == null) {
					c.setPublicUuid(UUID.randomUUID());
				}
				setField(c, "id", UUID.randomUUID());
				return c;
			});
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(
					org.mockito.ArgumentMatchers.argThat(
							t -> t != null
									&& !Boolean.TRUE.equals(t.getIsSystem())
									&& !"Old active".equals(t.getName()))))
					.thenAnswer(inv -> {
						GradeRegisterTemplate copy = inv.getArgument(0);
						return List.of(newCategory(copy, "Participación", 1, "30"));
					});

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					year.getPublicUuid().toString(),
					GradeRegisterTemplateScope.TENANT,
					null,
					null,
					true,
					true);

			GradeRegisterTemplateResponse response =
					service.adopt(source.getPublicUuid(), req);

			assertThat(response.system()).isFalse();
			assertThat(response.isDefault()).isTrue();
			verify(templateRepository, atLeast(2)).save(any(GradeRegisterTemplate.class));
		}
	}

	@Nested
	@DisplayName("resolveForAssignment")
	class ResolveForAssignment {

		@Test
		@DisplayName("COURSE template wins over TENANT default")
		void courseTemplateWins() {
			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");
			TeacherAssignment assignment = newAssignment(year, course);

			GradeRegisterTemplate courseTpl = newTemplate("Course override", false);
			courseTpl.setScope(GradeRegisterTemplateScope.COURSE);
			courseTpl.setAcademicYear(year);
			courseTpl.setCourse(course);

			when(templateRepository.findCourseTemplate(year, course))
					.thenReturn(Optional.of(courseTpl));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(courseTpl))
					.thenReturn(List.of());

			Optional<GradeRegisterTemplateResponse> result =
					service.resolveForAssignment(assignment);

			assertThat(result).isPresent();
			assertThat(result.get().publicUuid()).isEqualTo(courseTpl.getPublicUuid());
			assertThat(result.get().name()).isEqualTo("Course override");
			verify(templateRepository).findCourseTemplate(year, course);
			verify(templateRepository, never()).findDefaultTenantTemplate(any());
		}

		@Test
		@DisplayName("falls back to TENANT default when no COURSE template")
		void fallsBackToTenantDefault() {
			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");
			TeacherAssignment assignment = newAssignment(year, course);

			GradeRegisterTemplate tenantDefault = newTemplate("Tenant default", false);
			tenantDefault.setScope(GradeRegisterTemplateScope.TENANT);
			tenantDefault.setIsDefault(true);
			tenantDefault.setAcademicYear(year);

			when(templateRepository.findCourseTemplate(year, course))
					.thenReturn(Optional.empty());
			when(templateRepository.findDefaultTenantTemplate(year))
					.thenReturn(Optional.of(tenantDefault));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(tenantDefault))
					.thenReturn(List.of());

			Optional<GradeRegisterTemplateResponse> result =
					service.resolveForAssignment(assignment);

			assertThat(result).isPresent();
			assertThat(result.get().publicUuid()).isEqualTo(tenantDefault.getPublicUuid());
		}

		@Test
		@DisplayName("returns empty when no COURSE and no TENANT default")
		void returnsEmptyWhenNone() {
			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");
			TeacherAssignment assignment = newAssignment(year, course);

			when(templateRepository.findCourseTemplate(year, course))
					.thenReturn(Optional.empty());
			when(templateRepository.findDefaultTenantTemplate(year))
					.thenReturn(Optional.empty());

			assertThat(service.resolveForAssignment(assignment)).isEmpty();
		}

		@Test
		@DisplayName("returns empty when assignment period/year missing")
		void returnsEmptyWhenPeriodMissing() {
			TeacherAssignment assignment = new TeacherAssignment();
			assignment.setCourse(newCourse("MAT"));
			assignment.setAcademicPeriod(null);

			assertThat(service.resolveForAssignment(assignment)).isEmpty();
			verify(templateRepository, never()).findCourseTemplate(any(), any());
		}

		@Test
		@DisplayName("LEVEL template wins when the section's level has one")
		void levelTemplateWinsOverYearDefault() {
			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");
			AcademicLevel level = newLevel("PRIMARIA");
			TeacherAssignment assignment = newAssignment(year, course, level);

			GradeRegisterTemplate levelTpl = newTemplate("Primaria", false);
			levelTpl.setScope(GradeRegisterTemplateScope.LEVEL);
			levelTpl.setAcademicYear(year);
			levelTpl.setAcademicLevel(level);

			when(templateRepository.findCourseTemplate(year, course)).thenReturn(Optional.empty());
			when(templateRepository.findLevelTemplate(year, level)).thenReturn(Optional.of(levelTpl));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(levelTpl))
					.thenReturn(List.of());

			Optional<GradeRegisterTemplateResponse> result = service.resolveForAssignment(assignment);

			assertThat(result).isPresent();
			assertThat(result.get().publicUuid()).isEqualTo(levelTpl.getPublicUuid());
			assertThat(result.get().scope()).isEqualTo(GradeRegisterTemplateScope.LEVEL);
			verify(templateRepository, never()).findDefaultTenantTemplate(any());
		}

		@Test
		@DisplayName("falls back to TENANT default when the level has no template")
		void fallsBackToYearWhenLevelHasNone() {
			AcademicYear year = newYear("2026");
			Course course = newCourse("MAT");
			AcademicLevel level = newLevel("INICIAL");
			TeacherAssignment assignment = newAssignment(year, course, level);

			GradeRegisterTemplate tenantDefault = newTemplate("Año", false);
			tenantDefault.setScope(GradeRegisterTemplateScope.TENANT);
			tenantDefault.setIsDefault(true);
			tenantDefault.setAcademicYear(year);

			when(templateRepository.findCourseTemplate(year, course)).thenReturn(Optional.empty());
			when(templateRepository.findLevelTemplate(year, level)).thenReturn(Optional.empty());
			when(templateRepository.findDefaultTenantTemplate(year))
					.thenReturn(Optional.of(tenantDefault));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(tenantDefault))
					.thenReturn(List.of());

			Optional<GradeRegisterTemplateResponse> result = service.resolveForAssignment(assignment);

			assertThat(result).isPresent();
			assertThat(result.get().publicUuid()).isEqualTo(tenantDefault.getPublicUuid());
			assertThat(result.get().scope()).isEqualTo(GradeRegisterTemplateScope.TENANT);
		}
	}

	@Nested
	@DisplayName("adopt LEVEL")
	class AdoptLevel {

		@Test
		@DisplayName("clones a LEVEL copy that is not the year default")
		void adoptsLevelCopy() {
			GradeRegisterTemplate source = newTemplate("Planilla sesiones", true);
			source.setCatalogCode("PLANILLA_SESIONES");
			GradeCategory srcCat = newCategory(source, "Participación oral", 1, "30");
			AcademicYear year = newYear("2026");
			AcademicLevel level = newLevel("SECUNDARIA");

			when(templateRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(academicYearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(academicLevelRepository.findByPublicUuid(level.getPublicUuid()))
					.thenReturn(Optional.of(level));
			when(templateRepository.findLevelTemplate(year, level)).thenReturn(Optional.empty());
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(source))
					.thenReturn(List.of(srcCat));
			when(templateRepository.save(any(GradeRegisterTemplate.class))).thenAnswer(inv -> {
				GradeRegisterTemplate t = inv.getArgument(0);
				if (t.getPublicUuid() == null) {
					t.setPublicUuid(UUID.randomUUID());
				}
				setField(t, "id", UUID.randomUUID());
				return t;
			});
			when(categoryRepository.save(any(GradeCategory.class))).thenAnswer(inv -> inv.getArgument(0));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(
					org.mockito.ArgumentMatchers.argThat(
							t -> t != null && !Boolean.TRUE.equals(t.getIsSystem()))))
					.thenAnswer(inv -> List.of(newCategory(inv.getArgument(0), "Participación oral", 1, "30")));

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					year.getPublicUuid().toString(),
					GradeRegisterTemplateScope.LEVEL,
					null,
					level.getPublicUuid().toString(),
					false,
					false);

			GradeRegisterTemplateResponse response = service.adopt(source.getPublicUuid(), req);

			assertThat(response.scope()).isEqualTo(GradeRegisterTemplateScope.LEVEL);
			assertThat(response.isDefault()).isFalse();
			assertThat(response.levelPublicUuid()).isEqualTo(level.getPublicUuid());

			ArgumentCaptor<GradeRegisterTemplate> captor =
					ArgumentCaptor.forClass(GradeRegisterTemplate.class);
			verify(templateRepository).save(captor.capture());
			assertThat(captor.getValue().getAcademicLevel()).isSameAs(level);
			assertThat(captor.getValue().getCourse()).isNull();
			assertThat(captor.getValue().getIsDefault()).isFalse();
			verify(templateRepository, never()).findDefaultTenantTemplate(any());
		}

		@Test
		@DisplayName("blocks LEVEL replace when that copy is in use and leaves the year template")
		void blocksLevelReplaceWhenInUse() {
			GradeRegisterTemplate source = newTemplate("Catalog", true);
			AcademicYear year = newYear("2026");
			AcademicLevel level = newLevel("PRIMARIA");

			GradeRegisterTemplate active = newTemplate("Primaria activa", false);
			active.setAcademicYear(year);
			active.setAcademicLevel(level);
			active.setScope(GradeRegisterTemplateScope.LEVEL);
			GradeCategory cat = newCategory(active, "Participación", 1, "30");

			when(templateRepository.findByPublicUuid(source.getPublicUuid()))
					.thenReturn(Optional.of(source));
			when(academicYearRepository.findByPublicUuid(year.getPublicUuid()))
					.thenReturn(Optional.of(year));
			when(academicLevelRepository.findByPublicUuid(level.getPublicUuid()))
					.thenReturn(Optional.of(level));
			when(templateRepository.findLevelTemplate(year, level)).thenReturn(Optional.of(active));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(active))
					.thenReturn(List.of(cat));
			when(evaluationRepository.countByCategoriesAndYear(List.of(cat), year)).thenReturn(2L);
			when(gradeRecordRepository.countByCategoriesAndYear(List.of(cat), year)).thenReturn(4L);

			AdoptGradeRegisterTemplateRequest req = new AdoptGradeRegisterTemplateRequest(
					year.getPublicUuid().toString(),
					GradeRegisterTemplateScope.LEVEL,
					null,
					level.getPublicUuid().toString(),
					false,
					true);

			assertThatThrownBy(() -> service.adopt(source.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.extracting(ex -> ((ConflictException) ex).getCode())
					.isEqualTo("GRADE_REGISTER_IN_USE");
			verify(templateRepository, never()).save(any());
			verify(templateRepository, never()).findDefaultTenantTemplate(any());
		}
	}

	@Nested
	@DisplayName("update grading scale")
	class UpdateScale {

		@Test
		@DisplayName("unused template accepts LITERAL_CNEB")
		void changesScaleWhenUnused() {
			GradeRegisterTemplate template = newTemplate("Local", false);
			template.setAcademicYear(newYear("2026"));
			when(templateRepository.findByPublicUuid(template.getPublicUuid()))
					.thenReturn(Optional.of(template));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(template))
					.thenReturn(List.of());
			when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			GradeRegisterTemplateResponse response = service.update(
					template.getPublicUuid(),
					new UpdateGradeRegisterTemplateRequest(
							null, null, null, EvaluationScale.LITERAL_CNEB));

			assertThat(response.gradingScale()).isEqualTo(EvaluationScale.LITERAL_CNEB);
		}

		@Test
		@DisplayName("in-use template rejects a scale change")
		void rejectsScaleChangeWhenInUse() {
			GradeRegisterTemplate template = newTemplate("Local", false);
			AcademicYear year = newYear("2026");
			template.setAcademicYear(year);
			GradeCategory category = newCategory(template, "Comp", 1, "100");
			when(templateRepository.findByPublicUuid(template.getPublicUuid()))
					.thenReturn(Optional.of(template));
			when(categoryRepository.findByTemplateOrderBySortOrderAscNameAsc(template))
					.thenReturn(List.of(category));
			when(evaluationRepository.countByCategoriesAndYear(any(), any())).thenReturn(1L);
			when(gradeRecordRepository.countByCategoriesAndYear(any(), any())).thenReturn(0L);

			assertThatThrownBy(() -> service.update(
					template.getPublicUuid(),
					new UpdateGradeRegisterTemplateRequest(
							null, null, null, EvaluationScale.LITERAL_CNEB)))
					.isInstanceOf(ConflictException.class)
					.extracting(ex -> ((ConflictException) ex).getCode())
					.isEqualTo("GRADE_REGISTER_IN_USE");
		}
	}

	private static GradeRegisterTemplate newTemplate(String name, boolean system) {
		GradeRegisterTemplate t = new GradeRegisterTemplate();
		t.setPublicUuid(UUID.randomUUID());
		setField(t, "id", UUID.randomUUID());
		t.setName(name);
		t.setIsSystem(system);
		t.setScope(GradeRegisterTemplateScope.TENANT);
		t.setIsDefault(Boolean.FALSE);
		t.setRecommendedLevels(List.of());
		return t;
	}

	private static GradeCategory newCategory(
			GradeRegisterTemplate template,
			String name,
			int order,
			String weight
	) {
		GradeCategory c = new GradeCategory();
		c.setPublicUuid(UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		c.setTemplate(template);
		c.setName(name);
		c.setSortOrder(order);
		c.setColorToken("amber");
		c.setWeight(new BigDecimal(weight));
		c.setContributesToAverage(Boolean.TRUE);
		c.setAllowsTeacherColumns(Boolean.TRUE);
		return c;
	}

	private static AcademicYear newYear(String name) {
		AcademicYear y = new AcademicYear();
		y.setPublicUuid(UUID.randomUUID());
		setField(y, "id", UUID.randomUUID());
		y.setName(name);
		return y;
	}

	private static Course newCourse(String code) {
		Course c = new Course();
		c.setCode(code);
		c.setName("Course " + code);
		c.setIsActive(Boolean.TRUE);
		c.setPublicUuid(UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static TeacherAssignment newAssignment(AcademicYear year, Course course) {
		return newAssignment(year, course, null);
	}

	private static TeacherAssignment newAssignment(
			AcademicYear year,
			Course course,
			AcademicLevel level
	) {
		AcademicPeriod period = new AcademicPeriod();
		period.setPublicUuid(UUID.randomUUID());
		setField(period, "id", UUID.randomUUID());
		period.setAcademicYear(year);
		period.setName("Bimestre 1");

		TeacherAssignment a = new TeacherAssignment();
		a.setPublicUuid(UUID.randomUUID());
		setField(a, "id", UUID.randomUUID());
		a.setCourse(course);
		a.setAcademicPeriod(period);
		if (level != null) {
			Grade grade = new Grade();
			grade.setPublicUuid(UUID.randomUUID());
			setField(grade, "id", UUID.randomUUID());
			grade.setLevel(level);
			grade.setName("1ro");
			grade.setOrdinal(1);

			Section section = new Section();
			section.setPublicUuid(UUID.randomUUID());
			setField(section, "id", UUID.randomUUID());
			section.setGrade(grade);
			section.setName("A");
			a.setSection(section);
		}
		return a;
	}

	private static AcademicLevel newLevel(String code) {
		AcademicLevel level = new AcademicLevel();
		level.setPublicUuid(UUID.randomUUID());
		setField(level, "id", UUID.randomUUID());
		level.setCode(code);
		level.setName(code);
		level.setOrdinal(1);
		return level;
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
