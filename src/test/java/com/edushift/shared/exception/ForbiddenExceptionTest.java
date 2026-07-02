package com.edushift.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ForbiddenExceptionTest {

    @Test
    @DisplayName("creates exception with FORBIDDEN status and default code")
    void createsWithDefaultCode() {
        var ex = new ForbiddenException("Access is denied");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
        assertThat(ex.getMessage()).isEqualTo("Access is denied");
    }

    @Test
    @DisplayName("creates exception with custom code")
    void createsWithCustomCode() {
        var ex = new ForbiddenException("INSUFFICIENT_ROLE", "Only admins can delete");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_ROLE");
        assertThat(ex.getMessage()).isEqualTo("Only admins can delete");
    }

    @Test
    @DisplayName("creates exception with null message in single-arg constructor")
    void createsWithNullMessage() {
        var ex = new ForbiddenException((String) null);
        assertThat(ex.getMessage()).isNull();
        assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("creates exception with null code and message")
    void createsWithNullCodeAndMessage() {
        var ex = new ForbiddenException(null, (String) null);
        assertThat(ex.getCode()).isNull();
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    @DisplayName("is an ApiException")
    void isApiException() {
        var ex = new ForbiddenException("msg");
        assertThat(ex).isInstanceOf(ApiException.class);
    }
}
