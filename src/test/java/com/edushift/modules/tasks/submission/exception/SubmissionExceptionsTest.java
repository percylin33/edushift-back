package com.edushift.modules.tasks.submission.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BadRequestException;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Submission exceptions")
class SubmissionExceptionsTest {

    @Test
    @DisplayName("SubmissionNotFoundException — NotFound + 404")
    void submissionNotFound() {
        var ex = new SubmissionNotFoundException("sub-abc");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.SUBMISSION_NOT_FOUND);
        assertThat(ex.getMessage()).contains("sub-abc");
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("AssignmentPastDueException — Conflict + 409")
    void assignmentPastDue() {
        var ex = new AssignmentPastDueException();
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.ASSIGNMENT_PAST_DUE);
        assertThat(ex.getMessage()).contains("past");
        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("ResubmissionNotAllowedException — Conflict + 409")
    void resubmissionNotAllowed() {
        var ex = new ResubmissionNotAllowedException();
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.RESUBMISSION_NOT_ALLOWED);
        assertThat(ex.getMessage()).contains("re-submission");
        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("GradeOutOfRangeException — BadRequest + 400")
    void gradeOutOfRange() {
        var ex = new GradeOutOfRangeException(150);
        assertThat(ex).isInstanceOf(BadRequestException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.GRADE_OUT_OF_RANGE);
        assertThat(ex.getMessage()).contains("150");
        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("NotGuardianOfStudentException — Forbidden + 403")
    void notGuardian() {
        UUID parent = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        var ex = new NotGuardianOfStudentException(parent, student);
        assertThat(ex).isInstanceOf(ForbiddenException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.NOT_GUARDIAN_OF_STUDENT);
        assertThat(ex.getMessage()).contains(parent.toString()).contains(student.toString());
        assertThat(ex.getStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("StudentNotEnrolledInSectionException — Business + 422")
    void studentNotEnrolled() {
        UUID student = UUID.randomUUID();
        UUID section = UUID.randomUUID();
        var ex = new StudentNotEnrolledInSectionException(student, section);
        assertThat(ex).isInstanceOf(BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.STUDENT_NOT_ENROLLED_IN_SECTION);
        assertThat(ex.getMessage()).contains(student.toString()).contains(section.toString());
        assertThat(ex.getStatus().value()).isEqualTo(422);
    }
}