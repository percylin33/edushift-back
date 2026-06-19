package com.edushift.modules.quizzes.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.evaluations.entity.Evaluation;
import com.edushift.modules.evaluations.entity.EvaluationKind;
import com.edushift.modules.evaluations.entity.EvaluationScale;
import com.edushift.modules.evaluations.entity.EvaluationStatus;
import com.edushift.modules.evaluations.graderecord.entity.GradeRecord;
import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.entity.Rubric;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.quizzes.dto.GradeWithRubricRequest;
import com.edushift.modules.quizzes.dto.GradeWithRubricRequest.CriterionLevelPick;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.RubricNotFoundException;
import com.edushift.modules.quizzes.mapper.QuizMapper;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizRubricService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.teachers.assignments.entity.TeacherAssignment;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.entity.Teacher;
import com.edushift.modules.teachers.repository.TeacherRepository;
import com.edushift.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link QuizRubricService} implementation
 * (Sprint 7b / BE-7b.3).
 *
 * <h3>Design</h3>
 * The rubric is attached to the quiz header
 * ({@code lms_quizzes.rubric_id}) and a derived
 * {@code edushift.evaluations} row (kind=QUIZ, scale=
 * LITERAL_A_B_C_D) is lazily created to anchor the
 * {@code grade_records} entries. The derived evaluation reuses
 * the quiz owner's first active {@link TeacherAssignment} on the
 * same {@link Section} (BE-7b.3 decision A1).
 *
 * <h3>Multi-tenant safety</h3>
 * All reads &amp; writes go through {@code @TenantId}-filtered
 * repository methods. Cross-tenant access resolves as 404
 * (anti-enumeration).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizRubricServiceImpl implements QuizRubricService {

	/** Prefix on the derived evaluation's name so the grade book UI
	 * can render it unambiguously. */
	private static final String DERIVED_EVAL_NAME_PREFIX = "[QuizRubric] ";

	private final QuizRepository quizRepository;
	private final QuizAttemptRepository attemptRepository;
	private final QuizMapper quizMapper;
	private final RubricRepository rubricRepository;
	private final EvaluationRepository evaluationRepository;
	private final GradeRecordRepository gradeRecordRepository;
	private final TeacherRepository teacherRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final StudentRepository studentRepository;
	private final UserRepository userRepository;

	// ------------------------------------------------------------------
	// attach / detach
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public QuizResponse attachRubric(UUID quizPublicUuid, UUID rubricPublicUuid) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		Rubric rubric = rubricRepository.findByPublicUuid(rubricPublicUuid)
				.orElseThrow(() -> new RubricNotFoundException(rubricPublicUuid.toString()));

		// Idempotency: re-attaching the same rubric returns the
		// current response without recreating the derived evaluation.
		if (quiz.getRubric() != null
				&& rubric.getId().equals(quiz.getRubric().getId())) {
			log.debug("Quiz {} already carries rubric {} — no-op",
					quizPublicUuid, rubricPublicUuid);
			return quizMapper.toResponse(quiz, /* revealCorrectness */ true);
		}

		// Lazily create the derived evaluation on first attach.
		Evaluation derived = (quiz.getRubricEvaluation() != null)
				? quiz.getRubricEvaluation()
				: createDerivedEvaluation(quiz);
		quiz.setRubric(rubric);
		quiz.setRubricEvaluation(derived);
		Quiz saved = quizRepository.save(quiz);
		log.info("Quiz {} attached rubric {} via derived evaluation {}",
				quizPublicUuid, rubricPublicUuid, derived.getPublicUuid());
		return quizMapper.toResponse(saved, /* revealCorrectness */ true);
	}

	@Override
	@Transactional
	public QuizResponse detachRubric(UUID quizPublicUuid) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		if (quiz.getRubric() == null && quiz.getRubricEvaluation() == null) {
			// Nothing to detach; treat as no-op for idempotency.
			return quizMapper.toResponse(quiz, /* revealCorrectness */ true);
		}
		Evaluation derived = quiz.getRubricEvaluation();
		quiz.setRubric(null);
		quiz.setRubricEvaluation(null);
		Quiz saved = quizRepository.save(quiz);

		// Soft-delete the derived evaluation if and only if it has
		// no grades yet (otherwise the grade book keeps the row for
		// historical transcript integrity).
		if (derived != null
				&& !gradeRecordRepository.existsByEvaluation(derived)) {
			derived.markDeleted();
			evaluationRepository.save(derived);
			log.info("Quiz {} detached rubric; soft-deleted empty derived eval {}",
					quizPublicUuid, derived.getPublicUuid());
		} else if (derived != null) {
			log.info("Quiz {} detached rubric; kept derived eval {} "
					+ "(has grades — historical integrity)",
					quizPublicUuid, derived.getPublicUuid());
		}
		return quizMapper.toResponse(saved, /* revealCorrectness */ true);
	}

	// ------------------------------------------------------------------
	// gradeWithRubric
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public QuizResponse gradeWithRubric(UUID attemptPublicUuid,
			GradeWithRubricRequest request, UUID graderUserId) {
		QuizAttempt attempt = attemptRepository.findByPublicUuid(attemptPublicUuid)
				.orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND",
						"Attempt " + attemptPublicUuid + " not found"));
		// Re-fetch the quiz in the current persistence context so
		// we see the latest rubric link (the attempt was created
		// earlier and its in-memory quiz ref is stale).
		Quiz quiz = quizRepository.findByPublicUuid(attempt.getQuiz().getPublicUuid())
				.orElseThrow(() -> new QuizNotFoundException(
						attempt.getQuiz().getPublicUuid().toString()));
		if (quiz.getRubric() == null || quiz.getRubricEvaluation() == null) {
			throw new com.edushift.shared.exception.BadRequestException(
					"QUIZ_HAS_NO_RUBRIC",
					"Quiz " + quiz.getPublicUuid() + " has no rubric attached; "
							+ "attach one first (PATCH /quizzes/{uuid}/rubric).");
		}
		if (quiz.getRubricEvaluation().getStatus() == EvaluationStatus.CLOSED) {
			throw new com.edushift.shared.exception.BadRequestException(
					"EVAL_CLOSED",
					"Derived rubric evaluation is CLOSED — cannot accept new grades.");
		}

		// Resolve the student entity. Note that
		// {@code attempt.studentUserId} carries the
		// {@code users.public_uuid} (the JWT-bearer id, see
		// BE-5B.3 / V29 hotfix), but {@code students.user_id}
		// FK-references {@code users.id} (the internal PK, see
		// V10). The service therefore dereferences
		// {@code public_uuid → id → user_id} in two steps.
		UUID ownerInternalId = userRepository.findByPublicUuid(attempt.getStudentUserId())
				.map(User::getId)
				.orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND",
						"User " + attempt.getStudentUserId() + " not found "
								+ "for attempt " + attemptPublicUuid));
		Student student = studentRepository.findByUserId(ownerInternalId)
				.orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND",
						"Student for attempt " + attemptPublicUuid
								+ " not found (userId=" + attempt.getStudentUserId() + ")"));

		// Translate (criterionKey, levelCode) picks to a single
		// qualitative literal — the best level chosen across the
		// rubric's criteria, ranked by canonical MINEDU order.
		String literal = computeOverallLiteral(quiz.getRubric(), request.picks());

		// UPSERT semantics on (evaluation, student) — same shape as
		// the standard GradeRecordService flow.
		GradeRecord record = gradeRecordRepository
				.findByEvaluationAndStudent(quiz.getRubricEvaluation(), student)
				.orElseGet(() -> {
					GradeRecord g = new GradeRecord();
					g.setEvaluation(quiz.getRubricEvaluation());
					g.setStudent(student);
					return g;
				});
		record.setLiteral(literal);
		record.setComments(request.comments());
		record.setRecordedAt(Instant.now());
		record.setRecordedByUserId(graderUserId);
		gradeRecordRepository.save(record);

		log.info("Quiz attempt {} graded-with-rubric by {}: literal={}",
				attemptPublicUuid, graderUserId, literal);

		// Re-read the quiz to return a fresh response (the rubric
		// link is unchanged; we re-project to keep the mapper's
		// revealCorrectness semantics consistent with the rest of
		// the service).
		Quiz refreshed = quizRepository.findByPublicUuid(quiz.getPublicUuid())
				.orElseThrow(() -> new QuizNotFoundException(quiz.getPublicUuid().toString()));
		return quizMapper.toResponse(refreshed, /* revealCorrectness */ true);
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Quiz requireQuiz(UUID quizPublicUuid) {
		return quizRepository.findByPublicUuid(quizPublicUuid)
				.orElseThrow(() -> new QuizNotFoundException(quizPublicUuid.toString()));
	}

	/**
	 * Creates a derived {@code Evaluation} row anchored to the
	 * first active {@link TeacherAssignment} of the quiz owner on
	 * the quiz's section (BE-7b.3 decision A1).
	 *
	 * <p>If the owner has no active assignment in the section the
	 * service throws a {@code TEACHER_NOT_ASSIGNED_TO_SECTION}
	 * (409) — surfacing the upstream problem instead of silently
	 * picking an unrelated assignment.</p>
	 *
	 * <h3>Identity note</h3>
	 * {@code quiz.ownerUserId} carries the owner's
	 * {@code users.public_uuid} (as set by
	 * {@code currentUserProvider.currentUserId()}, which returns the
	 * JWT-public UUID, see BE-5B.3 / V29 hotfix). The
	 * {@code teachers.user_id} column, however, FK-references
	 * {@code users.id} (the internal UUIDv7 PK, see V18). The
	 * service therefore performs a two-step lookup:
	 * {@code users.public_uuid → users.id → teachers.user_id}.
	 */
	private Evaluation createDerivedEvaluation(Quiz quiz) {
		Section section = quiz.getSection();

		// Step 1 — find the user by public UUID.
		UUID ownerInternalId = userRepository.findByPublicUuid(quiz.getOwnerUserId())
				.map(User::getId)
				.orElseThrow(() -> new com.edushift.shared.exception.BadRequestException(
						"TEACHER_NOT_ASSIGNED_TO_SECTION",
						"Quiz owner " + quiz.getOwnerUserId() + " does not exist; "
								+ "cannot derive a rubric evaluation."));

		// Step 2 — find the teacher linked to that user.
		Teacher teacher = teacherRepository.findByUserId(ownerInternalId)
				.orElseThrow(() -> new com.edushift.shared.exception.BadRequestException(
						"TEACHER_NOT_ASSIGNED_TO_SECTION",
						"Quiz owner " + quiz.getOwnerUserId() + " is not a teacher; "
								+ "cannot derive a rubric evaluation."));

		List<TeacherAssignment> assignments = teacherAssignmentRepository
				.findAllBySectionActive(section, null);
		TeacherAssignment assignment = assignments.stream()
				.filter(a -> teacher.getId().equals(a.getTeacher().getId()))
				.findFirst()
				.orElseThrow(() -> new com.edushift.shared.exception.BadRequestException(
						"TEACHER_NOT_ASSIGNED_TO_SECTION",
						"Teacher " + teacher.getPublicUuid()
								+ " has no active assignment in section "
								+ section.getPublicUuid() + "; cannot derive a rubric evaluation."));

		Evaluation evaluation = new Evaluation();
		evaluation.setTeacherAssignment(assignment);
		evaluation.setKind(EvaluationKind.QUIZ);
		evaluation.setScale(EvaluationScale.LITERAL_A_B_C_D);
		evaluation.setName(DERIVED_EVAL_NAME_PREFIX + quiz.getTitle());
		evaluation.setDescription("Derived evaluation for rubric attached to quiz "
				+ quiz.getPublicUuid());
		evaluation.setWeight(new BigDecimal("1.00"));
		evaluation.setScheduledDate(LocalDate.now());
		evaluation.setIsActive(Boolean.TRUE);
		// Lifecycle: skip DRAFT — the derived evaluation is published
		// immediately so the grading queue can write to it.
		evaluation.setStatus(EvaluationStatus.PUBLISHED);
		evaluation.setPublishedAt(Instant.now());
		return evaluationRepository.save(evaluation);
	}

	/**
	 * Reduces the per-criterion level picks to a single literal
	 * accepted by {@code grade_records.literal}'s CHECK constraint
	 * (V27 allows only {@code AD | A | B | C | D | NA}).
	 *
	 * <p>The mapping follows MINEDU's conservative "criterio
	 * mínimo alcanzado" rule: the <em>worst</em> chosen level
	 * determines the literal, so a single failed criterion pulls
	 * the whole row down.
	 *
	 * <table>
	 *   <tr><th>Worst chosen level</th><th>GradeRecord.literal</th></tr>
	 *   <tr><td>SOBRESALIENTE</td><td>AD</td></tr>
	 *   <tr><td>ESPERADO</td><td>A</td></tr>
	 *   <tr><td>EN_PROCESO</td><td>B</td></tr>
	 *   <tr><td>EN_INICIO</td><td>C</td></tr>
	 * </table>
	 *
	 * <p>If any level code is unknown the service throws
	 * {@code RUBRIC_LEVEL_INVALID} (400).
	 */
	private static String computeOverallLiteral(
			Rubric rubric, List<CriterionLevelPick> picks) {
		List<String> ranking = List.of("SOBRESALIENTE", "ESPERADO",
				"EN_PROCESO", "EN_INICIO");
		Map<String, String> levelToLiteral = Map.of(
				"SOBRESALIENTE", "AD",
				"ESPERADO", "A",
				"EN_PROCESO", "B",
				"EN_INICIO", "C");
		Set<String> validCodes = new HashSet<>(ranking);
		String worstSeen = "SOBRESALIENTE";
		int worstRank = -1;
		for (CriterionLevelPick pick : picks) {
			String code = pick.levelCode() == null
					? null
					: pick.levelCode().toUpperCase();
			if (code == null || !validCodes.contains(code)) {
				throw new com.edushift.shared.exception.BadRequestException(
						"RUBRIC_LEVEL_INVALID",
						"Level code " + pick.levelCode()
								+ " is not one of the rubric's levels.");
			}
			int rank = ranking.indexOf(code);
			if (rank > worstRank) {
				worstRank = rank;
				worstSeen = code;
			}
		}
		return levelToLiteral.get(worstSeen);
	}
}
