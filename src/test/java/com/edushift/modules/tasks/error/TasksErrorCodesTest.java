package com.edushift.modules.tasks.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TasksErrorCodes")
class TasksErrorCodesTest {

    @Test
    @DisplayName("task-level error codes")
    void taskLevelCodes() {
        assertThat(TasksErrorCodes.TASK_NOT_FOUND).isEqualTo("TASK_NOT_FOUND");
        assertThat(TasksErrorCodes.SECTION_NOT_FOUND).isEqualTo("SECTION_NOT_FOUND");
        assertThat(TasksErrorCodes.DUE_AT_IN_PAST).isEqualTo("DUE_AT_IN_PAST");
        assertThat(TasksErrorCodes.RECORD_EMPTY_PATCH).isEqualTo("RECORD_EMPTY_PATCH");
    }

    @Test
    @DisplayName("submission-level error codes")
    void submissionLevelCodes() {
        assertThat(TasksErrorCodes.SUBMISSION_NOT_FOUND).isEqualTo("SUBMISSION_NOT_FOUND");
        assertThat(TasksErrorCodes.ASSIGNMENT_PAST_DUE).isEqualTo("ASSIGNMENT_PAST_DUE");
        assertThat(TasksErrorCodes.RESUBMISSION_NOT_ALLOWED).isEqualTo("RESUBMISSION_NOT_ALLOWED");
        assertThat(TasksErrorCodes.GRADE_OUT_OF_RANGE).isEqualTo("GRADE_OUT_OF_RANGE");
        assertThat(TasksErrorCodes.NOT_GUARDIAN_OF_STUDENT).isEqualTo("NOT_GUARDIAN_OF_STUDENT");
        assertThat(TasksErrorCodes.STUDENT_NOT_ENROLLED_IN_SECTION).isEqualTo("STUDENT_NOT_ENROLLED_IN_SECTION");
        assertThat(TasksErrorCodes.INCONSISTENT_PAYLOAD).isEqualTo("INCONSISTENT_PAYLOAD");
    }

    @Test
    @DisplayName("utility class — private constructor, not instantiable")
    void utilityClass() throws Exception {
        Constructor<TasksErrorCodes> ctor = TasksErrorCodes.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}