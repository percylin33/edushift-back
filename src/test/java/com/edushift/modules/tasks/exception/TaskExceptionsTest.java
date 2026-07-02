package com.edushift.modules.tasks.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.error.TasksErrorCodes;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tasks (task-level) exceptions")
class TaskExceptionsTest {

    @Test
    @DisplayName("TaskNotFoundException — code + message + NotFoundException type")
    void taskNotFound() {
        var ex = new TaskNotFoundException("abc-123");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.TASK_NOT_FOUND);
        assertThat(ex.getMessage()).contains("abc-123");
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("SectionNotFoundException — code + message + NotFoundException type")
    void sectionNotFound() {
        var ex = new SectionNotFoundException("sec-xyz");
        assertThat(ex).isInstanceOf(NotFoundException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.SECTION_NOT_FOUND);
        assertThat(ex.getMessage()).contains("sec-xyz");
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("DueAtInPastException — code + message + BusinessException type")
    void dueAtInPast() {
        var ex = new DueAtInPastException();
        assertThat(ex).isInstanceOf(BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.DUE_AT_IN_PAST);
        assertThat(ex.getMessage()).contains("dueAt");
        assertThat(ex.getStatus().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("RecordEmptyPatchException — code + message + BusinessException type")
    void recordEmptyPatch() {
        var ex = new RecordEmptyPatchException();
        assertThat(ex).isInstanceOf(BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(TasksErrorCodes.RECORD_EMPTY_PATCH);
        assertThat(ex.getMessage()).contains("PATCH");
        assertThat(ex.getStatus().value()).isEqualTo(422);
    }
}