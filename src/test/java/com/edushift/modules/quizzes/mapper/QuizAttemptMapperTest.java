package com.edushift.modules.quizzes.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edushift.modules.quizzes.entity.AttemptStatus;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.entity.QuizAnswer;
import com.edushift.modules.quizzes.entity.QuizAttempt;
import com.edushift.modules.quizzes.entity.QuizQuestion;
import com.edushift.modules.quizzes.repository.QuizAnswerRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuizAttemptMapper — Attempt → AttemptResponse / AttemptSummary")
class QuizAttemptMapperTest {

    private QuizAnswerRepository answerRepo;
    private QuizAttemptMapper mapper;

    @BeforeEach
    void setUp() {
        answerRepo = mock(QuizAnswerRepository.class);
        mapper = new QuizAttemptMapper(answerRepo);
    }

    @Test
    @DisplayName("toResponse (no quiz) — pulls answers from repo, reveal flag passthrough")
    void toResponse_noQuiz() {
        var attempt = attempt();
        var answers = List.of(answer(5, true), answer(0, false));
        when(answerRepo.findAllByAttemptOrderByQuestionPositionAsc(attempt)).thenReturn(answers);

        var r = mapper.toResponse(attempt, true, 2);

        assertThat(r.publicUuid()).isEqualTo(attempt.getPublicUuid());
        assertThat(r.studentUserId()).isEqualTo(attempt.getStudentUserId());
        assertThat(r.status()).isEqualTo(AttemptStatus.GRADED);
        assertThat(r.revealCorrectness()).isTrue();
        assertThat(r.answers()).hasSize(2);
        assertThat(r.answers().get(0).correct()).isTrue();
        assertThat(r.answers().get(0).pointsAwarded()).isEqualTo(5);
        assertThat(r.answers().get(1).correct()).isFalse();
    }

    @Test
    @DisplayName("toResponse — reveal=false drops correct/pointsAwarded fields on answers")
    void toResponse_revealFalse() {
        var attempt = attempt();
        when(answerRepo.findAllByAttemptOrderByQuestionPositionAsc(attempt))
                .thenReturn(List.of(answer(5, true)));

        var r = mapper.toResponse(attempt, attempt.getQuiz(), false, 0);

        assertThat(r.revealCorrectness()).isFalse();
        assertThat(r.answers()).hasSize(1);
        assertThat(r.answers().get(0).correct()).isNull();
        assertThat(r.answers().get(0).pointsAwarded()).isNull();
        assertThat(r.answers().get(0).gradedByUserId()).isNull();
        assertThat(r.answers().get(0).gradedAt()).isNull();
    }

    @Test
    @DisplayName("toResponse — quiz maxScore is exposed; null quiz → null maxScore")
    void toResponse_quizMaxScore() {
        var attempt = attempt();
        when(answerRepo.findAllByAttemptOrderByQuestionPositionAsc(attempt)).thenReturn(List.of());

        var withQuiz = mapper.toResponse(attempt, attempt.getQuiz(), true, 0);
        assertThat(withQuiz.maxScore()).isEqualTo(Integer.valueOf(100));

        var withoutQuiz = mapper.toResponse(attempt, true, 0);
        assertThat(withoutQuiz.maxScore()).isNull();
    }

    @Test
    @DisplayName("toResponse — timeRemainingSeconds computed from expiresAt")
    void toResponse_timeRemaining() {
        var attempt = attempt();
        attempt.setExpiresAt(Instant.now().plusSeconds(120));
        when(answerRepo.findAllByAttemptOrderByQuestionPositionAsc(attempt)).thenReturn(List.of());

        var r = mapper.toResponse(attempt, true, 0);
        assertThat(r.timeRemainingSeconds()).isBetween(110, 120);

        attempt.setExpiresAt(null);
        var r2 = mapper.toResponse(attempt, true, 0);
        assertThat(r2.timeRemainingSeconds()).isNull();
    }

    @Test
    @DisplayName("toSummary — slim TEACHER projection with pending count")
    void toSummary() {
        var attempt = attempt();
        var s = mapper.toSummary(attempt, attempt.getQuiz(), 7);
        assertThat(s.publicUuid()).isEqualTo(attempt.getPublicUuid());
        assertThat(s.quizPublicUuid()).isEqualTo(attempt.getQuiz().getPublicUuid());
        assertThat(s.maxScore()).isEqualTo(Integer.valueOf(100));
        assertThat(s.pendingAnswerCount()).isEqualTo(7);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static QuizAttempt attempt() {
        var a = new QuizAttempt();
        a.setPublicUuid(UUID.randomUUID());
        a.setStudentUserId(UUID.randomUUID());
        a.setSubmitterUserId(UUID.randomUUID());
        a.setAttemptNumber((short) 1);
        a.setStatus(AttemptStatus.GRADED);
        a.setStartedAt(Instant.now());
        a.setSubmittedAt(Instant.now());
        a.setAutoScore((short) 5);
        a.setManualScore((short) 0);
        a.setScore((short) 5);

        var q = new Quiz();
        q.setPublicUuid(UUID.randomUUID());
        q.setMaxScore((short) 100);
        a.setQuiz(q);
        return a;
    }

    private static QuizAnswer answer(int points, boolean correct) {
        var ans = new QuizAnswer();
        ans.setPublicUuid(UUID.randomUUID());
        var q = new QuizQuestion();
        q.setPublicUuid(UUID.randomUUID());
        ans.setQuestion(q);
        ans.setPointsAwarded((short) points);
        ans.setCorrect(correct);
        ans.setGradedByUserId(UUID.randomUUID());
        ans.setGradedAt(Instant.now());
        ans.setUpdatedAt(Instant.now());
        return ans;
    }
}