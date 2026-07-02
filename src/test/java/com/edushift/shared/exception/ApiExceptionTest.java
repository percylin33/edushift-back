package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

    @Test
    @DisplayName("creates exception with status code and message")
    void createsWithStatusCodeAndMessage() {
        var ex = new ApiException(HttpStatus.CONFLICT, "CONFLICT", "msg") {};
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("CONFLICT");
        assertThat(ex.getMessage()).isEqualTo("msg");
    }

    @Test
    @DisplayName("creates exception with cause")
    void createsWithCause() {
        var cause = new RuntimeException("root");
        var ex = new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS", "msg", cause) {};
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("msg");
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new ApiException(HttpStatus.OK, "OK", "msg", null) {};
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null message")
    void createsWithNullMessage() {
        var ex = new ApiException(HttpStatus.BAD_REQUEST, "BAD", null) {};
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("creates exception with null code")
    void createsWithNullCode() {
        var ex = new ApiException(HttpStatus.NOT_FOUND, null, "msg") {};
        assertThat(ex.getCode()).isNull();
    }

    @Test
    @DisplayName("is a RuntimeException")
    void isRuntimeException() {
        var ex = new ApiException(HttpStatus.I_AM_A_TEAPOT, "TEA", "msg") {};
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
