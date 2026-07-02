package com.edushift.modules.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.dto.CreateTaskRequest;
import com.edushift.modules.tasks.dto.TaskResponse;
import com.edushift.modules.tasks.dto.TaskSummary;
import com.edushift.modules.tasks.dto.UpdateTaskRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@DisplayName("TaskService contract")
class TaskServiceTest {

    @Test
    @DisplayName("interface declares the five public operations")
    void exposesContract() throws NoSuchMethodException {
        assertThat(TaskService.class.isInterface()).isTrue();

        Method create = TaskService.class.getMethod(
                "create", UUID.class, CreateTaskRequest.class, UUID.class);
        Method list = TaskService.class.getMethod(
                "listBySection", UUID.class, Pageable.class);
        Method get = TaskService.class.getMethod(
                "getByPublicUuid", UUID.class);
        Method patch = TaskService.class.getMethod(
                "patch", UUID.class, UpdateTaskRequest.class);
        Method delete = TaskService.class.getMethod(
                "delete", UUID.class);

        assertThat(create.getReturnType()).isEqualTo(TaskResponse.class);
        assertThat(list.getReturnType()).isEqualTo(Page.class);
        assertThat(list.getReturnType().getMethods()).extracting(m -> m.getName())
                .contains("map", "getContent");
        assertThat(get.getReturnType()).isEqualTo(TaskResponse.class);
        assertThat(patch.getReturnType()).isEqualTo(TaskResponse.class);
        assertThat(delete.getReturnType()).isEqualTo(void.class);

        // Reference for compile-time type assertions
        TaskSummary ts = new TaskSummary(UUID.randomUUID(), "t",
                Instant.now(), false, UUID.randomUUID(), Instant.now());
        assertThat(ts.title()).isEqualTo("t");
    }
}