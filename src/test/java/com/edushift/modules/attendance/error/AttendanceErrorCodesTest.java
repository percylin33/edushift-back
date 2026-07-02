package com.edushift.modules.attendance.error;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class AttendanceErrorCodesTest {
    @Test void constantsExist() { assertThat(AttendanceErrorCodes.SESSION_CLOSED).isEqualTo("SESSION_CLOSED"); assertThat(AttendanceErrorCodes.SESSION_ALREADY_OPEN).isEqualTo("SESSION_ALREADY_OPEN"); assertThat(AttendanceErrorCodes.QR_INVALID).isEqualTo("QR_INVALID"); assertThat(AttendanceErrorCodes.QR_EXPIRED).isEqualTo("QR_EXPIRED"); }
}
