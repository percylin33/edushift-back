package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BusinessExceptionTest {

    @Test
    @DisplayName("creates exception with UNPROCESSABLE_ENTITY status")
    void createsWithStatus() {
        var ex = new BusinessException("DUPLICATE_EMAIL", "Email already exists");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getCode()).isEqualTo("DUPLICATE_EMAIL");
        assertThat(ex.getMessage()).isEqualTo("Email already exists");
    }

    @Test
    @DisplayName("creates exception with cause")
    void createsWithCause() {
        var cause = new RuntimeException("root");
        var ex = new BusinessException("DUPLICATE_EMAIL", "Email already exists", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new BusinessException("X", "msg", null);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null message")
    void createsWithNullMessage() {
        var ex = new BusinessException("X", null);
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new BusinessException("X", "msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
