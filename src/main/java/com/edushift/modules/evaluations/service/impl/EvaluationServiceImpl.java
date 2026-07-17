package com.edushift.modules.evaluations.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.unit.entity.Unit;
import com.edushift.modules.academic.unit.repository.UnitRepository;
import com.edushift.modules.evaluations.dto.CreateEvaluationRequest;
import com.edushift.modules.evaluations.dto.EvaluationFilters;
import com.edushift.modules.evaluations.dto.EvaluationListItem;
import com.edushift.modules.evaluations.dto.EvaluationResponse;
import com.edushift.modules.evaluations.dto.UpdateEvaluationRequest;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.error.EvaluationErrorCodes;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.mapper.EvaluationMapper;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.service.EvaluationService;
import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link EvaluationService} (Sprint 5B / BE-5B.1).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>CRUD anchored on a {@link TeacherAssignment}.</li>
 *   <li>Cross-context validation: kind/scale coherence (ADR-5B.2), unit
 *       belongs to the same course as the assignment, learning session
 *       belongs to the same assignment, and the date window
 *       {@code dueDate >= scheduledDate}.</li>
 *   <li>Lifecycle state machine ({@code EvaluationStatus.legalNext()})
 *       with terminal {@code CLOSED} that fully locks the row.</li>
 *   <li>Editability matrix for {@code PUT}: {@code DRAFT} = any field;
 *       {@code PUBLISHED} = only {@code description} / {@code dueDate};
 *       {@code CLOSED} = no fields (rejected with {@code EVAL_CLOSED}).</li>
 *   <li>Soft-delete with the {@code EVAL_HAS_GRADES} guard (BE-5B.3) — refuses
 *       deletion when {@code GradeRecord}s point at the row.</li>
 *   <li>{@code gradeCount} on every read/write response is the live
 *       count from {@code GradeRecordRepository.countByEvaluation}
 *       (BE-5B.4 plug). For listings the count is per-row (N+1
 *       acceptable for the current page sizes; tracked as
 *       DEBT-EVAL-N for batch-aggregation when sections grow large).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

	private final EvaluationRepository evaluationRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final UnitRepository unitRepository;
	private final LearningSessionRepository sessionRepository;
	private final GradeRecordRepository gradeRecordRepository;
	private final EvaluationMapper mapper;
	private final ApplicationEventPublisher eventPublisher; // Sprint 9 / BE-9.3
	private final StudentEnrollmentRepository studentEnrollmentRepository; // Sprint 9 / BE-9.3

	// =========================================================================
	// Reads
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<EvaluationListItem> listEvaluations(
			UUID assignmentPublicUuid, EvaluationFilters filters) {
		TeacherAssignment assignment = loadAssignment(assignmentPublicUuid);

		EvaluationFilters effective = (filters == null)
				? new EvaluationFilters(null, null, null, null)
				: filters;

		List<Evaluation> rows = evaluationRepository.findFiltered(
				assignment,
				effective.status(),
				effective.isActive(),
				effective.from(),
				effective.to());

		if (rows.isEmpty()) return List.of();
		return rows.stream()
				.map(e -> mapper.toListItem(e,
						gradeRecordRepository.countByEvaluation(e)))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public EvaluationResponse getEvaluation(UUID publicUuid) {
		Evaluation evaluation = loadEvaluation(publicUuid);
		return mapper.toResponse(evaluation,
				gradeRecordRepository.countByEvaluation(evaluation));
	}

	// =========================================================================
	// Writes
	// =========================================================================

	@Override
	@Transactional
	public EvaluationResponse createEvaluation(
			UUID assignmentPublicUuid, CreateEvaluationRequest request) {
		TeacherAssignment assignment = loadAssignment(assignmentPublicUuid);
		ensureAssignmentActive(assignment);

		assertKindScaleCoherent(request.kind(), request.scale());
		validateDateWindow(request.scheduledDate(), request.dueDate());
		ensureNameAvailable(assignment, request.name().trim(), null);

		Evaluation evaluation = mapper.fromCreate(request, assignment);

		// Anchor wiring (with cross-context validation). Both anchors
		// are optional; either may be set. UUIDs travel as String in
		// the DTOs so Bean Validation can shape the error; we parse
		// here against a non-blank check.
		if (request.unitPublicUuid() != null
				&& !request.unitPublicUuid().isBlank()) {
			Unit unit = loadUnit(parseUuid(request.unitPublicUuid()));
			ensureUnitInAssignmentCourse(unit, assignment);
			evaluation.setUnit(unit);
		}
		if (request.learningSessionPublicUuid() != null
				&& !request.learningSessionPublicUuid().isBlank()) {
			LearningSession session = loadSession(
					parseUuid(request.learningSessionPublicUuid()));
			ensureSessionInAssignment(session, assignment);
			evaluation.setLearningSession(session);
		}

		Evaluation saved;
		try {
			saved = evaluationRepository.saveAndFlush(evaluation);
		}
		catch (DataIntegrityViolationException ex) {
			// uk_evaluations_tenant_assignment_name_ci fired — concurrent
			// insert grabbed the same name first.
			throw new ConflictException(EvaluationErrorCodes.EVAL_NAME_EXISTS,
					"Another evaluation in this assignment already uses the name '"
							+ request.name() + "'", ex);
		}

		log.info("[evaluations] created -- publicUuid={} assignment={} kind={} scale={} status={}",
				saved.getPublicUuid(), assignment.getPublicUuid(),
				saved.getKind(), saved.getScale(), saved.getStatus());

		// gradeCount is necessarily 0 on a fresh row, but we keep the
		// call to mirror getEvaluation's contract (and future-proof if
		// we ever stop creating in DRAFT-without-grades).
		return mapper.toResponse(saved,
				gradeRecordRepository.countByEvaluation(saved));
	}

	@Override
	@Transactional
	public EvaluationResponse updateEvaluation(
			UUID publicUuid, UpdateEvaluationRequest request) {
		Evaluation evaluation = loadEvaluation(publicUuid);

		if (request == null || request.isEmpty()) {
			return mapper.toResponse(evaluation,
					gradeRecordRepository.countByEvaluation(evaluation));
		}

		// Lifecycle gate. PUBLISHED only allows description + dueDate
		// + isActive patches. CLOSED rejects every write.
		EvaluationStatus status = evaluation.getStatus();
		if (status == EvaluationStatus.CLOSED) {
			throw new ConflictException(EvaluationErrorCodes.EVAL_CLOSED,
					"Evaluation " + publicUuid + " is CLOSED and cannot be modified");
		}
		if (status == EvaluationStatus.PUBLISHED
				&& hasNonPublishedEditableField(request)) {
			throw new ConflictException(EvaluationErrorCodes.EVAL_NOT_EDITABLE,
					"Evaluation " + publicUuid + " is PUBLISHED; only description, "
							+ "dueDate and isActive are editable");
		}

		// Cross-context checks only if the anchor is changing.
		TeacherAssignment assignment = evaluation.getTeacherAssignment();
		if (request.unitPublicUuid() != null) {
			if (request.unitPublicUuid().isBlank()) {
				// Explicit "clear anchor" payload.
				evaluation.setUnit(null);
			}
			else {
				Unit newUnit = loadUnit(parseUuid(request.unitPublicUuid()));
				ensureUnitInAssignmentCourse(newUnit, assignment);
				evaluation.setUnit(newUnit);
			}
		}
		if (request.learningSessionPublicUuid() != null) {
			if (request.learningSessionPublicUuid().isBlank()) {
				evaluation.setLearningSession(null);
			}
			else {
				LearningSession newSession = loadSession(
						parseUuid(request.learningSessionPublicUuid()));
				ensureSessionInAssignment(newSession, assignment);
				evaluation.setLearningSession(newSession);
			}
		}

		// Compute the effective kind/scale so we can validate
		// coherence against the post-merge state.
		EvaluationKind effectiveKind = request.kind() != null
				? request.kind() : evaluation.getKind();
		EvaluationScale effectiveScale = request.scale() != null
				? request.scale() : evaluation.getScale();
		assertKindScaleCoherent(effectiveKind, effectiveScale);

		// Date window against the post-merge state.
		LocalDate effectiveScheduled = request.scheduledDate() != null
				? request.scheduledDate() : evaluation.getScheduledDate();
		LocalDate effectiveDue = request.dueDate() != null
				? request.dueDate() : evaluation.getDueDate();
		validateDateWindow(effectiveScheduled, effectiveDue);

		// Name uniqueness against the post-merge state.
		if (request.name() != null
				&& !request.name().trim().equalsIgnoreCase(evaluation.getName())) {
			ensureNameAvailable(assignment, request.name().trim(),
					evaluation.getId());
		}

		mapper.applyUpdate(request, evaluation);

		Evaluation saved;
		try {
			saved = evaluationRepository.saveAndFlush(evaluation);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException(EvaluationErrorCodes.EVAL_NAME_EXISTS,
					"Another evaluation in this assignment already uses the name '"
							+ request.name() + "'", ex);
		}

		log.info("[evaluations] updated -- publicUuid={} status={}",
				saved.getPublicUuid(), saved.getStatus());

		return mapper.toResponse(saved,
				gradeRecordRepository.countByEvaluation(saved));
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	@Override
	@Transactional
	public EvaluationResponse publishEvaluation(UUID publicUuid) {
		Evaluation evaluation = loadEvaluation(publicUuid);
		ensureTransition(evaluation, EvaluationStatus.PUBLISHED);

		evaluation.setStatus(EvaluationStatus.PUBLISHED);
		evaluation.setPublishedAt(Instant.now());

		Evaluation saved = evaluationRepository.saveAndFlush(evaluation);
		log.info("[evaluations] published -- publicUuid={} at={}",
				saved.getPublicUuid(), saved.getPublishedAt());

		// Sprint 9 / BE-9.3 — fire GRADE_PUBLISHED to all enrolled students.
		Section section = saved.getTeacherAssignment().getSection();
		List<StudentEnrollment> enrolled = studentEnrollmentRepository.findActiveBySection(section);
		if (!enrolled.isEmpty()) {
			List<com.edushift.modules.notifications.event.NotificationEvent.Recipient> recipients = enrolled.stream()
					.map(e -> e.getStudent().getUserId())
					.filter(Objects::nonNull)
					.map(uid -> new com.edushift.modules.notifications.event.NotificationEvent.Recipient(uid, null))
					.toList();
			String courseName = saved.getTeacherAssignment().getCourse().getName();
			String evalTitle = saved.getName();
			recipients.forEach(r -> eventPublisher.publishEvent(
					com.edushift.modules.notifications.event.NotificationEvent.builder()
							.templateKey("GRADE_PUBLISHED")
							.category(com.edushift.modules.notifications.entity.Notification.Category.GRADE)
							.sourceId(saved.getPublicUuid())
							.recipients(java.util.List.of(r))
							.payload(java.util.Map.of(
									"studentName", "",
									"evaluationTitle", evalTitle,
									"courseName", courseName,
									"grade", "",
									"maxGrade", ""))
							.build()));
		}

		return mapper.toResponse(saved,
				gradeRecordRepository.countByEvaluation(saved));
	}

	@Override
	@Transactional
	public EvaluationResponse closeEvaluation(UUID publicUuid) {
		Evaluation evaluation = loadEvaluation(publicUuid);
		ensureTransition(evaluation, EvaluationStatus.CLOSED);

		// Defensive: closing a never-published evaluation is forbidden
		// (it would skip the publish step). The state machine catches
		// this via legalNext(), but we double-check the timestamp shape
		// for a clearer message.
		if (evaluation.getPublishedAt() == null) {
			throw new ConflictException(EvaluationErrorCodes.EVAL_ILLEGAL_TRANSITION,
					"Evaluation " + publicUuid + " cannot be CLOSED before being PUBLISHED");
		}

		evaluation.setStatus(EvaluationStatus.CLOSED);
		evaluation.setClosedAt(Instant.now());

		Evaluation saved = evaluationRepository.saveAndFlush(evaluation);
		log.info("[evaluations] closed -- publicUuid={} at={}",
				saved.getPublicUuid(), saved.getClosedAt());

		return mapper.toResponse(saved,
				gradeRecordRepository.countByEvaluation(saved));
	}

	// =========================================================================
	// Delete
	// =========================================================================

	@Override
	@Transactional
	public void deleteEvaluation(UUID publicUuid) {
		Evaluation evaluation = loadEvaluation(publicUuid);

		// BE-5B.3 — guard delete when there are GradeRecords attached.
		// Soft-delete on the evaluation would orphan the grade rows
		// (audit-history pollution + risk of leaving them invisible
		// while still occupying the unique (eval, student) slot).
		if (gradeRecordRepository.existsByEvaluation(evaluation)) {
			throw new ConflictException(EvaluationErrorCodes.EVAL_HAS_GRADES,
					"Evaluation " + publicUuid
							+ " has grade records attached; delete the grades first"
							+ " or close the evaluation");
		}

		evaluationRepository.delete(evaluation);
		log.info("[evaluations] deleted -- publicUuid={} status={}",
				publicUuid, evaluation.getStatus());
	}

	// =========================================================================
	// Loaders
	// =========================================================================

	private Evaluation loadEvaluation(UUID publicUuid) {
		return evaluationRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException("Evaluation", publicUuid));
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

	private LearningSession loadSession(UUID publicUuid) {
		return sessionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new ResourceNotFoundException(
						"LearningSession", publicUuid));
	}

	// =========================================================================
	// Cross-context validation
	// =========================================================================

	private static void ensureAssignmentActive(TeacherAssignment assignment) {
		if (!assignment.isActive()) {
			throw new ConflictException("ASSIGNMENT_NOT_ACTIVE",
					"Assignment " + assignment.getPublicUuid()
							+ " has been soft-ended; cannot create evaluations on it");
		}
	}

	private static void ensureUnitInAssignmentCourse(Unit unit,
			TeacherAssignment assignment) {
		if (unit.getCourse() == null
				|| assignment.getCourse() == null
				|| !Objects.equals(unit.getCourse().getId(),
						assignment.getCourse().getId())) {
			throw new BadRequestException(
					EvaluationErrorCodes.EVAL_UNIT_NOT_IN_COURSE,
					"Unit " + unit.getPublicUuid()
							+ " does not belong to the assignment's course");
		}
		if (Boolean.FALSE.equals(unit.getIsActive())) {
			throw new BadRequestException(
					EvaluationErrorCodes.EVAL_UNIT_NOT_IN_COURSE,
					"Unit " + unit.getPublicUuid() + " is inactive");
		}
	}

	private static void ensureSessionInAssignment(LearningSession session,
			TeacherAssignment assignment) {
		if (session.getTeacherAssignment() == null
				|| !Objects.equals(session.getTeacherAssignment().getId(),
						assignment.getId())) {
			throw new BadRequestException(
					EvaluationErrorCodes.EVAL_SESSION_NOT_IN_ASSIGNMENT,
					"LearningSession " + session.getPublicUuid()
							+ " does not belong to assignment "
							+ assignment.getPublicUuid());
		}
	}

	/**
	 * Coherent kind/scale pairs (ADR-5B.2):
	 * <pre>
	 *   TASK         -> SCORE_0_20 | LITERAL_A_B_C_D
	 *   QUIZ         -> SCORE_0_20 | LITERAL_A_B_C_D
	 *   EXAM         -> SCORE_0_20
	 *   RUBRIC       -> LITERAL_AD | LITERAL_NA | LITERAL_A_B_C_D
	 *   COMPETENCY   -> LITERAL_NA
	 * </pre>
	 */
	private static void assertKindScaleCoherent(EvaluationKind kind, EvaluationScale scale) {
		if (kind == null || scale == null) return;
		boolean ok = switch (kind) {
			case TASK, QUIZ     -> scale == EvaluationScale.SCORE_0_20
					|| scale == EvaluationScale.LITERAL_A_B_C_D;
			case EXAM            -> scale == EvaluationScale.SCORE_0_20;
			case RUBRIC          -> scale == EvaluationScale.LITERAL_AD
					|| scale == EvaluationScale.LITERAL_NA
					|| scale == EvaluationScale.LITERAL_A_B_C_D;
			case COMPETENCY      -> scale == EvaluationScale.LITERAL_NA;
		};
		if (!ok) {
			throw new BadRequestException(
					EvaluationErrorCodes.EVAL_KIND_SCALE_MISMATCH,
					"Kind " + kind + " is not compatible with scale " + scale);
		}
	}

	private static void validateDateWindow(LocalDate scheduled, LocalDate due) {
		if (scheduled != null && due != null && due.isBefore(scheduled)) {
			throw new BadRequestException(
					EvaluationErrorCodes.EVAL_DATE_INVERTED,
					"Evaluation dueDate (" + due + ") must be on or after "
							+ "scheduledDate (" + scheduled + ")");
		}
	}

	private void ensureNameAvailable(TeacherAssignment assignment, String name,
			UUID excludeInternalId) {
		if (name == null) return;
		String normalised = name.trim();
		evaluationRepository
				.findByAssignmentAndNameIgnoreCase(assignment, normalised)
				.filter(existing -> !existing.getId().equals(excludeInternalId))
				.ifPresent(conflict -> {
					throw new ConflictException(
							EvaluationErrorCodes.EVAL_NAME_EXISTS,
							"Another evaluation in this assignment already "
									+ "uses the name '" + normalised + "'");
				});
	}

	private static boolean hasNonPublishedEditableField(UpdateEvaluationRequest patch) {
		return patch.kind() != null
				|| patch.name() != null
				|| patch.weight() != null
				|| patch.scheduledDate() != null
				|| patch.scale() != null
				|| patch.unitPublicUuid() != null
				|| patch.learningSessionPublicUuid() != null;
	}

	private static UUID parseUuid(String value) {
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("INVALID_UUID",
					"Invalid UUID: " + value);
		}
	}

	private static void ensureTransition(Evaluation evaluation, EvaluationStatus target) {
		Set<EvaluationStatus> legal = evaluation.getStatus().legalNext();
		if (!legal.contains(target)) {
			throw new ConflictException(
					EvaluationErrorCodes.EVAL_ILLEGAL_TRANSITION,
					"Cannot transition evaluation " + evaluation.getPublicUuid()
							+ " from " + evaluation.getStatus() + " to " + target
							+ "; legal next states: " + legal);
		}
	}
}
