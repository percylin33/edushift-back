package com.edushift.modules.quizzes.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.edushift.modules.evaluations.graderecord.repository.GradeRecordRepository;
import com.edushift.modules.evaluations.repository.EvaluationRepository;
import com.edushift.modules.evaluations.rubric.repository.RubricRepository;
import com.edushift.modules.quizzes.entity.Quiz;
import com.edushift.modules.quizzes.exception.QuizNotFoundException;
import com.edushift.modules.quizzes.exception.RubricNotFoundException;
import com.edushift.modules.quizzes.mapper.QuizMapper;
import com.edushift.modules.quizzes.repository.QuizAttemptRepository;
import com.edushift.modules.quizzes.repository.QuizRepository;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.teachers.assignments.repository.TeacherAssignmentRepository;
import com.edushift.modules.teachers.repository.TeacherRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizRubricServiceImpl — attach/detach/gradeWithRubric guards")
class QuizRubricServiceImplTest {

    @Mock QuizRepository quizRepository;
    @Mock QuizAttemptRepository attemptRepository;
    @Mock QuizMapper quizMapper;
    @Mock RubricRepository rubricRepository;
    @Mock EvaluationRepository evaluationRepository;
    @Mock GradeRecordRepository gradeRecordRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock TeacherAssignmentRepository teacherAssignmentRepository;
    @Mock StudentRepository studentRepository;
    @Mock UserRepository userRepository;

    @InjectMocks QuizRubricServiceImpl service;

    @Test
    @DisplayName("attachRubric — missing quiz → QuizNotFoundException")
    void attach_missingQuiz() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.attachRubric(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("detachRubric — missing quiz → QuizNotFoundException")
    void detach_missingQuiz() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detachRubric(UUID.randomUUID()))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    @DisplayName("gradeWithRubric — missing attempt surfaces as NotFoundException")
    void gradeWithRubric_missingAttempt() {
        when(quizRepository.findByPublicUuid(any())).thenReturn(Optional.of(quiz()));
        when(attemptRepository.findByPublicUuid(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.gradeWithRubric(UUID.randomUUID(),
                new com.edushift.modules.quizzes.dto.GradeWithRubricRequest(
                        java.util.List.of(
                                new com.edushift.modules.quizzes.dto.GradeWithRubricRequest
                                        .CriterionLevelPick("c1", "A")),
                        null),
                UUID.randomUUID()))
                .isInstanceOf(com.edushift.shared.exception.NotFoundException.class);
    }

    // The happy paths of this service depend on the Evaluations module's
    // Evaluation/Rubric/GradeRecord graph (lifecycle, status, criteria
    // weights). Those flows are exercised by integration tests; the unit
    // tests above pin down the guards the controller relies on.

    private static Quiz quiz() {
        var q = new Quiz();
        q.setPublicUuid(UUID.randomUUID());
        return q;
    }
}