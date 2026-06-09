package com.edushift.modules.sessions.learning.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionFilters;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LifecycleRequest;
import com.edushift.modules.sessions.learning.dto.SessionContentDto;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.sessions.learning.mapper.LearningSessionMapper;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
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

/**
 * Unit tests for {@link LearningSessionServiceImpl} (Sprint 5A - BE-5A.4).
 *
 * <p>Each nested group covers one public method on the service. The
 * cross-context validations have dedicated tests because they're the
 * trickiest part of the aggregate (unit must be in same course as
 * assignment; competencies + capacities must be in same course; date
 * must lie inside the period's window).</p>
 */
@ExtendWith(MockitoExtension.class)
class LearningSessionServiceImplTest {

	@Mock private LearningSessionRepository sessionRepository;
	@Mock private TeacherAssignmentRepository assignmentRepository;
	@Mock private UnitRepository unitRepository;
	@Mock private CompetencyRepository competencyRepository;
	@Mock private CapacityRepository capacityRepository;
	@Spy private LearningSessionMapper mapper = new LearningSessionMapper();

	@InjectMocks private LearningSessionServiceImpl service;

	// =========================================================================
	// createSession
	// =========================================================================

	@Nested
	@DisplayName("createSession")
	class CreateSession {

		@Test
		@DisplayName("happy path — saves with all fields and returns full response")
		void happyPath() {
			Fixtures fx = new Fixtures();
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(),
					fx.unit.getPublicUuid(),
					"Sesión de prueba",
					"Aprender los rectángulos",
					LocalDate.of(2026, 4, 10),
					60,
					new SessionContentDto("obj", List.of("a1"), List.of("m1"), null),
					List.of(),
					List.of()
			);

			LearningSessionResponse response = service.create(req);

			assertThat(response.title()).isEqualTo("Sesión de prueba");
			assertThat(response.status()).isEqualTo(SessionStatus.PLANNED);
			assertThat(response.scheduledDate()).isEqualTo(LocalDate.of(2026, 4, 10));
			assertThat(response.assignment().publicUuid())
					.isEqualTo(fx.assignment.getPublicUuid());
			assertThat(response.unit().publicUuid()).isEqualTo(fx.unit.getPublicUuid());
			verify(sessionRepository).saveAndFlush(any(LearningSession.class));
		}

