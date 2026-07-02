package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GoneExceptionTest {

    @Test
    @DisplayName("creates exception with GONE status")
    void createsWithStatus() {
        var ex = new GoneException("EXPIRED_LINK", "Magic link has expired");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.GONE);
        assertThat(ex.getCode()).isEqualTo("EXPIRED_LINK");
        assertThat(ex.getMessage()).isEqualTo("Magic link has expired");
    }

    @Test
    @DisplayName("creates exception with cause")
    void createsWithCause() {
        var cause = new RuntimeException("root");
        var ex = new GoneException("EXPIRED_LINK", "Magic link has expired", cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.GONE);
        assertThat(ex.getCode()).isEqualTo("EXPIRED_LINK");
    }

    @Test
    @DisplayName("creates exception with null cause")
    void createsWithNullCause() {
        var ex = new GoneException("X", "msg", null);
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("creates exception with null message")
    void createsWithNullMessage() {
        var ex = new GoneException("X", null);
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new GoneException("X", "msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
