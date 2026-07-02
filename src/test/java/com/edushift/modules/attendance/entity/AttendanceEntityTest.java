package com.edushift.modules.attendance.entity;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class AttendanceEntityTest {
    @Test void sessionSetters() { var s = new AttendanceSession(); s.setOccurredOn(LocalDate.now()); s.setSlot(AttendanceSessionSlot.MORNING); s.setStatus(AttendanceSessionStatus.ACTIVE); assertThat(s.getSlot()).isEqualTo(AttendanceSessionSlot.MORNING); }
    @Test void recordSetters() { var r = new AttendanceRecord(); r.setStatus(AttendanceRecordStatus.LATE); r.setOccurredAt(Instant.now()); assertThat(r.getStatus()).isEqualTo(AttendanceRecordStatus.LATE); }
    @Test void qrSetters() { var q = new StudentAttendanceQr(); q.setTokenHash("abc123"); q.setIssuedAt(Instant.now()); assertThat(q.getTokenHash()).isEqualTo("abc123"); assertThat(q.isActive()).isTrue(); }
}
