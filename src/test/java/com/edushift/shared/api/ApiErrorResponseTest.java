package com.edushift.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    @DisplayName("of(message, errors, path, traceId) creates error response with error list")
    void ofMessageErrorsPathTraceId() {
        var errors = List.of(
                ApiError.of("REQUIRED", "name", "Name is required"),
                ApiError.of("REQUIRED", "email", "Email is required"));
        var response = ApiErrorResponse.of("Validation failed", errors, "/api/v1/students", "abc-123");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Validation failed");
        assertThat(response.errors()).hasSize(2);
        assertThat(response.errors().get(0).field()).isEqualTo("name");
        assertThat(response.errors().get(1).field()).isEqualTo("email");
        assertThat(response.path()).isEqualTo("/api/v1/students");
        assertThat(response.traceId()).isEqualTo("abc-123");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("of(message, error, path, traceId) wraps single error in list")
    void ofMessageErrorPathTraceId() {
        var error = ApiError.of("NOT_FOUND", "student", "Student not found");
        var response = ApiErrorResponse.of("Not found", error, "/api/v1/students/42", "trace-1");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Not found");
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0).code()).isEqualTo("NOT_FOUND");
        assertThat(response.path()).isEqualTo("/api/v1/students/42");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("success is always false for error responses")
    void successIsAlwaysFalse() {
        var err = ApiError.of("ERR", "msg");
        var response = ApiErrorResponse.of("msg", err, null, null);
        assertThat(response.success()).isFalse();
    }

    @Test
    @DisplayName("timestamp is populated with current time")
    void timestampIsPopulated() {
        var before = Instant.now().minusSeconds(1);
        var response = ApiErrorResponse.of("msg", ApiError.of("ERR", "msg"), null, null);
        var after = Instant.now().plusSeconds(1);
        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("path and traceId can be null")
    void pathAndTraceIdMayBeNull() {
        var err = ApiError.of("ERR", "msg");
        var response = ApiErrorResponse.of("msg", err, null, null);
        assertThat(response.path()).isNull();
        assertThat(response.traceId()).isNull();
    }
}
