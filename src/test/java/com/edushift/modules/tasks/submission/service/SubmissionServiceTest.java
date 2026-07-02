package com.edushift.modules.tasks.submission.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.tasks.submission.dto.CreateSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.GradeSubmissionRequest;
import com.edushift.modules.tasks.submission.dto.SubmissionResponse;
import com.edushift.modules.tasks.submission.dto.SubmissionSummary;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@DisplayName("SubmissionService contract")
class SubmissionServiceTest {

    @Test
    @DisplayName("interface declares the four public operations")
    void exposesContract() throws NoSuchMethodException {
        assertThat(SubmissionService.class.isInterface()).isTrue();

        Method submit = SubmissionService.class.getMethod(
                "submit", UUID.class, CreateSubmissionRequest.class, UUID.class);
        Method list = SubmissionService.class.getMethod(
                "listByTask", UUID.class, Pageable.class);
        Method getMine = SubmissionService.class.getMethod(
                "getMine", UUID.class, UUID.class);
        Method grade = SubmissionService.class.getMethod(
                "grade", UUID.class, GradeSubmissionRequest.class, UUID.class);

        assertThat(submit.getReturnType()).isEqualTo(SubmissionResponse.class);
        assertThat(list.getReturnType()).isEqualTo(Page.class);
        assertThat(getMine.getReturnType()).isEqualTo(SubmissionResponse.class);
        assertThat(grade.getReturnType()).isEqualTo(SubmissionResponse.class);

        // Reference for compile-time type assertions
        SubmissionSummary ss = new SubmissionSummary(
                UUID.randomUUID(), UUID.randomUUID(), null, null, false, null);
        assertThat(ss.hasAttachment()).isFalse();
    }
}