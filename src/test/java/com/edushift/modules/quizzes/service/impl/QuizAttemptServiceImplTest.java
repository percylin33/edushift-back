package com.edushift.modules.quizzes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.quizzes.dto.AnswerInput;
import com.edushift.modules.quizzes.dto.AttemptResponse;
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
import com.edushift.modules.quizzes.mapper.QuizAttemptMapper;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.entity.Student;
import com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizAttemptServiceImpl — taker + teacher orchestration, grading pipeline")
class QuizAttemptServiceImplTest {

    @Mock QuizRepository quizRepository;
    @Mock QuizAttemptRepository attemptRepository;
    @Mock QuizAnswerRepository answerRepository;
    @Mock QuizQuestionRepository questionRepository;
    @Mock QuizOptionRepository optionRepository;
    @Mock SectionRepository sectionRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock QuizAttemptMapper attemptMapper;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks QuizAttemptServiceImpl service;

    private Quiz publishedQuiz;
    private Section section;
    private QuizAttempt attempt;
    private final UUID studentUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        section = new Section();
        section.setPublicUuid(UUID.randomUUID());

        publishedQuiz = new Quiz();
        publishedQuiz.setPublicUuid(UUID.randomUUID());
        publishedQuiz.setSection(section);
        publishedQuiz.setTitle("Test Quiz — " + UUID.randomUUID().toString().substring(0, 8));
        publishedQuiz.setStatus(QuizStatus.PUBLISHED);
        publishedQuiz.setAttemptsAllowed((short) 3);

