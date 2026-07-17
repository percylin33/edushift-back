package com.edushift.modules.quizzes.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.quizzes.dto.AddOptionRequest;
import com.edushift.modules.quizzes.dto.CreateOptionRequest;
import com.edushift.modules.quizzes.dto.CreateQuestionRequest;
import com.edushift.modules.quizzes.dto.CreateQuizRequest;
import com.edushift.modules.quizzes.dto.GradeAnswerRequest;
import com.edushift.modules.quizzes.dto.QuestionResponse;
import com.edushift.modules.quizzes.dto.QuizResponse;
import com.edushift.modules.quizzes.dto.QuizSummary;
import com.edushift.modules.quizzes.dto.UpdateQuizRequest;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.exception.InvalidQuizStateException;
import com.edushift.modules.quizzes.exception.QuestionNotFoundException;
import com.edushift.modules.quizzes.exception.QuestionValidationException;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.RecordEmptyPatchException;
import com.edushift.modules.quizzes.exception.SectionNotFoundException;
import com.edushift.modules.quizzes.mapper.QuizMapper;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.quizzes.service.QuizService;
import com.edushift.modules.students.enrollments.entity.StudentEnrollment;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link QuizService} implementation
 * (Sprint 7b / BE-7b.1).
 *
 * <h3>Multi-tenant safety</h3>
 * All reads &amp; writes go through {@code @TenantId}-filtered
 * repository methods; cross-tenant lookups resolve as 404
 * (anti-enumeration). The service never trusts a client-supplied
 * {@code tenant_id}; the row carries it.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   DRAFT ──publish──▶ PUBLISHED ──close──▶ CLOSED
 *     │                     │
 *     │                     └── soft-delete (admin only)
 *     └── soft-delete (owner / admin)
 * </pre>
 *
 * <h3>Child collection access</h3>
 * The {@link Quiz} / {@link QuizQuestion} entities do <em>not</em>
 * expose JPA {@code @OneToMany} collections. Child rows are
 * resolved on demand through the dedicated repositories
 * (see {@link com.edushift.modules.quizzes.repository}).
 * This keeps the entity graph minimal and matches the
 * design decision made in BE-7b.0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizOptionRepository optionRepository;
	private final SectionRepository sectionRepository;
	private final QuizMapper quizMapper;
	private final QuizAttemptService attemptService;
	private final ApplicationEventPublisher eventPublisher; // Sprint 9 / BE-9.3
	private final StudentEnrollmentRepository studentEnrollmentRepository; // Sprint 9 / BE-9.3

	// ------------------------------------------------------------------
	// Builder
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public QuizResponse create(UUID sectionPublicUuid,
			CreateQuizRequest request, UUID ownerUserId) {
		Section section = requireSection(sectionPublicUuid);
		validateDueAt(request.dueAt());

		Quiz entity = new Quiz();
		entity.setSection(section);
		entity.setTitle(request.title());
		entity.setDescription(request.description());
		entity.setDueAt(request.dueAt());
		entity.setTimeLimitMinutes(request.timeLimitMinutes() == null
				? null : request.timeLimitMinutes().shortValue());
		entity.setAttemptsAllowed(request.maxAttempts().shortValue());
		entity.setMaxScore(request.maxScore().shortValue());
		entity.setOwnerUserId(ownerUserId);
		// status defaults to DRAFT in @PrePersist.
		Quiz saved = quizRepository.save(entity);

		// Optionally bulk-create questions on the same call.
		if (request.questions() != null) {
			for (CreateQuestionRequest q : request.questions()) {
				persistQuestion(saved, q);
			}
		}

		log.info("Quiz created publicUuid={} sectionPublicUuid={} ownerUserId={}",
				saved.getPublicUuid(), sectionPublicUuid, ownerUserId);
		return quizMapper.toResponse(saved, /* revealCorrectness */ true);
	}

	@Override
	@Transactional
	public QuizResponse patch(UUID quizPublicUuid, UpdateQuizRequest request) {
		if (isAllNull(request)) {
			throw new RecordEmptyPatchException();
		}
		Quiz entity = requireQuiz(quizPublicUuid);

		// Once PUBLISHED, only dueAt and maxAttempts are editable.
		boolean restricted = entity.getStatus() != QuizStatus.DRAFT;

		if (request.title() != null) {
			requireDraft(entity);
			entity.setTitle(request.title());
		}
		if (request.description() != null) {
			requireDraft(entity);
			entity.setDescription(request.description());
		}
		if (request.dueAt() != null) {
			validateDueAt(request.dueAt());
			entity.setDueAt(request.dueAt());
		}
		if (request.timeLimitMinutes() != null) {
			requireDraft(entity);
			entity.setTimeLimitMinutes(request.timeLimitMinutes().shortValue());
		}
		if (request.maxAttempts() != null) {
			entity.setAttemptsAllowed(request.maxAttempts().shortValue());
		}
		if (request.maxScore() != null) {
			requireDraft(entity);
			entity.setMaxScore(request.maxScore().shortValue());
		}
		if (restricted) {
			log.debug("Quiz {} patched while PUBLISHED (limited fields)",
					quizPublicUuid);
		}

		Quiz saved = quizRepository.save(entity);
		return quizMapper.toResponse(saved, /* revealCorrectness */ true);
	}

	@Override
	@Transactional
	public QuestionResponse addQuestion(UUID quizPublicUuid,
			CreateQuestionRequest request) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		requireDraft(quiz);
		validateQuestion(request);

		QuizQuestion persisted = persistQuestion(quiz, request);
		return quizMapper.toQuestionResponse(persisted);
	}

	@Override
	@Transactional
	public QuestionResponse addOption(UUID questionPublicUuid,
			AddOptionRequest request) {
		QuizQuestion question = requireQuestion(questionPublicUuid);
		requireDraft(question.getQuiz());
		if (question.getQuestionType() != QuestionType.MC) {
			throw QuestionValidationException.tfHasOptions();
		}
		CreateOptionRequest o = request.option();
		if (o == null || o.label() == null || o.label().isBlank()) {
			throw QuestionValidationException.blankPrompt();
		}
		if (o.isCorrect() == null) {
			throw QuestionValidationException.mcNeedsExactlyOneCorrect(0);
		}

		QuizOption entity = new QuizOption();
		entity.setQuestion(question);
		entity.setLabel(o.label());
		entity.setCorrect(Boolean.TRUE.equals(o.isCorrect()));
		entity.setPosition((short) (optionRepository
				.findAllByQuestionOrderByPositionAsc(question).size() + 1));
		optionRepository.save(entity);

		// Re-validate the "exactly one correct" invariant on the
		// full option set so we don't violate the DB trigger.
		revalidateMcCorrectCount(question);

		return quizMapper.toQuestionResponse(question);
	}

	@Override
	@Transactional
	public QuizResponse publish(UUID quizPublicUuid) {
		Quiz entity = requireQuiz(quizPublicUuid);
		if (entity.getStatus() != QuizStatus.DRAFT) {
			throw InvalidQuizStateException.notDraft(entity.getStatus().name());
		}
		long count = questionRepository.countByQuiz(entity);
		if (count == 0) {
			throw InvalidQuizStateException.noQuestions();
		}
		entity.setStatus(QuizStatus.PUBLISHED);
		entity.setPublishedAt(Instant.now());
		Quiz saved = quizRepository.save(entity);
		log.info("Quiz published publicUuid={}", quizPublicUuid);

		// Sprint 9 / BE-9.3 — fire QUIZ_PUBLISHED to all enrolled students.
		Section section = saved.getSection();
		List<StudentEnrollment> enrolled = studentEnrollmentRepository.findActiveBySection(section);
		if (!enrolled.isEmpty()) {
			String dueDate = saved.getDueAt() == null ? "" : saved.getDueAt().toString();
			List<com.edushift.modules.notifications.event.NotificationEvent.Recipient> recipients = enrolled.stream()
					.map(e -> e.getStudent().getUserId())
					.filter(Objects::nonNull)
					.map(uid -> new com.edushift.modules.notifications.event.NotificationEvent.Recipient(uid, null))
					.toList();
			recipients.forEach(r -> eventPublisher.publishEvent(
					com.edushift.modules.notifications.event.NotificationEvent.builder()
							.templateKey("QUIZ_PUBLISHED")
							.category(com.edushift.modules.notifications.entity.Notification.Category.QUIZ)
							.sourceId(saved.getPublicUuid())
							.recipients(java.util.List.of(r))
							.payload(java.util.Map.of(
									"studentName", "",
									"quizTitle", saved.getTitle(),
									"courseName", section.getName(),
									"dueDate", dueDate))
							.build()));
		}

		return quizMapper.toResponse(saved, true);
	}

	@Override
	@Transactional
	public QuizResponse close(UUID quizPublicUuid) {
		Quiz entity = requireQuiz(quizPublicUuid);
		if (entity.getStatus() == QuizStatus.CLOSED) {
			throw InvalidQuizStateException.alreadyClosed();
		}
		if (entity.getStatus() == QuizStatus.DRAFT) {
			throw InvalidQuizStateException.notPublished(entity.getStatus().name());
		}
		entity.setStatus(QuizStatus.CLOSED);
		entity.setClosedAt(Instant.now());
		Quiz saved = quizRepository.save(entity);
		log.info("Quiz closed publicUuid={}", quizPublicUuid);
		return quizMapper.toResponse(saved, true);
	}

	@Override
	@Transactional
	public void delete(UUID quizPublicUuid) {
		Quiz entity = requireQuiz(quizPublicUuid);
		quizRepository.delete(entity);
		log.info("Quiz soft-deleted publicUuid={}", quizPublicUuid);
		// (D-QUIZ-01) attempts are NOT removed; they remain
		// orphaned, same orphan pattern as tasks.
	}

	// ------------------------------------------------------------------
	// Reader
	// ------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public QuizResponse getByPublicUuid(UUID quizPublicUuid) {
		Quiz entity = requireQuiz(quizPublicUuid);
		// Builders/graders see the correct answers; takers (in 7b.2)
		// get a different service method that strips them. Here we
		// always reveal, gated by the controller's @PreAuthorize.
		return quizMapper.toResponse(entity, true);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<QuizSummary> listBySection(UUID sectionPublicUuid,
			Pageable pageable) {
		Section section = requireSection(sectionPublicUuid);
		return quizRepository
				.findAllBySectionOrderByDueAtDesc(section, pageable)
				.map(quizMapper::toSummary);
	}

	// ------------------------------------------------------------------
	// Grading (LMS_QUIZ_GRADE)
	// ------------------------------------------------------------------

	/**
	 * Manual override on a single answer's points. The auto-grader
	 * fills {@code points_awarded} and {@code is_correct} on submit
	 * (BE-7b.2); this method lets a teacher fix or partially credit
	 * an answer. The implementation is delegated to
	 * {@link QuizAttemptService#overrideAnswerGrade}, which is the
	 * single source of truth for the override workflow (closes
	 * DEBT-BE-7B-5).
	 */
	@Override
	@Transactional
	public QuizResponse gradeAnswer(UUID quizPublicUuid, UUID attemptPublicUuid,
			UUID answerPublicUuid, GradeAnswerRequest request) {
		// Map the BE-7b.1 request shape to the BE-7b.2 record
		// (the body has only {@code pointsAwarded}, the path has
		// the answer UUID).
		com.edushift.modules.quizzes.dto.ManualGradeAnswerRequest mapped =
				new com.edushift.modules.quizzes.dto.ManualGradeAnswerRequest(
						answerPublicUuid, request.pointsAwarded());
		// Resolve the grader id from the security context. The
		// @PreAuthorize on the controller already gated by
		// LMS_QUIZ_GRADE; this lookup is just for the
		// {@code graded_by_user_id} audit column.
		UUID grader = resolveGraderId();
		// Defer to the attempts service: it persists the points,
		// stamps the grader, and re-counts the attempt scores.
		attemptService.overrideAnswerGrade(quizPublicUuid, attemptPublicUuid,
				answerPublicUuid, mapped, grader);
		// Return the (re-fetched) parent quiz so the FE can refresh
		// the whole quiz view in one round-trip (preserved
		// behaviour from the BE-7b.1 stub).
		Quiz quiz = requireQuiz(quizPublicUuid);
		log.debug("Override applied: quiz publicUuid={} attempt={} answer={}",
				quizPublicUuid, attemptPublicUuid, answerPublicUuid);
		return quizMapper.toResponse(quiz, true);
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Quiz requireQuiz(UUID publicUuid) {
		return quizRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new QuizNotFoundException(publicUuid.toString()));
	}

	/**
	 * Resolves the current authenticated principal's user id from
	 * Spring Security's context. Used by
	 * {@link #gradeAnswer} to populate the
	 * {@code graded_by_user_id} audit column on the overridden
	 * answer row. Returns {@code null} when there is no
	 * authenticated user (defensive — the controller is
	 * {@code @PreAuthorize}-gated so this is effectively
	 * unreachable in production).
	 */
	private static UUID resolveGraderId() {
		org.springframework.security.core.Authentication auth =
				org.springframework.security.core.context.SecurityContextHolder
						.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return null;
		}
		Object principal = auth.getPrincipal();
		if (principal instanceof com.edushift.infrastructure.security.AuthenticatedPrincipal ap) {
			return ap.getId();
		}
		if (principal instanceof String s) {
			try {
				return UUID.fromString(s);
			}
			catch (IllegalArgumentException ignored) {
				return null;
			}
		}
		return null;
	}

	private QuizQuestion requireQuestion(UUID publicUuid) {
		return questionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new QuestionNotFoundException(publicUuid.toString()));
	}

	private Section requireSection(UUID publicUuid) {
		return sectionRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new SectionNotFoundException(publicUuid.toString()));
	}

	private void requireDraft(Quiz quiz) {
		if (quiz.getStatus() != QuizStatus.DRAFT) {
			throw InvalidQuizStateException.notDraft(quiz.getStatus().name());
		}
	}

	private static void validateDueAt(Instant dueAt) {
		if (dueAt != null && dueAt.isBefore(Instant.now())) {
			throw new com.edushift.shared.exception.BadRequestException(
					"QUIZ_PAST_DUE",
					"dueAt must be in the future (got: " + dueAt + ").");
		}
	}

	private static boolean isAllNull(UpdateQuizRequest r) {
		return r.title() == null
				&& r.description() == null
				&& r.dueAt() == null
				&& r.timeLimitMinutes() == null
				&& r.maxAttempts() == null
				&& r.maxScore() == null;
	}

	// ----- question / option builders -----

	/**
	 * Validates the question payload against DB-level invariants
	 * that the application is the better place to enforce (the DB
	 * still backs them up).
	 */
	private static void validateQuestion(CreateQuestionRequest q) {
		if (q.prompt() == null || q.prompt().isBlank()) {
			throw QuestionValidationException.blankPrompt();
		}
		if (q.points() != null && (q.points() < 1 || q.points() > 100)) {
			throw QuestionValidationException.pointsOutOfRange(q.points());
		}
		switch (q.type()) {
			case MC:
				int n = q.options() == null ? 0 : q.options().size();
				if (n < 2 || n > 6) {
					throw QuestionValidationException.mcNeeds2To6Options(n);
				}
				int correct = 0;
				for (CreateOptionRequest o : q.options()) {
					if (Boolean.TRUE.equals(o.isCorrect())) {
						correct++;
					}
					if (o.label() == null || o.label().isBlank()) {
						throw QuestionValidationException.blankPrompt();
					}
				}
				if (correct != 1) {
					throw QuestionValidationException.mcNeedsExactlyOneCorrect(correct);
				}
				if (q.correctBoolean() != null) {
					throw QuestionValidationException.questionTypeIncompatible();
				}
				break;
			case TF:
				if (q.options() != null && !q.options().isEmpty()) {
					throw QuestionValidationException.tfHasOptions();
				}
				if (q.correctBoolean() == null) {
					throw new com.edushift.shared.exception.BadRequestException(
							"INCONSISTENT_PAYLOAD",
							"TF questions require correctBoolean.");
				}
				if (q.expectedKeywords() != null && !q.expectedKeywords().isBlank()) {
					throw QuestionValidationException.shortAnswerHasOptions();
				}
				break;
			case SHORT_ANSWER:
				if (q.options() != null && !q.options().isEmpty()) {
					throw QuestionValidationException.shortAnswerHasOptions();
				}
				if (q.correctBoolean() != null) {
					throw QuestionValidationException.questionTypeIncompatible();
				}
				break;
		}
	}

	private QuizQuestion persistQuestion(Quiz quiz, CreateQuestionRequest q) {
		QuizQuestion entity = new QuizQuestion();
		entity.setQuiz(quiz);
		entity.setQuestionType(q.type());
		entity.setPrompt(q.prompt());
		entity.setPoints(q.points() == null ? 1 : q.points().shortValue());
		entity.setPosition(q.position() == null
				? nextPosition(quiz)
				: q.position().shortValue());
		entity.setCorrectBoolean(q.type() == QuestionType.TF
				? q.correctBoolean() : null);
		entity.setExpectedKeywords(q.type() == QuestionType.SHORT_ANSWER
				? parseKeywords(q.expectedKeywords())
				: null);

		QuizQuestion saved = questionRepository.save(entity);

		if (q.type() == QuestionType.MC && q.options() != null) {
			short pos = 1;
			List<QuizOption> attached = new ArrayList<>();
			for (CreateOptionRequest o : q.options()) {
				QuizOption opt = new QuizOption();
				opt.setQuestion(saved);
				opt.setLabel(o.label());
				opt.setCorrect(Boolean.TRUE.equals(o.isCorrect()));
				opt.setPosition(pos++);
				attached.add(opt);
			}
			optionRepository.saveAll(attached);
		}
		return saved;
	}

	private short nextPosition(Quiz quiz) {
		long count = questionRepository.countByQuiz(quiz);
		return (short) (count + 1);
	}

	private static String[] parseKeywords(String csv) {
		if (csv == null || csv.isBlank()) {
			return null;
		}
		String[] parts = csv.split(",");
		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}
		return parts;
	}

	private void revalidateMcCorrectCount(QuizQuestion question) {
		long correct = optionRepository.countByQuestionAndCorrectIsTrue(question);
		if (correct != 1) {
			throw QuestionValidationException.mcNeedsExactlyOneCorrect((int) correct);
		}
	}
}
