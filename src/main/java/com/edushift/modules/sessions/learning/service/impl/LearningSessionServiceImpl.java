package com.edushift.modules.sessions.learning.service.impl;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.period.entity.AcademicPeriod;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.sessions.learning.dto.CreateLearningSessionRequest;
import com.edushift.modules.sessions.learning.dto.LearningSessionFilters;
import com.edushift.modules.sessions.learning.dto.LearningSessionListItem;
import com.edushift.modules.sessions.learning.dto.LearningSessionResponse;
import com.edushift.modules.sessions.learning.dto.LifecycleRequest;
import com.edushift.modules.sessions.learning.dto.UpdateLearningSessionRequest;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.sessions.learning.mapper.LearningSessionMapper;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.sessions.learning.service.LearningSessionService;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link LearningSessionService}.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>CRUD with cross-context validation
 *       ({@code SESSION_DATE_OUT_OF_PERIOD}, {@code UNIT_NOT_IN_COURSE},
 *       {@code COMPETENCY_NOT_IN_COURSE}, {@code CAPACITY_NOT_IN_COURSE}).</li>
 *   <li>State machine via {@link SessionStatus#canTransitionTo} for
 *       lifecycle endpoints.</li>
 *   <li>Optimistic locking on lifecycle transitions: callers send the
 *       version they loaded with; mismatch is 409
 *       {@code SESSION_VERSION_CONFLICT}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningSessionServiceImpl implements LearningSessionService {

	private final LearningSessionRepository sessionRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final UnitRepository unitRepository;
	private final CompetencyRepository competencyRepository;
	private final CapacityRepository capacityRepository;
	private final LearningSessionMapper mapper;

	// =========================================================================
	// CRUD
	// =========================================================================

	@Override
	@Transactional
	public LearningSessionResponse create(CreateLearningSessionRequest request) {
		TeacherAssignment assignment = loadAssignment(request.assignmentUuid());
		ensureAssignmentActive(assignment);

		Unit unit = loadUnit(request.unitUuid());
		ensureUnitInAssignmentCourse(unit, assignment);

		ensureScheduledDateInPeriod(request.scheduledDate(),
				assignment.getAcademicPeriod());

		Set<Competency> competencies = resolveCompetencies(
				request.competencyUuids(), assignment.getCourse());
		Set<Capacity> capacities = resolveCapacities(
				request.capacityUuids(), assignment.getCourse());

		LearningSession session = mapper.fromCreate(request, assignment, unit);
		session.setCompetencies(competencies);
		session.setCapacities(capacities);

		LearningSession saved = sessionRepository.saveAndFlush(session);

		log.info("[learning-session] created -- publicUuid={} assignment={} unit={} date={} competencies={} capacities={}",
				saved.getPublicUuid(), assignment.getPublicUuid(),
				unit.getPublicUuid(), saved.getScheduledDate(),
				competencies.size(), capacities.size());

		return mapper.toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public LearningSessionResponse get(UUID publicUuid) {
		return mapper.toResponse(loadSession(publicUuid));
	}

	@Override
	@Transactional(readOnly = true)
	public List<LearningSessionListItem> list(LearningSessionFilters filters) {
		LearningSessionFilters effective = (filters == null)
				? new LearningSessionFilters(null, null, null, null, null, null, null)
				: filters;
		validateDateRange(effective.dateFrom(), effective.dateTo());

		// JPQL navigates the path a.teacher.publicUuid etc., so we feed
		// the public UUIDs directly. Hibernate auto-applies the tenant
		// filter on top of every joined entity.
		return sessionRepository.findFiltered(
						effective.teacherUuid(),
						effective.sectionUuid(),
						effective.periodUuid(),
						effective.unitUuid(),
						effective.status(),
						effective.dateFrom(),
						effective.dateTo())
				.stream()
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional
	public LearningSessionResponse update(UUID publicUuid,
			UpdateLearningSessionRequest request) {
		LearningSession session = loadSession(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(session);
		}

		ensureMutable(session);

		// Re-point the unit if requested, validating same-course.
		if (request.unitUuid() != null
				&& !Objects.equals(request.unitUuid(),
						session.getUnit().getPublicUuid())) {
			Unit newUnit = loadUnit(request.unitUuid());
			ensureUnitInAssignmentCourse(newUnit, session.getTeacherAssignment());
			session.setUnit(newUnit);
		}

		// Apply scalar partial-merge BEFORE date validation so we
		// validate against the post-merge state.
		mapper.applyUpdate(request, session);

		if (request.scheduledDate() != null) {
			ensureScheduledDateInPeriod(session.getScheduledDate(),
					session.getTeacherAssignment().getAcademicPeriod());
		}

		// Replace association sets when explicitly provided (even
		// empty list = clear).
		Course course = session.getTeacherAssignment().getCourse();
		if (request.competencyUuids() != null) {
			session.setCompetencies(resolveCompetencies(
					request.competencyUuids(), course));
		}
		if (request.capacityUuids() != null) {
			session.setCapacities(resolveCapacities(
					request.capacityUuids(), course));
		}

		LearningSession saved = sessionRepository.saveAndFlush(session);
		log.info("[learning-session] updated -- publicUuid={} status={} date={}",
				saved.getPublicUuid(), saved.getStatus(), saved.getScheduledDate());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public void delete(UUID publicUuid) {
		LearningSession session = loadSession(publicUuid);

		// Placeholder for future attendance integration (Sprint 6).
		// The error code is reserved here so the FE can rely on it
		// without rev-ing the API once attendance lands.
		// if (attendanceRepository.existsBySession(session)) {
		//     throw new ConflictException("SESSION_HAS_ATTENDANCE", "...");
		// }

		sessionRepository.delete(session);
		log.info("[learning-session] deleted -- publicUuid={} status={}",
				session.getPublicUuid(), session.getStatus());
	}

	// =========================================================================
	// Reverse views
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<LearningSessionListItem> listByAssignment(UUID assignmentUuid) {
		TeacherAssignment assignment = loadAssignment(assignmentUuid);
		return sessionRepository.findAllByAssignmentOrdered(assignment).stream()
				.map(mapper::toListItem)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<LearningSessionListItem> listByUnit(UUID unitUuid) {
		Unit unit = loadUnit(unitUuid);
		return sessionRepository.findAllByUnitOrdered(unit).stream()
				.map(mapper::toListItem)
				.toList();
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	@Override
	@Transactional
	public LearningSessionResponse start(UUID publicUuid, LifecycleRequest request) {
		LearningSession session = loadSession(publicUuid);
		ensureVersion(session, request);
		ensureTransition(session, SessionStatus.IN_PROGRESS);

		session.setStatus(SessionStatus.IN_PROGRESS);
		session.setStartedAt(Instant.now());

		LearningSession saved = sessionRepository.saveAndFlush(session);
		log.info("[learning-session] started -- publicUuid={}", saved.getPublicUuid());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public LearningSessionResponse complete(UUID publicUuid, LifecycleRequest request) {
		LearningSession session = loadSession(publicUuid);
		ensureVersion(session, request);
		ensureTransition(session, SessionStatus.COMPLETED);

		Instant now = Instant.now();
		// Defensive: started_at is required by the DB CHECK and by the
		// state machine (PLANNED can't go straight to COMPLETED).
		if (session.getStartedAt() == null) {
			session.setStartedAt(now);
		}
		session.setStatus(SessionStatus.COMPLETED);
		session.setEndedAt(now);

		LearningSession saved = sessionRepository.saveAndFlush(session);
		log.info("[learning-session] completed -- publicUuid={}", saved.getPublicUuid());
		return mapper.toResponse(saved);
	}

	@Override
	@Transactional
	public LearningSessionResponse cancel(UUID publicUuid, LifecycleRequest request) {
		LearningSession session = loadSession(publicUuid);
		ensureVersion(session, request);
		ensureTransition(session, SessionStatus.CANCELLED);

		session.setStatus(SessionStatus.CANCELLED);
		session.setCancelledAt(Instant.now());
		// Append cancellation reason to objective if provided (no
		// dedicated column - keep the schema lean for MVP).
		if (request != null && request.reason() != null
				&& !request.reason().isBlank()) {
			String reason = request.reason().trim();
			String trail = "[CANCELLED] " + reason;
			session.setObjective(
					session.getObjective() == null
							? trail
							: session.getObjective() + "\n" + trail);
		}

		LearningSession saved = sessionRepository.saveAndFlush(session);
		log.info("[learning-session] cancelled -- publicUuid={}", saved.getPublicUuid());
		return mapper.toResponse(saved);
	}

	// =========================================================================
	// Loaders
	// =========================================================================

	private LearningSession loadSession(UUID publicUuid) {
		return sessionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"LearningSession", publicUuid));
	}

	private TeacherAssignment loadAssignment(UUID publicUuid) {
		return assignmentRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"TeacherAssignment", publicUuid));
	}

	private Unit loadUnit(UUID publicUuid) {
		return unitRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Unit", publicUuid));
	}

	// =========================================================================
	// Cross-context validation
	// =========================================================================

	private static void ensureAssignmentActive(TeacherAssignment assignment) {
		if (!assignment.isActive()) {
			throw new ConflictException("ASSIGNMENT_NOT_ACTIVE",
					"Assignment " + assignment.getPublicUuid()
							+ " has been soft-ended; cannot create or update sessions on it");
		}
	}

	private static void ensureUnitInAssignmentCourse(Unit unit,
			TeacherAssignment assignment) {
		Course assignmentCourse = assignment.getCourse();
		Course unitCourse = unit.getCourse();
		if (assignmentCourse == null || unitCourse == null
				|| !Objects.equals(assignmentCourse.getId(), unitCourse.getId())) {
			throw new BadRequestException("UNIT_NOT_IN_COURSE",
					"Unit " + unit.getPublicUuid()
							+ " does not belong to the assignment's course");
		}
		if (Boolean.FALSE.equals(unit.getIsActive())) {
			throw new BadRequestException("UNIT_NOT_IN_COURSE",
					"Unit " + unit.getPublicUuid() + " is inactive");
		}
	}

	private static void ensureScheduledDateInPeriod(LocalDate scheduledDate,
			AcademicPeriod period) {
		if (scheduledDate == null || period == null) {
			return; // already covered by Bean Validation / loaders
		}
		LocalDate start = period.getStartDate();
		LocalDate end = period.getEndDate();
		if (start != null && scheduledDate.isBefore(start)) {
			throw new BadRequestException("SESSION_DATE_OUT_OF_PERIOD",
					"scheduledDate " + scheduledDate + " is before period start " + start);
		}
		if (end != null && scheduledDate.isAfter(end)) {
			throw new BadRequestException("SESSION_DATE_OUT_OF_PERIOD",
					"scheduledDate " + scheduledDate + " is after period end " + end);
		}
	}

	private Set<Competency> resolveCompetencies(List<UUID> publicUuids,
			Course expectedCourse) {
		if (publicUuids == null || publicUuids.isEmpty()) {
			return new HashSet<>();
		}
		List<UUID> dedup = publicUuids.stream().filter(Objects::nonNull).distinct().toList();
		List<Competency> found = competencyRepository.findAllByPublicUuidIn(dedup);
		if (found.size() != dedup.size()) {
			throw new ResourceNotFoundException("Competency",
					"One or more competencyUuids do not resolve in this tenant");
		}
		for (Competency c : found) {
			if (Boolean.FALSE.equals(c.getIsActive())
					|| !Objects.equals(c.getCourse().getId(), expectedCourse.getId())) {
				throw new BadRequestException("COMPETENCY_NOT_IN_COURSE",
						"Competency " + c.getPublicUuid()
								+ " does not belong to the assignment's course");
			}
		}
		return new HashSet<>(found);
	}

	private Set<Capacity> resolveCapacities(List<UUID> publicUuids,
			Course expectedCourse) {
		if (publicUuids == null || publicUuids.isEmpty()) {
			return new HashSet<>();
		}
		List<UUID> dedup = publicUuids.stream().filter(Objects::nonNull).distinct().toList();
		List<Capacity> found = capacityRepository.findAllByPublicUuidIn(dedup);
		if (found.size() != dedup.size()) {
			throw new ResourceNotFoundException("Capacity",
					"One or more capacityUuids do not resolve in this tenant");
		}
		for (Capacity c : found) {
			Competency parent = c.getCompetency();
			if (Boolean.FALSE.equals(c.getIsActive())
					|| parent == null
					|| !Objects.equals(parent.getCourse().getId(), expectedCourse.getId())) {
				throw new BadRequestException("CAPACITY_NOT_IN_COURSE",
						"Capacity " + c.getPublicUuid()
								+ " does not belong to a competency of the assignment's course");
			}
		}
		return new HashSet<>(found);
	}

	private static void validateDateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BadRequestException("VALIDATION_ERROR",
					"dateFrom (" + from + ") must be on or before dateTo (" + to + ")");
		}
	}

	// =========================================================================
	// Lifecycle helpers
	// =========================================================================

	private static void ensureMutable(LearningSession session) {
		if (session.getStatus() != null && session.getStatus().isTerminal()) {
			throw new ConflictException("SESSION_TRANSITION_INVALID",
					"Session " + session.getPublicUuid()
							+ " is " + session.getStatus()
							+ "; terminal sessions cannot be edited");
		}
	}

	private static void ensureTransition(LearningSession session, SessionStatus target) {
		SessionStatus current = session.getStatus();
		if (current == null || !current.canTransitionTo(target)) {
			throw new ConflictException("SESSION_TRANSITION_INVALID",
					"Cannot transition session " + session.getPublicUuid()
							+ " from " + current + " to " + target);
		}
	}

	private static void ensureVersion(LearningSession session, LifecycleRequest request) {
		if (request == null || request.version() == null) {
			throw new BadRequestException("VALIDATION_ERROR",
					"version is required for lifecycle transitions");
		}
		Long currentVersion = session.getVersion();
		// JPA initialises @Version to 0 on insert; null only happens for
		// transient sessions, which can't reach this code path.
		if (currentVersion != null && !currentVersion.equals(request.version())) {
			throw new ConflictException("SESSION_VERSION_CONFLICT",
					"Session " + session.getPublicUuid()
							+ " has been modified concurrently (expected version "
							+ request.version() + ", current "
							+ currentVersion + "); reload and retry");
		}
	}

}