        attempt = new QuizAttempt();
        attempt.setPublicUuid(UUID.randomUUID());
        attempt.setQuiz(publishedQuiz);
        attempt.setStudentUserId(studentUuid);
        attempt.setSubmitterUserId(studentUuid);
        attempt.setAttemptNumber((short) 1);
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(Instant.now());
    }

    // ------------------------------------------------------------------
    // startAttempt
    // ------------------------------------------------------------------

    @Test
    @DisplayName("startAttempt — missing quiz → QuizNotFoundException")
    void start_missingQuiz() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("startAttempt — quiz not PUBLISHED → QuizNotPublishedException")
    void start_notPublished() {
        publishedQuiz.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(QuizNotPublishedException.class);
    }

    @Test
    @DisplayName("startAttempt — user has no User row → StudentNotEnrolledException")
    void start_unknownUser() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        // DEBT-FK-BUGS-3 / V76: students.user_id stores publicUuid now,
        // so the service goes straight to findByUserId(publicUuid).
        when(studentRepository.findByUserId(studentUuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(StudentNotEnrolledException.class);
    }

    @Test
    @DisplayName("startAttempt — user has no Student row → StudentNotEnrolledException")
    void start_unknownStudent() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        var student = new Student();
        when(studentRepository.findByUserId(studentUuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(StudentNotEnrolledException.class);
    }

    @Test
    @DisplayName("startAttempt — student not ACTIVE-enrolled → StudentNotEnrolledException")
    void start_notEnrolled() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        var student = new Student();
        when(studentRepository.findByUserId(studentUuid)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveAt(student, section, java.time.LocalDate.now()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(StudentNotEnrolledException.class);
    }

    @Test
    @DisplayName("startAttempt — exhausted attempts → AttemptsExhaustedException")
    void start_exhausted() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        var student = new Student();
        when(studentRepository.findByUserId(studentUuid)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveAt(any(), any(), any())).thenReturn(true);
        when(attemptRepository.countByQuizAndStudentUserId(publishedQuiz, studentUuid)).thenReturn(3L);
        assertThatThrownBy(() -> service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid))
                .isInstanceOf(AttemptsExhaustedException.class);
    }

    @Test
    @DisplayName("startAttempt — happy path persists IN_PROGRESS attempt")
    void start_happyPath() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        var student = new Student();
        when(studentRepository.findByUserId(studentUuid)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveAt(any(), any(), any())).thenReturn(true);
        when(attemptRepository.countByQuizAndStudentUserId(any(), any())).thenReturn(0L);
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptMapper.toResponse(any(), any(), anyBoolean(), anyInt())).thenReturn(mock(AttemptResponse.class));

        var resp = service.startAttempt(UUID.randomUUID(), studentUuid, studentUuid);
        assertThat(resp).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    // ------------------------------------------------------------------
    // getAttempt
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getAttempt — caller is not the student & not a grader → AttemptNotFoundException")
    void get_antiEnumeration() {
        attempt.setStudentUserId(UUID.randomUUID()); // different from caller
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.getAttempt(UUID.randomUUID(), studentUuid))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // saveAnswers
    // ------------------------------------------------------------------

    @Test
    @DisplayName("saveAnswers — attempt not IN_PROGRESS → AttemptNotInProgressException")
    void saveAnswers_notInProgress() {
        attempt.setStatus(AttemptStatus.SUBMITTED);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.saveAnswers(UUID.randomUUID(), studentUuid, List.of()))
                .isInstanceOf(AttemptNotInProgressException.class);
    }

    @Test
    @DisplayName("saveAnswers — caller is not the student → AttemptNotFoundException (anti-enumeration)")
    void saveAnswers_wrongCaller() {
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        var otherStudent = UUID.randomUUID();
        assertThatThrownBy(() -> service.saveAnswers(UUID.randomUUID(), otherStudent, List.of()))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    @DisplayName("saveAnswers — empty list returns current attempt (no-op)")
    void saveAnswers_emptyList() {
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt)).thenReturn(List.of());
        when(attemptMapper.toResponse(any(), any(), anyBoolean(), anyInt())).thenReturn(mock(AttemptResponse.class));
        var resp = service.saveAnswers(UUID.randomUUID(), studentUuid, List.of());
        assertThat(resp).isNotNull();
    }

    @Test
    @DisplayName("saveAnswers — inconsistent payload (TF with selectedOptionId) → BadRequestException")
    void saveAnswers_inconsistentPayload() {
        var qq = new QuizQuestion();
        qq.setPublicUuid(UUID.randomUUID());
        qq.setQuestionType(QuestionType.TF);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        when(questionRepository.findAllByQuizOrderByPositionAsc(any())).thenReturn(List.of(qq));

        var input = new AnswerInput(qq.getPublicUuid(), QuestionType.TF,
                UUID.randomUUID(), null, null); // selectedOptionId set on a TF!
        assertThatThrownBy(() -> service.saveAnswers(UUID.randomUUID(), studentUuid, List.of(input)))
                .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                .hasMessageContaining("selectedOptionId");
    }

    // ------------------------------------------------------------------
    // submitAttempt
    // ------------------------------------------------------------------

    @Test
    @DisplayName("submitAttempt — attempt not IN_PROGRESS → AttemptNotInProgressException")
    void submit_notInProgress() {
        attempt.setStatus(AttemptStatus.GRADED);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.submitAttempt(UUID.randomUUID(), studentUuid))
                .isInstanceOf(AttemptNotInProgressException.class);
    }

    @Test
    @DisplayName("submitAttempt — no answers → AUTO_GRADED with score=0")
    void submit_noAnswers() {
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt)).thenReturn(List.of());
        when(questionRepository.findAllByQuizOrderByPositionAsc(any())).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptMapper.toResponse(any(), any(), anyBoolean(), anyInt())).thenReturn(mock(AttemptResponse.class));

        service.submitAttempt(UUID.randomUUID(), studentUuid);
        assertThat(attempt.getStatus()).isIn(AttemptStatus.AUTO_GRADED, AttemptStatus.GRADED);
    }

    // ------------------------------------------------------------------
    // listAttempts / getGradingQueue
    // ------------------------------------------------------------------

    @Test
    @DisplayName("listAttempts — delegates to repo with pageable")
    void listAttempts() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        when(attemptRepository.findAllByQuizOrderByAttemptNumberAsc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        var p = service.listAttempts(UUID.randomUUID(), PageRequest.of(0, 10));
        assertThat(p).isEmpty();
    }

    @Test
    @DisplayName("listAttempts — missing quiz → QuizNotFoundException")
    void listAttempts_missingQuiz() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listAttempts(UUID.randomUUID(), Pageable.unpaged()))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("getGradingQueue — collects SHORT_ANSWER rows with no grade yet")
    void getGradingQueue() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(publishedQuiz));
        when(attemptRepository.findAllByQuizOrderByAttemptNumberAsc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(attempt)));
        var q = mcQuestion();
        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        ans.setQuestion(q);
        ans.setTextAnswer("Paris");
        when(answerRepository.findAllByAttemptAndTextAnswerIsNotNullAndGradedAtIsNull(attempt))
                .thenReturn(List.of(ans));

        var items = service.getGradingQueue(UUID.randomUUID());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).textAnswer()).isEqualTo("Paris");
    }

    // ------------------------------------------------------------------
    // gradeAttempt
    // ------------------------------------------------------------------

    @Test
    @DisplayName("gradeAttempt — attempt not SUBMITTED/AUTO_GRADED → AttemptNotSubmittedException")
    void gradeAttempt_notSubmitted() {
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.gradeAttempt(UUID.randomUUID(),
                new ManualGradeAttemptRequest(null, null), UUID.randomUUID()))
                .isInstanceOf(AttemptNotSubmittedException.class);
    }

    @Test
    @DisplayName("gradeAttempt — entry without answerPublicUuid → BadRequestException")
    void gradeAttempt_missingAnswerUuid() {
        attempt.setStatus(AttemptStatus.AUTO_GRADED);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        var req = new ManualGradeAttemptRequest(
                List.of(new ManualGradeAnswerRequest(null, 5)), null);
        assertThatThrownBy(() -> service.gradeAttempt(UUID.randomUUID(), req, UUID.randomUUID()))
                .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                .hasMessageContaining("answerPublicUuid");
    }

    @Test
    @DisplayName("gradeAttempt — entry referencing unknown answer → AnswerNotFoundException")
    void gradeAttempt_unknownAnswer() {
        attempt.setStatus(AttemptStatus.AUTO_GRADED);
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        when(answerRepository.findByPublicUuid(any())).thenReturn(Optional.empty());

        var req = new ManualGradeAttemptRequest(
                List.of(new ManualGradeAnswerRequest(UUID.randomUUID(), 5)), null);
        assertThatThrownBy(() -> service.gradeAttempt(UUID.randomUUID(), req, UUID.randomUUID()))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("gradeAttempt — pointsAwarded > question.points → BadRequestException GRADE_OUT_OF_RANGE")
    void gradeAttempt_outOfRange() {
        attempt.setStatus(AttemptStatus.AUTO_GRADED);
        attempt.setId(UUID.randomUUID());
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));

        var q = mcQuestion();
        q.setPoints((short) 3);
        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        when(answerRepository.findByPublicUuid(ans.getPublicUuid())).thenReturn(Optional.of(ans));

        var req = new ManualGradeAttemptRequest(
                List.of(new ManualGradeAnswerRequest(ans.getPublicUuid(), 99)), null);
        assertThatThrownBy(() -> service.gradeAttempt(UUID.randomUUID(), req, UUID.randomUUID()))
                .isInstanceOfSatisfying(com.edushift.shared.exception.BadRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("GRADE_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("gradeAttempt — happy path closes the attempt")
    void gradeAttempt_happyPath() {
        attempt.setStatus(AttemptStatus.AUTO_GRADED);
        attempt.setId(UUID.randomUUID());
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));

        var q = mcQuestion();
        q.setPoints((short) 5);
        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        ans.setAttempt(attempt);
        ans.setQuestion(q);
        when(answerRepository.findByPublicUuid(ans.getPublicUuid())).thenReturn(Optional.of(ans));
        lenient().when(answerRepository.findAllByAttemptOrderByQuestionPositionAsc(attempt))
                .thenReturn(List.of(ans));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attemptMapper.toResponse(any(), any(), anyBoolean(), anyInt())).thenReturn(mock(AttemptResponse.class));

        var req = new ManualGradeAttemptRequest(
                List.of(new ManualGradeAnswerRequest(ans.getPublicUuid(), 5)), "fb");
        var resp = service.gradeAttempt(UUID.randomUUID(), req, UUID.randomUUID());
        assertThat(resp).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getGradedAt()).isNotNull();
        assertThat(attempt.getFeedback()).isEqualTo("fb");
    }

    // ------------------------------------------------------------------
    // overrideAnswerGrade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("overrideAnswerGrade — missing attempt → AttemptNotFoundException")
    void override_missingAttempt() {
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overrideAnswerGrade(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new ManualGradeAnswerRequest(UUID.randomUUID(), 5),
                UUID.randomUUID()))
                .isInstanceOf(AttemptNotFoundException.class);
    }

    @Test
    @DisplayName("overrideAnswerGrade — missing answer → AnswerNotFoundException")
    void override_missingAnswer() {
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));
        when(answerRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overrideAnswerGrade(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new ManualGradeAnswerRequest(UUID.randomUUID(), 5),
                UUID.randomUUID()))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("overrideAnswerGrade — answer belongs to a different attempt → AnswerNotFoundException")
    void override_wrongAttempt() {
        attempt.setId(UUID.randomUUID());
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));

        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        var otherAttempt = new QuizAttempt();
        otherAttempt.setId(UUID.randomUUID());
        ans.setAttempt(otherAttempt);
        when(answerRepository.findByPublicUuid(any())).thenReturn(Optional.of(ans));

        assertThatThrownBy(() -> service.overrideAnswerGrade(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new ManualGradeAnswerRequest(UUID.randomUUID(), 5),
                UUID.randomUUID()))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    @Test
    @DisplayName("overrideAnswerGrade — answer belongs to a different quiz → AnswerNotFoundException")
    void override_wrongQuiz() {
        attempt.setId(UUID.randomUUID());
        publishedQuiz.setId(UUID.randomUUID());
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.of(attempt));

        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        ans.setAttempt(attempt);
        var otherQuestion = new QuizQuestion();
        var otherQuiz = new Quiz();
        otherQuiz.setId(UUID.randomUUID());
        otherQuestion.setQuiz(otherQuiz);
        ans.setQuestion(otherQuestion);
        when(answerRepository.findByPublicUuid(any())).thenReturn(Optional.of(ans));

        assertThatThrownBy(() -> service.overrideAnswerGrade(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new ManualGradeAnswerRequest(UUID.randomUUID(), 5),
                UUID.randomUUID()))
                .isInstanceOf(AnswerNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static QuizQuestion mcQuestion() {
        var q = new QuizQuestion();
        q.setPublicUuid(UUID.randomUUID());
        q.setQuestionType(QuestionType.MC);
        q.setPoints((short) 5);
        q.setPrompt("Q?");
        var opt = new QuizOption();
        opt.setPublicUuid(UUID.randomUUID());
        opt.setCorrect(true);
        q.setQuiz(publishedQuizInternal());
        return q;
    }

    private static Quiz publishedQuizInternal() {
        var q = new Quiz();
        q.setPublicUuid(UUID.randomUUID());
        q.setStatus(QuizStatus.PUBLISHED);
        return q;
    }
}