		@Test
		@DisplayName("unknown assignment → 404 RESOURCE_NOT_FOUND")
		void unknownAssignment() {
			UUID anyUuid = UUID.randomUUID();
			when(assignmentRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					anyUuid, UUID.randomUUID(), "x", null,
					LocalDate.now(), 60, null, null, null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(ResourceNotFoundException.class);
			verify(sessionRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("soft-ended assignment → 409 ASSIGNMENT_NOT_ACTIVE")
		void softEndedAssignment() {
			Fixtures fx = new Fixtures();
			fx.assignment.setUnassignedAt(Instant.now());
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 4, 10), 60, null, null, null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("soft-ended");
		}

		@Test
		@DisplayName("unit from another course → 400 UNIT_NOT_IN_COURSE")
		void unitFromAnotherCourse() {
			Fixtures fx = new Fixtures();
			Course otherCourse = newCourse("COMU", "Comunicación");
			Unit alienUnit = newUnit(otherCourse, "Alien", 1);
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(alienUnit.getPublicUuid()))
					.thenReturn(Optional.of(alienUnit));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), alienUnit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 4, 10), 60, null, null, null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("does not belong to the assignment");
		}

		@Test
		@DisplayName("scheduledDate before period start → 400 SESSION_DATE_OUT_OF_PERIOD")
		void dateBeforePeriodStart() {
			Fixtures fx = new Fixtures();
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 1, 1), 60, null, null, null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("before period start");
		}

		@Test
		@DisplayName("scheduledDate after period end → 400 SESSION_DATE_OUT_OF_PERIOD")
		void dateAfterPeriodEnd() {
			Fixtures fx = new Fixtures();
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 12, 31), 60, null, null, null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("after period end");
		}

		@Test
		@DisplayName("competency from another course → 400 COMPETENCY_NOT_IN_COURSE")
		void competencyFromAnotherCourse() {
			Fixtures fx = new Fixtures();
			Course otherCourse = newCourse("COMU", "Comunicación");
			Competency alien = newCompetency(otherCourse, "C1", "Alien", 1);
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));
			when(competencyRepository.findAllByPublicUuidIn(anyList()))
					.thenReturn(List.of(alien));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 4, 10), 60, null,
					List.of(alien.getPublicUuid()), null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("Competency");
		}

		@Test
		@DisplayName("competency UUID does not resolve → 404 RESOURCE_NOT_FOUND")
		void competencyUnknown() {
			Fixtures fx = new Fixtures();
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));
			when(competencyRepository.findAllByPublicUuidIn(anyList()))
					.thenReturn(List.of());

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 4, 10), 60, null,
					List.of(UUID.randomUUID()), null);

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("capacity from another course → 400 CAPACITY_NOT_IN_COURSE")
		void capacityFromAnotherCourse() {
			Fixtures fx = new Fixtures();
			Course otherCourse = newCourse("COMU", "Comunicación");
			Competency alienComp = newCompetency(otherCourse, "C1", "Alien", 1);
			Capacity alienCap = newCapacity(alienComp, "C1.1", "Alien Cap", 1);
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));
			when(capacityRepository.findAllByPublicUuidIn(anyList()))
					.thenReturn(List.of(alienCap));

			CreateLearningSessionRequest req = new CreateLearningSessionRequest(
					fx.assignment.getPublicUuid(), fx.unit.getPublicUuid(), "x",
					null, LocalDate.of(2026, 4, 10), 60, null, null,
					List.of(alienCap.getPublicUuid()));

			assertThatThrownBy(() -> service.create(req))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("Capacity");
		}
	}

	// =========================================================================
	// getSession / listSessions / reverse views
	// =========================================================================

	@Nested
	@DisplayName("getSession")
	class GetSession {

		@Test
		@DisplayName("happy path → returns response")
		void happyPath() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			LearningSessionResponse response = service.get(session.getPublicUuid());

			assertThat(response.publicUuid()).isEqualTo(session.getPublicUuid());
		}

		@Test
		@DisplayName("unknown UUID → 404")
		void unknownUuid() {
			UUID anyUuid = UUID.randomUUID();
			when(sessionRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.get(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("list with filters")
	class ListSessions {

		@Test
		@DisplayName("null filters → loads with all-null params")
		void nullFilters() {
			when(sessionRepository.findFiltered(any(), any(), any(), any(),
					any(), any(), any())).thenReturn(List.of());

			List<LearningSessionListItem> result = service.list(null);

			assertThat(result).isEmpty();
			verify(sessionRepository).findFiltered(null, null, null, null,
					null, null, null);
		}

		@Test
		@DisplayName("dateFrom > dateTo → 400 VALIDATION_ERROR")
		void invertedDateRange() {
			LearningSessionFilters filters = new LearningSessionFilters(
					null, null, null, null, null,
					LocalDate.of(2026, 5, 10),
					LocalDate.of(2026, 5, 1));

			assertThatThrownBy(() -> service.list(filters))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("dateFrom");
		}

		@Test
		@DisplayName("filters propagate verbatim to repository")
		void filtersPropagate() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findFiltered(eq(fx.teacher.getPublicUuid()),
					eq(null), eq(fx.period.getPublicUuid()),
					eq(fx.unit.getPublicUuid()), eq(SessionStatus.PLANNED),
					eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30))))
					.thenReturn(List.of(session));

			LearningSessionFilters filters = new LearningSessionFilters(
					fx.teacher.getPublicUuid(), null, fx.unit.getPublicUuid(),
					fx.period.getPublicUuid(), SessionStatus.PLANNED,
					LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

			List<LearningSessionListItem> result = service.list(filters);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).publicUuid()).isEqualTo(session.getPublicUuid());
		}
	}

	@Nested
	@DisplayName("listByAssignment / listByUnit")
	class ReverseViews {

		@Test
		@DisplayName("listByAssignment happy path")
		void byAssignmentHappy() {
			Fixtures fx = new Fixtures();
			LearningSession s = fx.session();
			when(assignmentRepository.findByPublicUuid(fx.assignment.getPublicUuid()))
					.thenReturn(Optional.of(fx.assignment));
			when(sessionRepository.findAllByAssignmentOrdered(fx.assignment))
					.thenReturn(List.of(s));

			List<LearningSessionListItem> result = service.listByAssignment(
					fx.assignment.getPublicUuid());

			assertThat(result).hasSize(1);
		}

		@Test
		@DisplayName("listByAssignment unknown → 404")
		void byAssignmentUnknown() {
			UUID anyUuid = UUID.randomUUID();
			when(assignmentRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());
			assertThatThrownBy(() -> service.listByAssignment(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("listByUnit happy path")
		void byUnitHappy() {
			Fixtures fx = new Fixtures();
			LearningSession s = fx.session();
			when(unitRepository.findByPublicUuid(fx.unit.getPublicUuid()))
					.thenReturn(Optional.of(fx.unit));
			when(sessionRepository.findAllByUnitOrdered(fx.unit))
					.thenReturn(List.of(s));

			List<LearningSessionListItem> result = service.listByUnit(
					fx.unit.getPublicUuid());

			assertThat(result).hasSize(1);
		}

		@Test
		@DisplayName("listByUnit unknown → 404")
		void byUnitUnknown() {
			UUID anyUuid = UUID.randomUUID();
			when(unitRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());
			assertThatThrownBy(() -> service.listByUnit(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// updateSession / deleteSession
	// =========================================================================

	@Nested
	@DisplayName("updateSession")
	class UpdateSession {

		@Test
		@DisplayName("happy path — partial-merge title + scheduledDate")
		void happyPartialMerge() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			UpdateLearningSessionRequest patch = new UpdateLearningSessionRequest(
					null, "Nuevo título", null,
					LocalDate.of(2026, 5, 15), null, null, null, null);

			LearningSessionResponse response = service.update(
					session.getPublicUuid(), patch);

			assertThat(response.title()).isEqualTo("Nuevo título");
			assertThat(response.scheduledDate()).isEqualTo(LocalDate.of(2026, 5, 15));
		}

		@Test
		@DisplayName("empty patch → no save call, returns current state")
		void emptyPatchIsNoop() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			UpdateLearningSessionRequest patch = new UpdateLearningSessionRequest(
					null, null, null, null, null, null, null, null);

			service.update(session.getPublicUuid(), patch);
			verify(sessionRepository, never()).saveAndFlush(any());
		}

		@Test
		@DisplayName("terminal session → 409 SESSION_TRANSITION_INVALID")
		void terminalRejected() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setStatus(SessionStatus.COMPLETED);
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			UpdateLearningSessionRequest patch = new UpdateLearningSessionRequest(
					null, "x", null, null, null, null, null, null);

			assertThatThrownBy(() ->
					service.update(session.getPublicUuid(), patch))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("terminal");
		}

		@Test
		@DisplayName("unit re-pointed to another course → 400 UNIT_NOT_IN_COURSE")
		void unitCrossCourse() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			Course otherCourse = newCourse("COMU", "Comunicación");
			Unit alienUnit = newUnit(otherCourse, "Alien", 1);
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(unitRepository.findByPublicUuid(alienUnit.getPublicUuid()))
					.thenReturn(Optional.of(alienUnit));

			UpdateLearningSessionRequest patch = new UpdateLearningSessionRequest(
					alienUnit.getPublicUuid(), null, null, null, null, null, null, null);

			assertThatThrownBy(() ->
					service.update(session.getPublicUuid(), patch))
					.isInstanceOf(BadRequestException.class)
					.hasMessageContaining("does not belong");
		}

		@Test
		@DisplayName("post-merge scheduledDate out of period → 400")
		void dateOutOfPeriodAfterMerge() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			UpdateLearningSessionRequest patch = new UpdateLearningSessionRequest(
					null, null, null,
					LocalDate.of(2027, 1, 1), null, null, null, null);

			assertThatThrownBy(() ->
					service.update(session.getPublicUuid(), patch))
					.isInstanceOf(BadRequestException.class);
		}
	}

	@Nested
	@DisplayName("deleteSession")
	class DeleteSession {

		@Test
		@DisplayName("happy path — soft-deletes")
		void happyPath() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			service.delete(session.getPublicUuid());
			verify(sessionRepository).delete(session);
		}

		@Test
		@DisplayName("unknown UUID → 404")
		void unknownUuid() {
			UUID anyUuid = UUID.randomUUID();
			when(sessionRepository.findByPublicUuid(anyUuid))
					.thenReturn(Optional.empty());
			assertThatThrownBy(() -> service.delete(anyUuid))
					.isInstanceOf(ResourceNotFoundException.class);
			verify(sessionRepository, never()).delete(any(LearningSession.class));
		}
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	@Nested
	@DisplayName("start lifecycle")
	class StartLifecycle {

		@Test
		@DisplayName("PLANNED -> IN_PROGRESS, stamps started_at")
		void happyPath() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			LearningSessionResponse response = service.start(session.getPublicUuid(),
					new LifecycleRequest(0L, null));

			assertThat(response.status()).isEqualTo(SessionStatus.IN_PROGRESS);
			assertThat(response.startedAt()).isNotNull();
		}

		@Test
		@DisplayName("already IN_PROGRESS -> 409 SESSION_TRANSITION_INVALID")
		void wrongStatus() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setStatus(SessionStatus.IN_PROGRESS);
			session.setStartedAt(Instant.now());
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			assertThatThrownBy(() -> service.start(session.getPublicUuid(),
					new LifecycleRequest(0L, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("Cannot transition");
		}

		@Test
		@DisplayName("version mismatch -> 409 SESSION_VERSION_CONFLICT")
		void versionConflict() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setVersion(7L);
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			assertThatThrownBy(() -> service.start(session.getPublicUuid(),
					new LifecycleRequest(3L, null)))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("modified concurrently");
		}
	}

	@Nested
	@DisplayName("complete lifecycle")
	class CompleteLifecycle {

		@Test
		@DisplayName("IN_PROGRESS -> COMPLETED, stamps ended_at")
		void happyPath() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setStatus(SessionStatus.IN_PROGRESS);
			session.setStartedAt(Instant.now());
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			LearningSessionResponse response = service.complete(
					session.getPublicUuid(), new LifecycleRequest(0L, null));

			assertThat(response.status()).isEqualTo(SessionStatus.COMPLETED);
			assertThat(response.endedAt()).isNotNull();
		}

		@Test
		@DisplayName("from PLANNED -> 409 (cannot skip start)")
		void fromPlannedRejected() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			assertThatThrownBy(() -> service.complete(session.getPublicUuid(),
					new LifecycleRequest(0L, null)))
					.isInstanceOf(ConflictException.class);
		}
	}

	@Nested
	@DisplayName("cancel lifecycle")
	class CancelLifecycle {

		@Test
		@DisplayName("PLANNED -> CANCELLED")
		void cancelFromPlanned() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			LearningSessionResponse response = service.cancel(
					session.getPublicUuid(), new LifecycleRequest(0L, null));

			assertThat(response.status()).isEqualTo(SessionStatus.CANCELLED);
			assertThat(response.cancelledAt()).isNotNull();
		}

		@Test
		@DisplayName("IN_PROGRESS -> CANCELLED")
		void cancelFromInProgress() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setStatus(SessionStatus.IN_PROGRESS);
			session.setStartedAt(Instant.now());
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			LearningSessionResponse response = service.cancel(
					session.getPublicUuid(), new LifecycleRequest(0L, "no clase"));

			assertThat(response.status()).isEqualTo(SessionStatus.CANCELLED);
		}

		@Test
		@DisplayName("with reason -> appended to objective with [CANCELLED] tag")
		void cancelWithReason() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setObjective("Learn rectangles");
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));
			when(sessionRepository.saveAndFlush(any(LearningSession.class)))
					.thenAnswer(inv -> inv.getArgument(0));

			LearningSessionResponse response = service.cancel(
					session.getPublicUuid(),
					new LifecycleRequest(0L, "Día feriado nacional"));

			assertThat(response.objective())
					.contains("[CANCELLED]")
					.contains("Día feriado nacional")
					.contains("Learn rectangles");
		}

		@Test
		@DisplayName("already CANCELLED -> 409")
		void alreadyTerminal() {
			Fixtures fx = new Fixtures();
			LearningSession session = fx.session();
			session.setStatus(SessionStatus.CANCELLED);
			session.setCancelledAt(Instant.now());
			when(sessionRepository.findByPublicUuid(session.getPublicUuid()))
					.thenReturn(Optional.of(session));

			assertThatThrownBy(() -> service.cancel(session.getPublicUuid(),
					new LifecycleRequest(0L, null)))
					.isInstanceOf(ConflictException.class);
		}
	}

	// =========================================================================
	// Helpers
	// =========================================================================

	/**
	 * Reusable fixture wiring an active assignment + matching unit +
	 * an academic period that runs from 2026-03-01 to 2026-07-31. Each
	 * test gets a fresh instance so collections are isolated.
	 */
	private static class Fixtures {
		final Course course = newCourse("MAT", "Matemática");
		final Section section = newSection("1A");
		final Teacher teacher = newTeacher("Ana", "Pérez");
		final AcademicPeriod period = newPeriod(
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31));
		final TeacherAssignment assignment = newAssignment(
				teacher, section, course, period);
		final Unit unit = newUnit(course, "Unidad I", 1);

		LearningSession session() {
			LearningSession s = new LearningSession();
			s.setTeacherAssignment(assignment);
			s.setUnit(unit);
			s.setTitle("Sesión 1");
			s.setObjective("Obj 1");
			s.setScheduledDate(LocalDate.of(2026, 4, 10));
			s.setDurationMinutes(60);
			s.setStatus(SessionStatus.PLANNED);
			s.setVersion(0L);
			s.setCompetencies(new HashSet<>());
			s.setCapacities(new HashSet<>());
			setField(s, "publicUuid", UUID.randomUUID());
			setField(s, "id", UUID.randomUUID());
			return s;
		}
	}

	private static Course newCourse(String code, String name) {
		Course c = new Course();
		c.setCode(code);
		c.setName(name);
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static Section newSection(String name) {
		Section s = new Section();
		s.setName(name);
		setField(s, "publicUuid", UUID.randomUUID());
		setField(s, "id", UUID.randomUUID());
		return s;
	}

	private static Teacher newTeacher(String first, String last) {
		Teacher t = new Teacher();
		t.setFirstName(first);
		t.setLastName(last);
		setField(t, "publicUuid", UUID.randomUUID());
		setField(t, "id", UUID.randomUUID());
		return t;
	}

	private static AcademicPeriod newPeriod(LocalDate start, LocalDate end) {
		AcademicPeriod p = new AcademicPeriod();
		p.setName("Bimestre I");
		p.setOrdinal(1);
		p.setStartDate(start);
		p.setEndDate(end);
		setField(p, "publicUuid", UUID.randomUUID());
		setField(p, "id", UUID.randomUUID());
		return p;
	}

	private static TeacherAssignment newAssignment(Teacher t, Section s,
			Course c, AcademicPeriod p) {
		TeacherAssignment a = new TeacherAssignment();
		a.setTeacher(t);
		a.setSection(s);
		a.setCourse(c);
		a.setAcademicPeriod(p);
		a.setAssignedAt(Instant.now());
		setField(a, "publicUuid", UUID.randomUUID());
		setField(a, "id", UUID.randomUUID());
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

	private static Competency newCompetency(Course course, String code,
			String name, int displayOrder) {
		Competency c = new Competency();
		c.setCourse(course);
		c.setCode(code);
		c.setName(name);
		c.setDisplayOrder(displayOrder);
		c.setIsActive(Boolean.TRUE);
		setField(c, "publicUuid", UUID.randomUUID());
		setField(c, "id", UUID.randomUUID());
		return c;
	}

	private static Capacity newCapacity(Competency competency, String code,
			String name, int displayOrder) {
		Capacity c = new Capacity();
		c.setCompetency(competency);
		c.setCode(code);
		c.setName(name);
		c.setDisplayOrder(displayOrder);
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
