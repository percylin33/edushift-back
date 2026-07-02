package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UnauthorizedExceptionTest {

    @Test
    @DisplayName("creates exception with UNAUTHORIZED status and default code")
    void createsWithDefaultCode() {
        var ex = new UnauthorizedException("Authentication required");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(ex.getMessage()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("creates exception with custom code")
    void createsWithCustomCode() {
        var ex = new UnauthorizedException("TOKEN_EXPIRED", "Token has expired");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("TOKEN_EXPIRED");
        assertThat(ex.getMessage()).isEqualTo("Token has expired");
    }

    @Test
    @DisplayName("creates exception with custom code and cause")
    void createsWithCustomCodeAndCause() {
        var cause = new RuntimeException("root");
        var ex = new UnauthorizedException("TOKEN_INVALID", "Token is invalid", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getCode()).isEqualTo("TOKEN_INVALID");
        assertThat(ex.getMessage()).isEqualTo("Token is invalid");
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new UnauthorizedException("X", "msg", null);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null message in single-arg constructor")
    void createsWithNullMessage() {
        var ex = new UnauthorizedException((String) null);
        assertThat(ex.getMessage()).isNull();
        assertThat(ex.getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("creates exception with null code")
    void createsWithNullCode() {
        var ex = new UnauthorizedException(null, "msg");
        assertThat(ex.getCode()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new UnauthorizedException("msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
