package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotFoundExceptionTest {

    @Test
    @DisplayName("creates exception with NOT_FOUND status")
    void createsWithStatus() {
        var ex = new NotFoundException("RESOURCE_NOT_FOUND", "Student not found");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("Student not found");
    }

    @Test
    @DisplayName("creates exception with null code")
    void createsWithNullCode() {
        var ex = new NotFoundException(null, "msg");
        assertThat(ex.getCode()).isNull();
    }

    @Test
    @DisplayName("creates exception with null message")
    void createsWithNullMessage() {
        var ex = new NotFoundException("CODE", null);
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new NotFoundException("X", "msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
