package com.edushift.modules.quizzes.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.academic.section.entity.Section;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.modules.quizzes.dto.AddOptionRequest;
import com.edushift.modules.quizzes.dto.CreateOptionRequest;
import com.edushift.modules.quizzes.dto.CreateQuestionRequest;
import com.edushift.modules.quizzes.dto.CreateQuizRequest;
import com.edushift.modules.quizzes.dto.UpdateQuizRequest;
import com.edushift.modules.quizzes.entity.QuestionType;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizOption;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.entity.QuizStatus;
import com.edushift.modules.quizzes.exception.InvalidQuizStateException;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.RecordEmptyPatchException;
import com.edushift.modules.quizzes.exception.SectionNotFoundException;
import com.edushift.modules.quizzes.mapper.QuizMapper;
import com.edushift.modules.quizzes.repository.QuizOptionRepository;
import com.edushift.modules.quizzes.repository.QuizQuestionRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.quizzes.service.QuizAttemptService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizServiceImpl — builder, reader & grading orchestration")
class QuizServiceImplTest {

    @Mock QuizRepository quizRepository;
    @Mock QuizQuestionRepository questionRepository;
    @Mock QuizOptionRepository optionRepository;
    @Mock SectionRepository sectionRepository;
    @Mock QuizMapper quizMapper;
    @Mock QuizAttemptService attemptService;

    @InjectMocks QuizServiceImpl service;

    private Section section;

