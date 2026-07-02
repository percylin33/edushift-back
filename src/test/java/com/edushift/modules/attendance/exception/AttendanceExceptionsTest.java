package com.edushift.modules.attendance.exception;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class AttendanceExceptionsTest {
    @Test void all() { assertThat(new SessionAlreadyOpenException("x")).hasMessageContaining("x"); assertThat(new SessionClosedException("x")).hasMessageContaining("x"); assertThat(new EditWindowExpiredException("x")).hasMessageContaining("x"); assertThat(new QrExpiredException("x")).hasMessageContaining("x"); assertThat(new QrInvalidException("x")).hasMessageContaining("x"); assertThat(new ForcedStatusForbiddenException("x")).hasMessageContaining("x"); assertThat(new StudentNotEnrolledException("x")).hasMessageContaining("x"); assertThat(new StudentNoActiveEnrollmentException("x")).hasMessageContaining("x"); }
}
