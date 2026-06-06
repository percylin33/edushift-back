package com.edushift.modules.academic.levelgrade.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.levelgrade.dto.AcademicLevelResponse;
import com.edushift.modules.academic.levelgrade.dto.CreateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.dto.UpdateAcademicLevelRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.mapper.AcademicLevelMapper;
import com.edushift.modules.academic.levelgrade.mapper.GradeMapper;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AcademicLevelServiceImplTest {

	@Mock private AcademicLevelRepository levelRepository;
	@Mock private GradeRepository gradeRepository;
	@Mock private com.edushift.modules.academic.course.repository.CourseLevelRepository courseLevelRepository;

	private AcademicLevelServiceImpl service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		AcademicLevelMapper mapper = new AcademicLevelMapper(new GradeMapper());
		service = new AcademicLevelServiceImpl(
				levelRepository, gradeRepository, courseLevelRepository, mapper);
	}

	@Nested
	@DisplayName("listLevels")
	class ListLevels {

		@Test
		@DisplayName("returns levels sorted by ordinal with their grades")
		void happyPath() {
			AcademicLevel inicial = newLevel("INICIAL", "Inicial", 1);
			AcademicLevel primaria = newLevel("PRIMARIA", "Primaria", 2);
			when(levelRepository.findAllByOrderByOrdinalAsc())
					.thenReturn(List.of(inicial, primaria));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(inicial))
					.thenReturn(List.of(newGrade(inicial, "3 años", 1)));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(primaria))
					.thenReturn(List.of(newGrade(primaria, "1ro Primaria", 1),
							newGrade(primaria, "2do Primaria", 2)));

			List<AcademicLevelResponse> result = service.listLevels();

			assertThat(result).hasSize(2);
			assertThat(result.get(0).code()).isEqualTo("INICIAL");
			assertThat(result.get(0).grades()).hasSize(1);
			assertThat(result.get(1).code()).isEqualTo("PRIMARIA");
			assertThat(result.get(1).grades()).hasSize(2);
		}
	}

	@Nested
	@DisplayName("getLevel")
	class GetLevel {

		@Test
		@DisplayName("happy path — returns level with grades")
		void happyPath() {
			AcademicLevel level = newLevel("PRIMARIA", "Primaria", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of());

			AcademicLevelResponse response = service.getLevel(level.getPublicUuid());

			assertThat(response.code()).isEqualTo("PRIMARIA");
			assertThat(response.grades()).isEmpty();
		}

		@Test
		@DisplayName("unknown publicUuid → 404")
		void unknown() {
			UUID id = UUID.randomUUID();
			when(levelRepository.findByPublicUuid(id)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.getLevel(id))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("createLevel")
	class CreateLevel {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			CreateAcademicLevelRequest req = new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4);
			when(levelRepository.findByCodeIgnoreCase("IGCSE")).thenReturn(Optional.empty());
			when(levelRepository.saveAndFlush(any())).thenAnswer(inv -> {
				AcademicLevel arg = inv.getArgument(0);
				setField(arg, "publicUuid", UUID.randomUUID());
				return arg;
			});

			AcademicLevelResponse response = service.createLevel(req);

			assertThat(response.code()).isEqualTo("IGCSE");
			assertThat(response.ordinal()).isEqualTo(4);
		}

		@Test
		@DisplayName("code already taken → 409 LEVEL_CODE_TAKEN")
		void codeTaken() {
			AcademicLevel existing = newLevel("IGCSE", "IGCSE", 4);
			when(levelRepository.findByCodeIgnoreCase("IGCSE")).thenReturn(Optional.of(existing));

			CreateAcademicLevelRequest req = new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4);

			assertThatThrownBy(() -> service.createLevel(req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("IGCSE");
			verify(levelRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("race: DB unique violation → 409 LEVEL_CODE_TAKEN")
		void raceCondition() {
			when(levelRepository.findByCodeIgnoreCase("IGCSE")).thenReturn(Optional.empty());
			when(levelRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException("uk_academic_levels_tenant_code_active"));

			CreateAcademicLevelRequest req = new CreateAcademicLevelRequest("IGCSE", "IGCSE", 4);

			assertThatThrownBy(() -> service.createLevel(req))
					.isInstanceOf(ConflictException.class);
		}
	}

	@Nested
	@DisplayName("updateLevel")
	class UpdateLevel {

		@Test
		@DisplayName("happy path — updates name only")
		void happyPath() {
			AcademicLevel level = newLevel("INICIAL", "Inicial", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(levelRepository.saveAndFlush(level)).thenReturn(level);
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of());

			AcademicLevelResponse response = service.updateLevel(level.getPublicUuid(),
					new UpdateAcademicLevelRequest(null, "Inicial Renovado", null));

			assertThat(response.name()).isEqualTo("Inicial Renovado");
		}

		@Test
		@DisplayName("empty patch returns current state without persist")
		void emptyPatch() {
			AcademicLevel level = newLevel("INICIAL", "Inicial", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of());

			service.updateLevel(level.getPublicUuid(),
					new UpdateAcademicLevelRequest(null, null, null));

			verify(levelRepository, never()).saveAndFlush(any());
		}
	}

	@Nested
	@DisplayName("deleteLevel")
	class DeleteLevel {

		@Test
		@DisplayName("level with no grades — soft-deletes")
		void happyPath() {
			AcademicLevel level = newLevel("CUSTOM", "Custom", 9);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.countByLevel(level)).thenReturn(0L);
			when(courseLevelRepository.countByLevel(level)).thenReturn(0L);

			service.deleteLevel(level.getPublicUuid());

			verify(levelRepository).delete(level);
		}

		@Test
		@DisplayName("level with grades → 409 LEVEL_HAS_GRADES")
		void hasGrades() {
			AcademicLevel level = newLevel("PRIMARIA", "Primaria", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.countByLevel(level)).thenReturn(6L);

			assertThatThrownBy(() -> service.deleteLevel(level.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("6 grade");
			verify(levelRepository, never()).delete(level);
		}

		@Test
		@DisplayName("level used by courses → 409 LEVEL_IN_USE_BY_COURSES")
		void inUseByCourses() {
			AcademicLevel level = newLevel("CUSTOM", "Custom", 9);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.countByLevel(level)).thenReturn(0L);
			when(courseLevelRepository.countByLevel(level)).thenReturn(2L);

			assertThatThrownBy(() -> service.deleteLevel(level.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("2 course");
			verify(levelRepository, never()).delete(level);
		}
	}

	// helpers
	private static AcademicLevel newLevel(String code, String name, int ordinal) {
		AcademicLevel l = new AcademicLevel();
		l.setCode(code);
		l.setName(name);
		l.setOrdinal(ordinal);
		setField(l, "publicUuid", UUID.randomUUID());
		setField(l, "id", UUID.randomUUID());
		return l;
	}

	private static Grade newGrade(AcademicLevel level, String name, int ordinal) {
		Grade g = new Grade();
		g.setLevel(level);
		g.setName(name);
		g.setOrdinal(ordinal);
		setField(g, "publicUuid", UUID.randomUUID());
		setField(g, "id", UUID.randomUUID());
		return g;
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