    @BeforeEach
    void setUp() {
        section = new Section();
        section.setPublicUuid(UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create — happy path persists a DRAFT quiz with no questions")
    void create_happyPath() {
        var req = new CreateQuizRequest("T", null, Instant.now().plusSeconds(60),
                30, 2, 100, null);
        when(sectionRepository.findByPublicUuid(section.getPublicUuid()))
                .thenReturn(Optional.of(section));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findAllByQuizOrderByPositionAsc(any()))
                .thenReturn(List.of());

        var resp = service.create(section.getPublicUuid(), req, UUID.randomUUID());

        assertThat(resp).isNotNull();
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    @DisplayName("create — past dueAt → BadRequestException")
    void create_pastDueAt() {
        var req = new CreateQuizRequest("T", null, Instant.now().minusSeconds(60),
                30, 2, 100, null);
        assertThatThrownBy(() -> service.create(section.getPublicUuid(), req, UUID.randomUUID()))
                .isInstanceOf(com.edushift.shared.exception.BadRequestException.class)
                .hasMessageContaining("dueAt");
    }

    @Test
    @DisplayName("create — missing section → SectionNotFoundException")
    void create_missingSection() {
        var req = new CreateQuizRequest("T", null, null, 30, 2, 100, null);
        when(sectionRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), req, UUID.randomUUID()))
                .isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    @DisplayName("create — bulk questions block persists each question")
    void create_withQuestions() {
        var req = new CreateQuizRequest("T", null, null, 30, 2, 100,
                List.of(mcQuestion(2, 1)));
        when(sectionRepository.findByPublicUuid(any())).thenReturn(Optional.of(section));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.save(any(QuizQuestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findAllByQuizOrderByPositionAsc(any())).thenReturn(List.of());

        service.create(section.getPublicUuid(), req, UUID.randomUUID());

        verify(questionRepository).save(any(QuizQuestion.class));
    }

    // ------------------------------------------------------------------
    // patch
    // ------------------------------------------------------------------

    @Test
    @DisplayName("patch — empty body → RecordEmptyPatchException")
    void patch_emptyBody() {
        var req = new UpdateQuizRequest(null, null, null, null, null, null);
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), req))
                .isInstanceOf(RecordEmptyPatchException.class);
        verify(quizRepository, never()).save(any());
    }

    @Test
    @DisplayName("patch — missing quiz → QuizNotFoundException")
    void patch_missingQuiz() {
        var req = new UpdateQuizRequest("T", null, null, null, null, null);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), req))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("patch — title on PUBLISHED quiz → InvalidQuizStateException")
    void patch_titleOnPublished() {
        var q = new Quiz();
        q.setStatus(QuizStatus.PUBLISHED);
        q.setPublicUuid(UUID.randomUUID());
        when(quizRepository.findByPublicUuid(q.getPublicUuid())).thenReturn(Optional.of(q));

        var req = new UpdateQuizRequest("New title", null, null, null, null, null);
        assertThatThrownBy(() -> service.patch(q.getPublicUuid(), req))
                .isInstanceOf(InvalidQuizStateException.class);
    }

    @Test
    @DisplayName("patch — dueAt on PUBLISHED is allowed (only dueAt/maxAttempts restricted)")
    void patch_dueAtOnPublished() {
        var q = new Quiz();
        q.setStatus(QuizStatus.PUBLISHED);
        q.setPublicUuid(UUID.randomUUID());
        when(quizRepository.findByPublicUuid(q.getPublicUuid())).thenReturn(Optional.of(q));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(questionRepository.findAllByQuizOrderByPositionAsc(any()))
                .thenReturn(List.of());

        var req = new UpdateQuizRequest(null, null, Instant.now().plusSeconds(60), null, null, null);
        var resp = service.patch(q.getPublicUuid(), req);
        assertThat(resp).isNotNull();
    }

    // ------------------------------------------------------------------
    // addQuestion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addQuestion — missing quiz → QuizNotFoundException")
    void addQuestion_missingQuiz() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), mcQuestion(2, 1)))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("addQuestion — quiz in PUBLISHED state → InvalidQuizStateException")
    void addQuestion_publishedQuiz() {
        var q = new Quiz();
        q.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), mcQuestion(2, 1)))
                .isInstanceOf(InvalidQuizStateException.class);
    }

    @Test
    @DisplayName("addQuestion — MC with one option flagged correct persists options")
    void addQuestion_mcPersistsOptions() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        when(questionRepository.countByQuiz(q)).thenReturn(0L);
        when(questionRepository.save(any(QuizQuestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.findAllByQuestionOrderByPositionAsc(any()))
                .thenReturn(List.of());

        var resp = service.addQuestion(UUID.randomUUID(), mcQuestion(2, 1));
        assertThat(resp).isNotNull();
        verify(optionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("addQuestion — MC with no correct option → QuestionValidationException")
    void addQuestion_mcNoCorrect() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));

        var req = new CreateQuestionRequest(QuestionType.MC, "Q?", 5, null,
                null, null, null, List.of(
                        new CreateOptionRequest("A", false, null),
                        new CreateOptionRequest("B", false, null)));
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addQuestion — MC with 1 option → MC_QUESTION_NEEDS_2_TO_6_OPTIONS")
    void addQuestion_mcTooFewOptions() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));

        var req = new CreateQuestionRequest(QuestionType.MC, "Q?", 5, null,
                null, null, null, List.of(new CreateOptionRequest("A", true, null)));
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addQuestion — TF without correctBoolean → BadRequestException")
    void addQuestion_tfMissingCorrectBoolean() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));

        var req = new CreateQuestionRequest(QuestionType.TF, "Q?", 1, null,
                null, null, null, null);
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.shared.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("addQuestion — TF with options → QuestionValidationException")
    void addQuestion_tfWithOptions() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));

        var req = new CreateQuestionRequest(QuestionType.TF, "Q?", 1, null,
                null, null, true, List.of(new CreateOptionRequest("A", true, null)));
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addQuestion — SHORT_ANSWER with options → QuestionValidationException")
    void addQuestion_shortWithOptions() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));

        var req = new CreateQuestionRequest(QuestionType.SHORT_ANSWER, "Q?", 1, null,
                null, null, null, List.of(new CreateOptionRequest("A", true, null)));
        assertThatThrownBy(() -> service.addQuestion(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    // ------------------------------------------------------------------
    // addOption
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addOption — non-MC question → QuestionValidationException (TF_QUESTION_HAS_OPTIONS)")
    void addOption_nonMcQuestion() {
        var qq = new QuizQuestion();
        qq.setQuestionType(QuestionType.TF);
        when(questionRepository.findByPublicUuid(any())).thenReturn(Optional.of(qq));
        var req = new AddOptionRequest(new CreateOptionRequest("A", true, null));
        assertThatThrownBy(() -> service.addOption(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addOption — blank label → QuestionValidationException (QUESTION_PROMPT_BLANK)")
    void addOption_blankLabel() {
        var qq = new QuizQuestion();
        qq.setQuestionType(QuestionType.MC);
        var quiz = new Quiz();
        quiz.setStatus(QuizStatus.DRAFT);
        qq.setQuiz(quiz);
        when(questionRepository.findByPublicUuid(any())).thenReturn(Optional.of(qq));

        var req = new AddOptionRequest(new CreateOptionRequest("  ", true, null));
        assertThatThrownBy(() -> service.addOption(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addOption — null isCorrect → QuestionValidationException (MC_QUESTION_MUST_HAVE_EXACTLY_ONE_CORRECT)")
    void addOption_nullIsCorrect() {
        var qq = new QuizQuestion();
        qq.setQuestionType(QuestionType.MC);
        var quiz = new Quiz();
        quiz.setStatus(QuizStatus.DRAFT);
        qq.setQuiz(quiz);
        when(questionRepository.findByPublicUuid(any())).thenReturn(Optional.of(qq));

        var req = new AddOptionRequest(new CreateOptionRequest("A", null, null));
        assertThatThrownBy(() -> service.addOption(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addOption — exceeding exactly-one-correct invariant → QuestionValidationException")
    void addOption_tooManyCorrect() {
        var qq = new QuizQuestion();
        qq.setQuestionType(QuestionType.MC);
        var quiz = new Quiz();
        quiz.setStatus(QuizStatus.DRAFT);
        qq.setQuiz(quiz);
        when(questionRepository.findByPublicUuid(any())).thenReturn(Optional.of(qq));
        when(optionRepository.findAllByQuestionOrderByPositionAsc(any())).thenReturn(List.of());
        when(optionRepository.countByQuestionAndCorrectIsTrue(qq)).thenReturn(2L);

        var req = new AddOptionRequest(new CreateOptionRequest("C", true, null));
        assertThatThrownBy(() -> service.addOption(UUID.randomUUID(), req))
                .isInstanceOf(com.edushift.modules.quizzes.exception.QuestionValidationException.class);
    }

    @Test
    @DisplayName("addOption — happy path persists and re-validates the invariant")
    void addOption_happyPath() {
        var qq = new QuizQuestion();
        qq.setQuestionType(QuestionType.MC);
        qq.setPublicUuid(UUID.randomUUID());
        var quiz = new Quiz();
        quiz.setStatus(QuizStatus.DRAFT);
        qq.setQuiz(quiz);
        when(questionRepository.findByPublicUuid(any())).thenReturn(Optional.of(qq));
        when(optionRepository.findAllByQuestionOrderByPositionAsc(qq)).thenReturn(List.of());
        when(optionRepository.countByQuestionAndCorrectIsTrue(qq)).thenReturn(1L);
        when(optionRepository.save(any(QuizOption.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new AddOptionRequest(new CreateOptionRequest("A", true, null));
        var resp = service.addOption(UUID.randomUUID(), req);
        assertThat(resp).isNotNull();
    }

    // ------------------------------------------------------------------
    // publish / close / delete / getByPublicUuid / listBySection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("publish — not DRAFT → InvalidQuizStateException.notDraft")
    void publish_notDraft() {
        var q = new Quiz();
        q.setStatus(QuizStatus.PUBLISHED);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.publish(UUID.randomUUID()))
                .isInstanceOf(InvalidQuizStateException.class)
                .hasMessageContaining("PUBLISHED");
    }

    @Test
    @DisplayName("publish — zero questions → InvalidQuizStateException.noQuestions")
    void publish_noQuestions() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        when(questionRepository.countByQuiz(q)).thenReturn(0L);
        assertThatThrownBy(() -> service.publish(UUID.randomUUID()))
                .isInstanceOf(InvalidQuizStateException.class)
                .hasMessageContaining("zero questions");
    }

    @Test
    @DisplayName("publish — happy path sets PUBLISHED + publishedAt")
    void publish_happyPath() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        q.setPublicUuid(UUID.randomUUID());
        when(quizRepository.findByPublicUuid(q.getPublicUuid())).thenReturn(Optional.of(q));
        when(questionRepository.countByQuiz(q)).thenReturn(1L);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(questionRepository.findAllByQuizOrderByPositionAsc(any()))
                .thenReturn(List.of());

        var resp = service.publish(q.getPublicUuid());
        assertThat(resp).isNotNull();
        assertThat(q.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(q.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("close — already CLOSED → InvalidQuizStateException.alreadyClosed")
    void close_alreadyClosed() {
        var q = new Quiz();
        q.setStatus(QuizStatus.CLOSED);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.close(UUID.randomUUID()))
                .isInstanceOf(InvalidQuizStateException.class);
    }

    @Test
    @DisplayName("close — DRAFT → InvalidQuizStateException.notPublished")
    void close_draft() {
        var q = new Quiz();
        q.setStatus(QuizStatus.DRAFT);
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.close(UUID.randomUUID()))
                .isInstanceOf(InvalidQuizStateException.class);
    }

    @Test
    @DisplayName("close — PUBLISHED happy path sets CLOSED + closedAt")
    void close_happyPath() {
        var q = new Quiz();
        q.setStatus(QuizStatus.PUBLISHED);
        q.setPublicUuid(UUID.randomUUID());
        when(quizRepository.findByPublicUuid(q.getPublicUuid())).thenReturn(Optional.of(q));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(questionRepository.findAllByQuizOrderByPositionAsc(any()))
                .thenReturn(List.of());

        var resp = service.close(q.getPublicUuid());
        assertThat(resp).isNotNull();
        assertThat(q.getStatus()).isEqualTo(QuizStatus.CLOSED);
        assertThat(q.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("delete — soft-deletes via @SQLDelete")
    void delete_quiz() {
        var q = new Quiz();
        q.setPublicUuid(UUID.randomUUID());
        when(quizRepository.findByPublicUuid(q.getPublicUuid())).thenReturn(Optional.of(q));
        service.delete(q.getPublicUuid());
        verify(quizRepository).delete(q);
    }

    @Test
    @DisplayName("getByPublicUuid — missing quiz → QuizNotFoundException")
    void get_missing() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByPublicUuid(UUID.randomUUID()))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("listBySection — missing section → SectionNotFoundException")
    void listBySection_missingSection() {
        when(sectionRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listBySection(UUID.randomUUID(),
                org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(SectionNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static CreateQuestionRequest mcQuestion(int optionCount, int correctIdx) {
        var opts = new java.util.ArrayList<CreateOptionRequest>();
        for (int i = 0; i < optionCount; i++) {
            opts.add(new CreateOptionRequest(
                    "Opt-" + i, i == correctIdx, null));
        }
        return new CreateQuestionRequest(QuestionType.MC, "Q?", 5, null,
                null, null, null, opts);
    }
}