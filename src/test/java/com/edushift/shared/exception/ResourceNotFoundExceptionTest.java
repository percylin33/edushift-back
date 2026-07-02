package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("creates exception with resource and id")
    void createsWithResourceAndId() {
        var ex = new ResourceNotFoundException("Student", 42);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Student not found with id: 42");
    }

    @Test
    @DisplayName("creates exception with resource and string id")
    void createsWithResourceAndStringId() {
        var ex = new ResourceNotFoundException("Course", "uuid-123");
        assertThat(ex.getMessage()).isEqualTo("Course not found with id: uuid-123");
    }

    @Test
    @DisplayName("creates exception with message")
    void createsWithMessage() {
        var ex = new ResourceNotFoundException("Custom message here");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Custom message here");
    }

    @Test
    @DisplayName("creates exception with null id")
    void createsWithNullId() {
        var ex = new ResourceNotFoundException("Student", null);
        assertThat(ex.getMessage()).isEqualTo("Student not found with id: null");
    }

    @Test
    @DisplayName("creates exception with null resource name")
    void createsWithNullResource() {
        var ex = new ResourceNotFoundException(null, 1);
        assertThat(ex.getMessage()).isEqualTo("null not found with id: 1");
    }

    @Test
    @DisplayName("creates exception with null message")
    void createsWithNullMessage() {
        var ex = new ResourceNotFoundException((String) null);
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new ResourceNotFoundException("X", 1);
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
