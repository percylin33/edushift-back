package com.edushift.modules.academic.levelgrade.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.levelgrade.dto.CreateGradeRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeReorderRequest;
import com.edushift.modules.academic.levelgrade.dto.GradeResponse;
import com.edushift.modules.academic.levelgrade.dto.UpdateGradeRequest;
import com.edushift.modules.academic.levelgrade.entity.AcademicLevel;
import com.edushift.modules.academic.levelgrade.entity.Grade;
import com.edushift.modules.academic.levelgrade.mapper.GradeMapper;
import com.edushift.modules.academic.levelgrade.repository.AcademicLevelRepository;
import com.edushift.modules.academic.levelgrade.repository.GradeRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GradeServiceImplTest {

	@Mock private AcademicLevelRepository levelRepository;
	@Mock private GradeRepository gradeRepository;
	@Mock private com.edushift.modules.academic.section.repository.SectionRepository sectionRepository;
	@Spy private GradeMapper mapper = new GradeMapper();

	@InjectMocks private GradeServiceImpl service;

	@Nested
	@DisplayName("listGrades")
	class ListGrades {

		@Test
		@DisplayName("returns grades sorted by ordinal asc")
		void happyPath() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade g1 = newGrade(level, "1ro", 1);
			Grade g2 = newGrade(level, "2do", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of(g1, g2));

			List<GradeResponse> result = service.listGrades(level.getPublicUuid());

			assertThat(result).hasSize(2);
			assertThat(result.get(0).name()).isEqualTo("1ro");
		}
	}

	@Nested
	@DisplayName("createGrade")
	class CreateGrade {

		@Test
		@DisplayName("happy path")
		void happyPath() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findByLevelAndOrdinal(level, 1)).thenReturn(Optional.empty());
			when(gradeRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Grade g = inv.getArgument(0);
				setField(g, "publicUuid", UUID.randomUUID());
				return g;
			});

			GradeResponse response = service.createGrade(
					level.getPublicUuid(),
					new CreateGradeRequest("1ro", 1));

			assertThat(response.name()).isEqualTo("1ro");
			assertThat(response.levelPublicUuid()).isEqualTo(level.getPublicUuid());
		}

		@Test
		@DisplayName("ordinal already taken in level → 409 GRADE_ORDINAL_TAKEN")
		void ordinalTaken() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade existing = newGrade(level, "Existing", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findByLevelAndOrdinal(level, 1)).thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.createGrade(
					level.getPublicUuid(), new CreateGradeRequest("1ro", 1)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("ordinal 1");
			verify(gradeRepository, never()).saveAndFlush(any());
		}
	}

	@Nested
	@DisplayName("updateGrade — anti-enumeration")
	class UpdateGradeAntiEnum {

		@Test
		@DisplayName("grade belonging to a sibling level → 404 (not 403)")
		void crossLevelGradeIs404() {
			AcademicLevel levelA = newLevel("PRIMARIA", 2);
			AcademicLevel levelB = newLevel("SECUNDARIA", 3);
			Grade gradeOfB = newGrade(levelB, "1ro Sec", 1);
			when(levelRepository.findByPublicUuid(levelA.getPublicUuid())).thenReturn(Optional.of(levelA));
			when(gradeRepository.findByPublicUuid(gradeOfB.getPublicUuid())).thenReturn(Optional.of(gradeOfB));

			assertThatThrownBy(() -> service.updateGrade(
					levelA.getPublicUuid(),
					gradeOfB.getPublicUuid(),
					new UpdateGradeRequest("hacked", null)))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("deleteGrade")
	class DeleteGrade {

		@Test
		@DisplayName("happy path — no sections, soft-deletes")
		void happyPath() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade grade = newGrade(level, "1ro", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid())).thenReturn(Optional.of(grade));
			when(sectionRepository.countByGrade(grade)).thenReturn(0L);

			service.deleteGrade(level.getPublicUuid(), grade.getPublicUuid());

			verify(gradeRepository).delete(grade);
		}

		@Test
		@DisplayName("grade has sections → 409 GRADE_HAS_SECTIONS")
		void hasSections() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade grade = newGrade(level, "1ro", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findByPublicUuid(grade.getPublicUuid())).thenReturn(Optional.of(grade));
			when(sectionRepository.countByGrade(grade)).thenReturn(3L);

			assertThatThrownBy(() -> service.deleteGrade(level.getPublicUuid(), grade.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("3 section");
			verify(gradeRepository, never()).delete(grade);
		}
	}

	@Nested
	@DisplayName("reorderGrades")
	class ReorderGrades {

		@Test
		@DisplayName("happy path — applies new ordinals atomically")
		void happyPath() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade g1 = newGrade(level, "1ro", 1);
			Grade g2 = newGrade(level, "2do", 2);
			Grade g3 = newGrade(level, "3ro", 3);
			List<Grade> sorted = new ArrayList<>(List.of(g1, g2, g3));

			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(sorted);
			when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));

			GradeReorderRequest req = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(g1.getPublicUuid(), 3),
					new GradeReorderRequest.Item(g2.getPublicUuid(), 1),
					new GradeReorderRequest.Item(g3.getPublicUuid(), 2)
			));

			service.reorderGrades(level.getPublicUuid(), req);

			// Final ordinals applied
			assertThat(g1.getOrdinal()).isEqualTo(3);
			assertThat(g2.getOrdinal()).isEqualTo(1);
			assertThat(g3.getOrdinal()).isEqualTo(2);
			// Repository was hit (parking + final) — at least 6 saves total
			verify(gradeRepository, atLeastOnce()).save(any(Grade.class));
		}

		@Test
		@DisplayName("payload with grade from another level → 409 GRADE_REORDER_INVALID")
		void crossLevelGradeRejected() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade g1 = newGrade(level, "1ro", 1);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of(g1));

			UUID stranger = UUID.randomUUID();
			GradeReorderRequest req = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(g1.getPublicUuid(), 1),
					new GradeReorderRequest.Item(stranger, 2)
			));

			assertThatThrownBy(() -> service.reorderGrades(level.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("does not belong to level");
		}

		@Test
		@DisplayName("duplicate ordinal in payload → 409 GRADE_REORDER_INVALID")
		void duplicateOrdinalRejected() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));

			GradeReorderRequest req = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(UUID.randomUUID(), 1),
					new GradeReorderRequest.Item(UUID.randomUUID(), 1)
			));

			assertThatThrownBy(() -> service.reorderGrades(level.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Duplicate ordinal");
		}

		@Test
		@DisplayName("DB collision on the final flush → 409 GRADE_ORDINAL_TAKEN")
		void dbCollision() {
			AcademicLevel level = newLevel("PRIMARIA", 2);
			Grade g1 = newGrade(level, "1ro", 1);
			Grade g2 = newGrade(level, "2do", 2);
			when(levelRepository.findByPublicUuid(level.getPublicUuid())).thenReturn(Optional.of(level));
			when(gradeRepository.findAllByLevelOrderByOrdinalAsc(level)).thenReturn(List.of(g1, g2));
			when(gradeRepository.save(any(Grade.class))).thenAnswer(inv -> inv.getArgument(0));
			// flush(): first call (after parking) succeeds; second call
			// (after final assignment) trips the unique partial index.
			org.mockito.Mockito.doNothing()
					.doThrow(new DataIntegrityViolationException("uk_grades_level_ordinal_active"))
					.when(gradeRepository).flush();

			GradeReorderRequest req = new GradeReorderRequest(List.of(
					new GradeReorderRequest.Item(g1.getPublicUuid(), 1),
					new GradeReorderRequest.Item(g2.getPublicUuid(), 2)
			));

			assertThatThrownBy(() -> service.reorderGrades(level.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class);
		}
	}

	// helpers
	private static AcademicLevel newLevel(String code, int ordinal) {
		AcademicLevel l = new AcademicLevel();
		l.setCode(code);
		l.setName(code);
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
