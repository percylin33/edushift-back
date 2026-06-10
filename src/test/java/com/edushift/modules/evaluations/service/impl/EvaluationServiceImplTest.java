package com.edushift.modules.evaluations.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationListItem;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.mapper.EvaluationMapper;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Unit tests for {@link EvaluationServiceImpl} (Sprint 5B — BE-5B.1).
 *
 * <p>Covers every error code in the contract, the state-machine
 * transitions, the editability matrix for partial-merge, the
 * kind/scale coherence matrix, and the cross-context FK validations
 * (unit-in-course, session-in-assignment).</p>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

	@Mock private EvaluationRepository evaluationRepository;
	@Mock private TeacherAssignmentRepository assignmentRepository;
	@Mock private UnitRepository unitRepository;
	@Mock private LearningSessionRepository sessionRepository;
	@Spy private EvaluationMapper mapper = new EvaluationMapper();

	@InjectMocks private EvaluationServiceImpl service;

	// =========================================================================
	// listEvaluations
	// =========================================================================

	@Nested
	@DisplayName("listEvaluations")
	class ListEvaluations {

		@Test
		@DisplayName("returns evaluations ordered by scheduledDate desc")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation e1 = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			Evaluation e2 = newEvaluation(assignment, "Examen Final",
					EvaluationKind.EXAM, EvaluationScale.SCORE_0_20);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(evaluationRepository.findFiltered(assignment, null, null, null, null))
					.thenReturn(List.of(e1, e2));

			List<EvaluationListItem> result = service.listEvaluations(
					assignment.getPublicUuid(), null);

			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
		void unknownAssignment() {
			UUID anyUuid = UUID.randomUUID();
			when(assignmentRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.listEvaluations(anyUuid, null))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// createEvaluation
	// =========================================================================

	@Nested
	@DisplayName("createEvaluation")
	class CreateEvaluation {

		@Test
		@DisplayName("happy path — DRAFT, no anchors")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(evaluationRepository.findByAssignmentAndNameIgnoreCase(
					assignment, "Tarea 1")).thenReturn(Optional.empty());
			when(evaluationRepository.saveAndFlush(any())).thenAnswer(inv -> {
				Evaluation e = inv.getArgument(0);
				setField(e, "publicUuid", UUID.randomUUID());
				setField(e, "id", UUID.randomUUID());
				return e;
			});

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.TASK, "Tarea 1", "Description",
					BigDecimal.valueOf(1.5),
					LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15),
					EvaluationScale.SCORE_0_20, null, null, null);

			EvaluationResponse response = service.createEvaluation(
					assignment.getPublicUuid(), req);

			assertThat(response.name()).isEqualTo("Tarea 1");
			assertThat(response.status()).isEqualTo(EvaluationStatus.DRAFT);
			assertThat(response.gradeCount()).isZero();
		}

		@Test
		@DisplayName("name duplicated case-insensitively → 409 EVAL_NAME_EXISTS")
		void nameTaken() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(evaluationRepository.findByAssignmentAndNameIgnoreCase(
					assignment, "Tarea 1")).thenReturn(Optional.of(existing));

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.TASK, "Tarea 1", null, BigDecimal.ONE,
					LocalDate.of(2026, 5, 1), null,
					EvaluationScale.SCORE_0_20, null, null, null);

			assertThatThrownBy(() -> service.createEvaluation(
					assignment.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Tarea 1");
			verify(evaluationRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("dueDate < scheduledDate → 400 EVAL_DATE_INVERTED")
		void datesInverted() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.TASK, "Tarea 1", null, BigDecimal.ONE,
					LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 1),
					EvaluationScale.SCORE_0_20, null, null, null);

			assertThatThrownBy(() -> service.createEvaluation(
					assignment.getPublicUuid(), req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("must be on or after");
		}

		@Test
		@DisplayName("EXAM with LITERAL_NA scale → 400 EVAL_KIND_SCALE_MISMATCH")
		void kindScaleMismatch() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.EXAM, "Examen", null, BigDecimal.ONE,
					LocalDate.of(2026, 5, 1), null,
					EvaluationScale.LITERAL_NA, null, null, null);

			assertThatThrownBy(() -> service.createEvaluation(
					assignment.getPublicUuid(), req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("not compatible");
		}

		@Test
		@DisplayName("DB collision on save → 409 EVAL_NAME_EXISTS")
		void dbCollision() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(evaluationRepository.findByAssignmentAndNameIgnoreCase(
					assignment, "Tarea 1")).thenReturn(Optional.empty());
			when(evaluationRepository.saveAndFlush(any()))
					.thenThrow(new DataIntegrityViolationException(
							"uk_evaluations_tenant_assignment_name_ci"));

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.TASK, "Tarea 1", null, BigDecimal.ONE,
					LocalDate.of(2026, 5, 1), null,
					EvaluationScale.SCORE_0_20, null, null, null);

			assertThatThrownBy(() -> service.createEvaluation(
					assignment.getPublicUuid(), req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Tarea 1");
		}

		@Test
		@DisplayName("unit anchor from another course → 400 EVAL_UNIT_NOT_IN_COURSE")
		void unitInAnotherCourse() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Course otherCourse = newCourse("COM");
			Unit foreignUnit = newUnit(otherCourse, "Unidad Hackeada", 1);
			when(assignmentRepository.findByPublicUuid(assignment.getPublicUuid()))
					.thenReturn(Optional.of(assignment));
			when(evaluationRepository.findByAssignmentAndNameIgnoreCase(
					assignment, "Tarea 1")).thenReturn(Optional.empty());
			when(unitRepository.findByPublicUuid(foreignUnit.getPublicUuid()))
					.thenReturn(Optional.of(foreignUnit));

			CreateEvaluationRequest req = new CreateEvaluationRequest(
					EvaluationKind.TASK, "Tarea 1", null, BigDecimal.ONE,
					LocalDate.of(2026, 5, 1), null,
					EvaluationScale.SCORE_0_20,
					foreignUnit.getPublicUuid().toString(), null, null);

			assertThatThrownBy(() -> service.createEvaluation(
					assignment.getPublicUuid(), req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("does not belong to the assignment's course");
		}
	}

	// =========================================================================
	// updateEvaluation
	// =========================================================================

	@Nested
	@DisplayName("updateEvaluation")
	class UpdateEvaluation {

		@Test
		@DisplayName("happy path — partial-merge on a DRAFT row")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(evaluationRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			EvaluationResponse response = service.updateEvaluation(
					existing.getPublicUuid(),
					new UpdateEvaluationRequest(null, null, "Updated desc",
							null, null, null, null, null, null, null));

			assertThat(response.description()).isEqualTo("Updated desc");
			assertThat(response.name()).isEqualTo("Tarea 1");
		}

		@Test
		@DisplayName("empty patch returns current state without writing")
		void emptyPatch() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			EvaluationResponse response = service.updateEvaluation(
					existing.getPublicUuid(), new UpdateEvaluationRequest(
							null, null, null, null, null, null, null, null, null, null));

			assertThat(response.name()).isEqualTo("Tarea 1");
			verify(evaluationRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("CLOSED row → 409 EVAL_CLOSED")
		void closed() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			existing.setStatus(EvaluationStatus.CLOSED);
			existing.setPublishedAt(Instant.now());
			existing.setClosedAt(Instant.now());
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.updateEvaluation(
					existing.getPublicUuid(),
					new UpdateEvaluationRequest(null, null, "x",
							null, null, null, null, null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("CLOSED");
		}

		@Test
		@DisplayName("PUBLISHED row + patch of frozen field → 409 EVAL_NOT_EDITABLE")
		void publishedFrozenField() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			existing.setStatus(EvaluationStatus.PUBLISHED);
			existing.setPublishedAt(Instant.now());
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			// Patch tries to change kind → frozen in PUBLISHED.
			assertThatThrownBy(() -> service.updateEvaluation(
					existing.getPublicUuid(),
					new UpdateEvaluationRequest(EvaluationKind.EXAM, null, null,
							null, null, null, null, null, null, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("PUBLISHED");
		}

		@Test
		@DisplayName("PUBLISHED row + patch of allowed field (description) succeeds")
		void publishedAllowedField() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			existing.setStatus(EvaluationStatus.PUBLISHED);
			existing.setPublishedAt(Instant.now());
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(evaluationRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			EvaluationResponse response = service.updateEvaluation(
					existing.getPublicUuid(),
					new UpdateEvaluationRequest(null, null, "Updated",
							null, null, null, null, null, null, null));

			assertThat(response.description()).isEqualTo("Updated");
		}

		@Test
		@DisplayName("post-merge kind/scale mismatch → 400 EVAL_KIND_SCALE_MISMATCH")
		void postMergeKindScale() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			// Patch sets kind=COMPETENCY while scale stays SCORE_0_20 → mismatch
			assertThatThrownBy(() -> service.updateEvaluation(
					existing.getPublicUuid(),
					new UpdateEvaluationRequest(EvaluationKind.COMPETENCY, null, null,
							null, null, null, null, null, null, null)))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("not compatible");
		}
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	@Nested
	@DisplayName("publish / close lifecycle")
	class Lifecycle {

		@Test
		@DisplayName("publish DRAFT → PUBLISHED with publishedAt timestamp")
		void publish() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(evaluationRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			EvaluationResponse response = service.publishEvaluation(
					existing.getPublicUuid());

			assertThat(response.status()).isEqualTo(EvaluationStatus.PUBLISHED);
			assertThat(response.publishedAt()).isNotNull();
			assertThat(response.closedAt()).isNull();
		}

		@Test
		@DisplayName("publish PUBLISHED → 409 EVAL_ILLEGAL_TRANSITION")
		void publishTwice() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			existing.setStatus(EvaluationStatus.PUBLISHED);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> service.publishEvaluation(existing.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("legal next");
		}

		@Test
		@DisplayName("close PUBLISHED → CLOSED with closedAt timestamp")
		void close() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			existing.setStatus(EvaluationStatus.PUBLISHED);
			existing.setPublishedAt(Instant.now());
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));
			when(evaluationRepository.saveAndFlush(any()))
					.thenAnswer(inv -> inv.getArgument(0));

			EvaluationResponse response = service.closeEvaluation(
					existing.getPublicUuid());

			assertThat(response.status()).isEqualTo(EvaluationStatus.CLOSED);
			assertThat(response.closedAt()).isNotNull();
		}

		@Test
		@DisplayName("close DRAFT (never published) → 409 EVAL_ILLEGAL_TRANSITION")
		void closeWithoutPublish() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			// The state machine catches it via legalNext(): DRAFT can only
			// go to PUBLISHED, so jumping straight to CLOSED is rejected
			// with the standard "legal next states: [PUBLISHED]" message.
			assertThatThrownBy(() -> service.closeEvaluation(existing.getPublicUuid()))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("legal next");
		}
	}

	// =========================================================================
	// deleteEvaluation
	// =========================================================================

	@Nested
	@DisplayName("deleteEvaluation")
	class DeleteEvaluation {

		@Test
		@DisplayName("happy path — placeholder GradeRecord count is 0, soft-deletes")
		void happyPath() {
			TeacherAssignment assignment = newAssignment(newCourse("MAT"));
			Evaluation existing = newEvaluation(assignment, "Tarea 1",
					EvaluationKind.TASK, EvaluationScale.SCORE_0_20);
			when(evaluationRepository.findByPublicUuid(existing.getPublicUuid()))
					.thenReturn(Optional.of(existing));

			service.deleteEvaluation(existing.getPublicUuid());

			verify(evaluationRepository).delete(existing);
		}

		@Test
		@DisplayName("unknown evaluation → 404 RESOURCE_NOT_FOUND")
		void unknown() {
			UUID anyUuid = UUID.randomUUID();
			when(evaluationRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteEvaluation(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	private static Course newCourse(String code) {
		Course c = new Course();
		c.setCode(code);
		c.setName("Course " + code);
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static TeacherAssignment newAssignment(Course course) {
		TeacherAssignment a = new TeacherAssignment();
		a.setCourse(course);
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "id", UUID.randomUUID());
		a.setAssignedAt(Instant.now());
		// a.isActive() checks unassignedAt == null; default null is fine
		return a;
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

	private static Evaluation newEvaluation(TeacherAssignment assignment,
			String name, EvaluationKind kind, EvaluationScale scale) {
		Evaluation e = new Evaluation();
		e.setTeacherAssignment(assignment);
		e.setKind(kind);
		e.setName(name);
		e.setWeight(BigDecimal.valueOf(1.00));
		e.setScheduledDate(LocalDate.of(2026, 5, 1));
		e.setScale(scale);
		e.setStatus(EvaluationStatus.DRAFT);
		e.setIsActive(Boolean.TRUE);
		setField(e, "publicUuid", UUID.randomUUID());
		setField(e, "id", UUID.randomUUID());
		return e;
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
