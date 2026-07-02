package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ConflictExceptionTest {

    @Test
    @DisplayName("creates exception with CONFLICT status")
    void createsWithStatus() {
        var ex = new ConflictException("DUPLICATE_KEY", "Resource already exists");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("DUPLICATE_KEY");
        assertThat(ex.getMessage()).isEqualTo("Resource already exists");
    }

    @Test
    @DisplayName("creates exception with cause")
    void createsWithCause() {
        var cause = new RuntimeException("root");
        var ex = new ConflictException("DUPLICATE_KEY", "Resource already exists", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("DUPLICATE_KEY");
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new ConflictException("X", "msg", null);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null code")
    void createsWithNullCode() {
        var ex = new ConflictException(null, "msg");
        assertThat(ex.getCode()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new ConflictException("X", "msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
