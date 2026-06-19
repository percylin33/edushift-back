package com.edushift.modules.quizzes.service.impl;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.quizzes.dto.AnswerInput;
import com.edushift.modules.quizzes.dto.AttemptResponse;
import com.edushift.modules.quizzes.dto.AttemptSummary;
import com.edushift.modules.quizzes.dto.GradingQueueItem;
import com.edushift.modules.quizzes.dto.ManualGradeAnswerRequest;
import com.edushift.modules.quizzes.dto.ManualGradeAttemptRequest;
import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.exception.AnswerNotFoundException;
import com.edushift.modules.quizzes.exception.AttemptNotFoundException;
import com.edushift.modules.quizzes.exception.AttemptNotInProgressException;
import com.edushift.modules.quizzes.exception.AttemptNotSubmittedException;
import com.edushift.modules.quizzes.exception.AttemptsExhaustedException;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.QuizNotPublishedException;
import com.edushift.modules.quizzes.exception.StudentNotEnrolledException;
import com.edushift.modules.quizzes.grader.QuizAutoGrader;
import com.edushift.modules.quizzes.mapper.QuizAttemptMapper;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link QuizAttemptService} implementation
 * (Sprint 7b / BE-7b.2).
 *
 * <h3>Auto-grading pipeline</h3>
 * <pre>
 *   start  ──▶ IN_PROGRESS ──submit──▶ SUBMITTED ──(auto-grade)──▶
 *   SUBMITTED ──(recount)──▶ AUTO_GRADED ──(gradeAttempt)──▶ GRADED
 * </pre>
 * The submit handler runs {@link QuizAutoGrader} on every answer:
 * <ul>
 *   <li>MC / TF: deterministic, auto-grade is final.</li>
 *   <li>SHORT_ANSWER: a seed {@code points_awarded} is set (full
 *       points if all keywords match, else 0). The teacher can
 *       override per-answer via the grading queue (BE-7b.2).</li>
 * </ul>
 * After submit, if <em>all</em> answers have a non-null
 * {@code points_awarded} the attempt is set to {@code GRADED}
 * directly; otherwise it stays at {@code AUTO_GRADED} (SHORT_ANSWER
 * pending).
 *
 * <h3>Multi-tenant safety</h3>
 * All reads &amp; writes go through {@code @TenantId}-filtered
 * repository methods. Cross-tenant access resolves as 404
 * (anti-enumeration). The service never trusts a client-supplied
 * {@code tenant_id}; the row carries it.
 *
 * <h3>Student access</h3>
 * The taker side enforces two things on every call: the
 * authenticated principal must equal the attempt's
 * {@code studentUserId}, AND that user must be ACTIVE-enrolled in
 * the quiz's section (D-QUIZ-04). A parent on-behalf flow uses
 * {@code submitterUserId} distinct from {@code studentUserId}
 * (V35 column).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAttemptServiceImpl implements QuizAttemptService {

	private final QuizRepository quizRepository;
	private final QuizAttemptRepository attemptRepository;
	private final QuizAnswerRepository answerRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizOptionRepository optionRepository;
	private final SectionRepository sectionRepository;
	private final StudentRepository studentRepository;
	private final StudentEnrollmentRepository enrollmentRepository;
	private final QuizAttemptMapper attemptMapper;
	private final CurrentUserProvider currentUserProvider;

	// ------------------------------------------------------------------
	// Taker
	// ------------------------------------------------------------------

	@Override
	@Transactional
	public AttemptResponse startAttempt(UUID quizPublicUuid,
			UUID studentUserId, UUID submitterUserId) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		if (quiz.getStatus() != QuizStatus.PUBLISHED) {
			throw new QuizNotPublishedException(quiz.getStatus().name());
		}
		requireEnrolledStudent(quiz.getSection(), studentUserId);

		long consumed = attemptRepository.countByQuizAndStudentUserId(
				quiz, studentUserId);
		short allowed = quiz.getAttemptsAllowed() == null
				? (short) 1 : quiz.getAttemptsAllowed();
		if (consumed >= allowed) {
			throw new AttemptsExhaustedException(allowed, (int) consumed);
		}

		QuizAttempt attempt = new QuizAttempt();
		attempt.setQuiz(quiz);
		attempt.setStudentUserId(studentUserId);
		attempt.setSubmitterUserId(
				submitterUserId != null ? submitterUserId : studentUserId);
		attempt.setAttemptNumber((short) (consumed + 1));
		attempt.setStatus(AttemptStatus.IN_PROGRESS);
		attempt.setStartedAt(Instant.now());
		if (quiz.getTimeLimitMinutes() != null) {
			attempt.setExpiresAt(Instant.now().plusSeconds(
					quiz.getTimeLimitMinutes().longValue() * 60L));
		}
		QuizAttempt saved = attemptRepository.save(attempt);
		log.info("Quiz attempt started publicUuid={} quiz={} student={} n={}/{}",
				saved.getPublicUuid(), quizPublicUuid, studentUserId,
				saved.getAttemptNumber(), allowed);

		// Taker starts with no answers; revealCorrectness=false
		// until the attempt reaches GRADED.
		return attemptMapper.toResponse(saved, quiz, false, 0);
	}

	@Override
	@Transactional(readOnly = true)
	public AttemptResponse getAttempt(UUID attemptPublicUuid, UUID callerUserId) {
		QuizAttempt attempt = requireAttempt(attemptPublicUuid);
		// Visibility: taker sees their own; a teacher/admin sees
		// any attempt in their section. The simple check is
		// "caller is the student OR caller has any quiz authority
		// beyond SUBMIT" (the controller already gated by
		// LMS_QUIZ_SUBMIT or LMS_QUIZ_GRADE).
		boolean isTaker = callerUserId != null
				&& callerUserId.equals(attempt.getStudentUserId());
		boolean isGrader = hasGraderAuthority();
		if (!isTaker && !isGrader) {
			// Anti-enumeration: surface the same 404 as a missing
			// attempt to a non-taker non-grader caller.
			throw new AttemptNotFoundException(attemptPublicUuid.toString());
		}
		int pending = pendingAnswerCount(attempt);
		boolean reveal = isGrader
				|| attempt.getStatus() == AttemptStatus.GRADED;
		return attemptMapper.toResponse(attempt, attempt.getQuiz(),
				reveal, pending);
	}

	@Override
	@Transactional
	public AttemptResponse saveAnswers(UUID attemptPublicUuid,
			UUID callerUserId, List<AnswerInput> inputs) {
		QuizAttempt attempt = requireAttempt(attemptPublicUuid);
		requireCallerIsStudent(attempt, callerUserId);
		if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
			throw new AttemptNotInProgressException(attempt.getStatus().name());
		}
		if (inputs == null || inputs.isEmpty()) {
			return attemptMapper.toResponse(attempt, attempt.getQuiz(), false,
					pendingAnswerCount(attempt));
		}

		// Resolve all referenced questions in one shot so we can
		// validate type compatibility before touching the DB.
		List<QuizQuestion> questions = questionRepository.findAllByQuizOrderByPositionAsc(
				attempt.getQuiz());
		Map<UUID, QuizQuestion> byPublicUuid = new HashMap<>();
		for (QuizQuestion q : questions) {
			byPublicUuid.put(q.getPublicUuid(), q);
		}

		// Pre-resolve options for MC answers (so we can later
		// auto-grade the in-place rows if the student submits
		// without an explicit "regrade" — currently we only
		// auto-grade on submit, so this is just a sanity check).
		Map<UUID, QuizOption> optionsByPublicUuid = new HashMap<>();
		for (QuizQuestion q : questions) {
			if (q.getQuestionType() != QuestionType.MC) {
				continue;
			}
			for (QuizOption o : optionRepository
					.findAllByQuestionOrderByPositionAsc(q)) {
				optionsByPublicUuid.put(o.getPublicUuid(), o);
			}
		}

		for (AnswerInput in : inputs) {
			QuizQuestion q = byPublicUuid.get(in.questionPublicUuid());
			if (q == null) {
				throw new AnswerNotFoundException(in.questionPublicUuid().toString());
			}
			// Server-side type trust: re-derive the question type
			// from the row, not from the client's
			// {@code questionType} hint.
			validateAnswerShape(q, in);

			// Upsert the row.
			QuizAnswer row = answerRepository
					.findByAttemptAndQuestion(attempt, q)
					.orElseGet(() -> {
						QuizAnswer a = new QuizAnswer();
						a.setAttempt(attempt);
						a.setQuestion(q);
						return a;
					});
			applyAnswerPayload(row, q, in, optionsByPublicUuid);
			answerRepository.save(row);
		}

		return attemptMapper.toResponse(attempt, attempt.getQuiz(), false,
				pendingAnswerCount(attempt));
	}

	@Override
	@Transactional
	public AttemptResponse submitAttempt(UUID attemptPublicUuid,
			UUID callerUserId) {
		QuizAttempt attempt = requireAttempt(attemptPublicUuid);
		requireCallerIsStudent(attempt, callerUserId);
		if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
			throw new AttemptNotInProgressException(attempt.getStatus().name());
		}
		attempt.setStatus(AttemptStatus.SUBMITTED);
		attempt.setSubmittedAt(Instant.now());

		// Auto-grade every persisted answer.
		List<QuizAnswer> answers = answerRepository
				.findAllByAttemptOrderByQuestionPositionAsc(attempt);
		short autoTotal = 0;
		boolean hasShortAnswerPending = false;
		Map<UUID, QuizOption> optionsIndex = indexOptions(attempt);

		for (QuizAnswer ans : answers) {
			QuizQuestion q = ans.getQuestion();
			if (q == null) {
				continue;
			}
			QuizAutoGrader.grade(ans, q, optionsIndex::get);
			answerRepository.save(ans);
			if (Boolean.TRUE.equals(ans.getCorrect())
					&& ans.getPointsAwarded() != null) {
				autoTotal = (short) (autoTotal + ans.getPointsAwarded());
			}
			// SHORT_ANSWER is "pending manual" iff the question
			// is SHORT_ANSWER and the row has no gradedAt yet
			// (the auto-grader seeds pointsAwarded but never
			// stamps gradedAt — that's the teacher's call).
			if (q.getQuestionType() == QuestionType.SHORT_ANSWER
					&& ans.getGradedAt() == null) {
				hasShortAnswerPending = true;
			}
		}

		attempt.setAutoScore(autoTotal);
		attempt.setScore(autoTotal);
		if (hasShortAnswerPending) {
			// MC + TF are final; SHORT_ANSWER stays open for the
			// teacher's manual verdict. The grading queue shows
			// up in the FE-7b.3 results panel.
			attempt.setStatus(AttemptStatus.AUTO_GRADED);
		} else {
			// Either no SHORT_ANSWER at all, or every SHORT_ANSWER
			// was auto-graded to a "graded at" stamp (BE-7b.2
			// does NOT stamp gradedAt for SHORT_ANSWER — the
			// teacher still owns the final verdict via the queue).
			attempt.setStatus(AttemptStatus.GRADED);
			attempt.setGradedAt(Instant.now());
			attempt.setGradedByUserId(null); // system
		}
		QuizAttempt saved = attemptRepository.save(attempt);
		log.info("Quiz attempt submitted publicUuid={} status={} autoScore={}",
				saved.getPublicUuid(), saved.getStatus(), autoTotal);

		// Caller (student) sees their own attempt; reveal only if
		// already fully graded.
		boolean reveal = saved.getStatus() == AttemptStatus.GRADED;
		return attemptMapper.toResponse(saved, attempt.getQuiz(), reveal,
				pendingAnswerCount(saved));
	}

	// ------------------------------------------------------------------
	// Teacher
	// ------------------------------------------------------------------

	@Override
	@Transactional(readOnly = true)
	public Page<AttemptSummary> listAttempts(UUID quizPublicUuid,
			Pageable pageable) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		return attemptRepository
				.findAllByQuizOrderByAttemptNumberAsc(quiz, pageable)
				.map(a -> attemptMapper.toSummary(a, quiz,
						pendingAnswerCount(a)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<GradingQueueItem> getGradingQueue(UUID quizPublicUuid) {
		Quiz quiz = requireQuiz(quizPublicUuid);
		List<QuizAttempt> attempts = attemptRepository
				.findAllByQuizOrderByAttemptNumberAsc(quiz,
						Pageable.unpaged())
				.getContent();
		List<GradingQueueItem.Row> rows = new ArrayList<>();
		for (QuizAttempt a : attempts) {
			for (QuizAnswer ans : answerRepository
					.findAllByAttemptAndTextAnswerIsNotNullAndGradedAtIsNull(
							a)) {
				QuizQuestion q = ans.getQuestion();
				if (q == null) {
					continue;
				}
				rows.add(new GradingQueueItem.Row(
						ans.getPublicUuid(),
						a.getPublicUuid(),
						q.getPublicUuid(),
						a.getStudentUserId(),
						quiz.getTitle(),
						q.getPrompt(),
						q.getPoints() == null
								? Integer.valueOf(0)
								: Integer.valueOf(q.getPoints()),
						ans.getTextAnswer()));
			}
		}
		return GradingQueueItem.fromRows(rows);
	}

	@Override
	@Transactional
	public AttemptResponse gradeAttempt(UUID attemptPublicUuid,
			ManualGradeAttemptRequest request, UUID graderUserId) {
		QuizAttempt attempt = requireAttempt(attemptPublicUuid);
		if (attempt.getStatus() != AttemptStatus.SUBMITTED
				&& attempt.getStatus() != AttemptStatus.AUTO_GRADED) {
			throw new AttemptNotSubmittedException(attempt.getStatus().name());
		}
		if (request != null && request.grades() != null) {
			for (ManualGradeAnswerRequest g : request.grades()) {
				// Single-answer override delegates to the same
				// code path the per-answer PATCH uses.
				applySingleAnswerGrade(attempt, g, graderUserId);
			}
		}
		// Recompute scores and close the attempt.
		recountScoresAndClose(attempt, request == null ? null : request.feedback(),
				graderUserId);
		QuizAttempt saved = attemptRepository.save(attempt);
		log.info("Quiz attempt graded publicUuid={} status={} score={}",
				saved.getPublicUuid(), saved.getStatus(), saved.getScore());
		return attemptMapper.toResponse(saved, attempt.getQuiz(), true,
				pendingAnswerCount(saved));
	}

	@Override
	@Transactional
	public AttemptResponse overrideAnswerGrade(UUID quizPublicUuid,
			UUID attemptPublicUuid, UUID answerPublicUuid,
			ManualGradeAnswerRequest request, UUID graderUserId) {
		QuizAttempt attempt = requireAttempt(attemptPublicUuid);
		// The path includes the quiz UUID; the service re-asserts
		// the answer belongs to that quiz (anti-enumeration: a
		// teacher who knows a valid attempt UUID from another
		// quiz cannot grade against this one).
		QuizAnswer ans = answerRepository.findByPublicUuid(answerPublicUuid)
				.orElseThrow(() -> new AnswerNotFoundException(
						answerPublicUuid.toString()));
		if (ans.getAttempt() == null
				|| !attempt.getId().equals(ans.getAttempt().getId())) {
			throw new AnswerNotFoundException(answerPublicUuid.toString());
		}
		if (ans.getQuestion() == null
				|| ans.getQuestion().getQuiz() == null
				|| !attempt.getQuiz().getId().equals(
						ans.getQuestion().getQuiz().getId())) {
			throw new AnswerNotFoundException(answerPublicUuid.toString());
		}
		// The path carries the answer UUID; re-attach it on the
		// request so {@link #applySingleAnswerGrade} finds the row
		// (the body DTO allows {@code answerPublicUuid} to be null
		// for backward compatibility with the BE-7b.1 stub).
		ManualGradeAnswerRequest resolved = new ManualGradeAnswerRequest(
				answerPublicUuid, request.pointsAwarded());
		applySingleAnswerGrade(attempt, resolved, graderUserId);
		// A single override does NOT auto-close the attempt; the
		// teacher uses the {@code gradeAttempt} endpoint to close
		// it. But if the override happens to be the last pending
		// SHORT_ANSWER, we still don't close — the teacher should
		// explicitly gradeAttempt to attach the feedback.
		recountScores(attempt);
		QuizAttempt saved = attemptRepository.save(attempt);
		return attemptMapper.toResponse(saved, attempt.getQuiz(), true,
				pendingAnswerCount(saved));
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Quiz requireQuiz(UUID publicUuid) {
		return quizRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new QuizNotFoundException(publicUuid.toString()));
	}

	private QuizAttempt requireAttempt(UUID publicUuid) {
		return attemptRepository.findByPublicUuid(publicUuid)
				.orElseThrow(() -> new AttemptNotFoundException(publicUuid.toString()));
	}

	/**
	 * Resolves the {@code Student} record linked to the given user
	 * id and asserts the student is currently ACTIVE-enrolled in
	 * the section. Surfaces 403 {@code STUDENT_NOT_ENROLLED_IN_SECTION}
	 * otherwise. The fact that the user has no {@code Student} row
	 * is also treated as "not enrolled" (no row to leak).
	 */
	private void requireEnrolledStudent(Section section, UUID userId) {
		if (section == null || userId == null) {
			throw new StudentNotEnrolledException("null");
		}
		Student student = studentRepository.findByUserId(userId)
				.orElseThrow(() -> new StudentNotEnrolledException(
						section.getPublicUuid().toString()));
		boolean active = enrollmentRepository.existsActiveAt(
				student, section, java.time.LocalDate.now());
		if (!active) {
			throw new StudentNotEnrolledException(
					section.getPublicUuid().toString());
		}
	}

	private void requireCallerIsStudent(QuizAttempt attempt, UUID callerUserId) {
		if (callerUserId == null
				|| !callerUserId.equals(attempt.getStudentUserId())) {
			// Anti-enumeration: callers who don't own the attempt
			// see the same 404 as a missing attempt.
			throw new AttemptNotFoundException(attempt.getPublicUuid().toString());
		}
	}

	private boolean hasGraderAuthority() {
		// Reads Spring Security's Authentication directly to
		// decide whether the current principal carries
		// {@code LMS_QUIZ_GRADE}. The controller is already
		// gated by @PreAuthorize, so this is a belt-and-suspenders
		// check used by {@link #getAttempt} to decide whether
		// to reveal the grading outcome.
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getAuthorities() == null) {
			return false;
		}
		return auth.getAuthorities().stream()
				.anyMatch(a -> LmsAuthorities.LMS_QUIZ_GRADE.equals(a.getAuthority()));
	}

	/**
	 * Returns true if the answer row has a non-null payload for
	 * its question type (used by submit to decide if the attempt
	 * is fully answered).
	 */
	private static boolean hasPayloadFor(QuizAnswer ans, QuizQuestion q) {
		return switch (q.getQuestionType()) {
			case MC -> ans.getSelectedOptionId() != null;
			case TF -> ans.getSelectedBoolean() != null;
			case SHORT_ANSWER -> ans.getTextAnswer() != null
					&& !ans.getTextAnswer().isBlank();
		};
	}

	/**
	 * Enforces the mutually-exclusive payload rule at the service
	 * layer (the DB CHECK constraints will also reject, but a
	 * 400 from us is more actionable than a 500 from a CHECK).
	 */
	private static void validateAnswerShape(QuizQuestion q, AnswerInput in) {
		int nonNull = 0;
		if (in.selectedOptionId() != null) {
			nonNull++;
			if (q.getQuestionType() != QuestionType.MC) {
				throw new com.edushift.shared.exception.BadRequestException(
						"INCONSISTENT_PAYLOAD",
						"selectedOptionId is only valid for MC questions.");
			}
		}
		if (in.selectedBoolean() != null) {
			nonNull++;
			if (q.getQuestionType() != QuestionType.TF) {
				throw new com.edushift.shared.exception.BadRequestException(
						"INCONSISTENT_PAYLOAD",
						"selectedBoolean is only valid for TF questions.");
			}
		}
		if (in.textAnswer() != null && !in.textAnswer().isBlank()) {
			nonNull++;
			if (q.getQuestionType() != QuestionType.SHORT_ANSWER) {
				throw new com.edushift.shared.exception.BadRequestException(
						"INCONSISTENT_PAYLOAD",
						"textAnswer is only valid for SHORT_ANSWER questions.");
			}
		}
		if (nonNull != 1) {
			throw new com.edushift.shared.exception.BadRequestException(
					"INCONSISTENT_PAYLOAD",
					"Exactly one of selectedOptionId / selectedBoolean / "
							+ "textAnswer must be non-null (got " + nonNull + ").");
		}
	}

	private static void applyAnswerPayload(QuizAnswer row, QuizQuestion q,
			AnswerInput in, Map<UUID, QuizOption> optionsIndex) {
		// Clear previous payload (defensive — the row may have
		// been partially filled by an earlier autosave).
		row.setSelectedOptionId(null);
		row.setSelectedBoolean(null);
		row.setTextAnswer(null);
		row.setPointsAwarded(null);
		row.setCorrect(null);
		row.setGradedByUserId(null);
		row.setGradedAt(null);
		switch (q.getQuestionType()) {
			case MC -> {
				UUID optId = in.selectedOptionId();
				// Sanity: option must exist in the index; the FK
				// is a soft one, but the resolver catches the
				// "selected an option of a different question" case.
				QuizOption o = optId == null ? null : optionsIndex.get(optId);
				if (optId != null && o == null) {
					throw new com.edushift.shared.exception.BadRequestException(
							"OPTION_NOT_FOUND",
							"Selected option not found in this quiz: " + optId);
				}
				row.setSelectedOptionId(optId);
			}
			case TF -> row.setSelectedBoolean(in.selectedBoolean());
			case SHORT_ANSWER -> row.setTextAnswer(in.textAnswer());
		}
	}

	/**
	 * Recompute {@code autoScore}, {@code manualScore} and
	 * {@code score} from the persisted answers. After the
	 * recompute, if every answer has a non-null
	 * {@code points_awarded} the attempt is left at
	 * {@code AUTO_GRADED} (the teacher will explicitly close it).
	 * Use {@link #recountScoresAndClose} to also transition to
	 * {@code GRADED}.
	 */
	private void recountScores(QuizAttempt attempt) {
		List<QuizAnswer> answers = answerRepository
				.findAllByAttemptOrderByQuestionPositionAsc(attempt);
		short auto = 0;
		short manual = 0;
		for (QuizAnswer a : answers) {
			if (a.getPointsAwarded() == null || a.getQuestion() == null) {
				continue;
			}
			if (a.getQuestion().getQuestionType() == QuestionType.SHORT_ANSWER) {
				manual = (short) (manual + a.getPointsAwarded());
			} else {
				auto = (short) (auto + a.getPointsAwarded());
			}
		}
		attempt.setAutoScore(auto);
		attempt.setManualScore(manual);
		attempt.setScore((short) (auto + manual));
	}

	/**
	 * Same as {@link #recountScores} but also transitions the
	 * attempt to {@code GRADED} and stamps the grader.
	 */
	private void recountScoresAndClose(QuizAttempt attempt, String feedback,
			UUID graderUserId) {
		recountScores(attempt);
		attempt.setStatus(AttemptStatus.GRADED);
		attempt.setGradedAt(Instant.now());
		attempt.setGradedByUserId(graderUserId);
		if (feedback != null && !feedback.isBlank()) {
			attempt.setFeedback(feedback);
		}
	}

	/**
	 * Apply a single per-answer override (used by both
	 * {@link #gradeAttempt} and {@link #overrideAnswerGrade}).
	 * Resolves the answer from the attempt, validates the
	 * {@code pointsAwarded} range against the question's
	 * {@code points}, persists, and re-stamps the grader.
	 */
	private void applySingleAnswerGrade(QuizAttempt attempt,
			ManualGradeAnswerRequest g, UUID graderUserId) {
		// The override is identified implicitly by the answer's
		// public UUID; the request body carries only the new
		// points. We resolve by walking the answers and matching
		// the public UUID referenced by the service-internal
		// wrapper. The wire-level PATCH passes
		// {@code { pointsAwarded }} in the body; the answer UUID
		// is on the path. For the gradeAttempt bulk path we need
		// the caller to identify the answer, so we extend the
		// single-answer DTO with an optional publicUuid.
		UUID answerUuid = g.answerPublicUuid();
		if (answerUuid == null) {
			throw new com.edushift.shared.exception.BadRequestException(
					"ANSWER_NOT_FOUND",
					"gradeAttempt entry is missing answerPublicUuid.");
		}
		QuizAnswer ans = answerRepository.findByPublicUuid(answerUuid)
				.orElseThrow(() -> new AnswerNotFoundException(
						answerUuid.toString()));
		if (ans.getAttempt() == null
				|| !attempt.getId().equals(ans.getAttempt().getId())) {
			throw new AnswerNotFoundException(answerUuid.toString());
		}
		QuizQuestion q = ans.getQuestion();
		if (q == null) {
			throw new AnswerNotFoundException(answerUuid.toString());
		}
		int max = q.getPoints() == null ? 1000 : q.getPoints();
		int points = g.pointsAwarded();
		if (points < 0 || points > max) {
			throw new com.edushift.shared.exception.BadRequestException(
					"GRADE_OUT_OF_RANGE",
					"pointsAwarded must be in [0, " + max + "] (got " + points + ").");
		}
		ans.setPointsAwarded((short) points);
		ans.setCorrect(points == max);
		ans.setGradedByUserId(graderUserId);
		ans.setGradedAt(Instant.now());
		answerRepository.save(ans);
	}

	private int pendingAnswerCount(QuizAttempt attempt) {
		return (int) answerRepository
				.findAllByAttemptOrderByQuestionPositionAsc(attempt)
				.stream()
				.filter(a -> a.getQuestion() != null
						&& a.getQuestion().getQuestionType()
								== QuestionType.SHORT_ANSWER
						&& a.getGradedAt() == null)
				.count();
	}

	private Map<UUID, QuizOption> indexOptions(QuizAttempt attempt) {
		Map<UUID, QuizOption> idx = new HashMap<>();
		Set<UUID> seen = new HashSet<>();
		for (QuizQuestion q : questionRepository
				.findAllByQuizOrderByPositionAsc(attempt.getQuiz())) {
			if (q.getQuestionType() != QuestionType.MC) {
				continue;
			}
			for (QuizOption o : optionRepository
					.findAllByQuestionOrderByPositionAsc(q)) {
				if (seen.add(o.getPublicUuid())) {
					idx.put(o.getPublicUuid(), o);
				}
			}
		}
		return idx;
	}

	// ------------------------------------------------------------------
	// explicit unused-suppressors (kept to make the wiring obvious
	// in code review; IntelliJ's inspections are quiet on these)
	// ------------------------------------------------------------------
	@SuppressWarnings("unused")
	private Section unusedSectionLookup(UUID sectionPublicUuid) {
		return sectionRepository.findByPublicUuid(sectionPublicUuid)
				.orElseThrow(() -> new com.edushift.modules.quizzes.exception
						.SectionNotFoundException(sectionPublicUuid.toString()));
	}
}
