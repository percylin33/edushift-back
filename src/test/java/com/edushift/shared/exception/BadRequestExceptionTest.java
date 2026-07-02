package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BadRequestExceptionTest {

    @Test
    @DisplayName("creates exception with BAD_REQUEST status and given code")
    void createsWithStatusAndCode() {
        var ex = new BadRequestException("INVALID_INPUT", "Input is invalid");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("INVALID_INPUT");
        assertThat(ex.getMessage()).isEqualTo("Input is invalid");
    }

    @Test
    @DisplayName("creates exception with cause")
    void createsWithCause() {
        var cause = new RuntimeException("root");
        var ex = new BadRequestException("INVALID_INPUT", "Input is invalid", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new BadRequestException("NULL_CAUSE", "msg", null);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null code")
    void createsWithNullCode() {
        var ex = new BadRequestException(null, "msg");
        assertThat(ex.getCode()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new BadRequestException("X", "msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
