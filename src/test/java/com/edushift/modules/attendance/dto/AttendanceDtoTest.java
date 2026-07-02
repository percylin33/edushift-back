package com.edushift.modules.attendance.dto;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class AttendanceDtoTest {
    @Test void sessionResponse() { var r = new AttendanceSessionResponse(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), AttendanceSessionSlot.MORNING, AttendanceSessionStatus.ACTIVE, Instant.now(), null, null, null, 10L, 2L, 1L, 0L, null, Instant.now(), null); assertThat(r.slot()).isEqualTo(AttendanceSessionSlot.MORNING); }
    @Test void recordResponse() { var r = new AttendanceRecordResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "A", AttendanceRecordStatus.PRESENT, Instant.now(), null, null, null, null, null, Instant.now(), null); assertThat(r.studentFullName()).isEqualTo("A"); }
    @Test void recentSessionItem() { var i = new RecentSessionItem(UUID.randomUUID(), UUID.randomUUID(), "A", LocalDate.now(), AttendanceSessionSlot.MORNING, Instant.now(), 10L, 2L, 1L, 0L, 13L); assertThat(i.totalRecords()).isEqualTo(13L); }
    @Test void dashboardOverview() { var r = DashboardOverviewResponse.empty(); assertThat(r.openSessions()).isZero(); }
    @Test void sessionListItem() { var i = new AttendanceSessionListItemResponse(UUID.randomUUID(), UUID.randomUUID(), "A", "G", LocalDate.now(), AttendanceSessionSlot.AFTERNOON, AttendanceSessionStatus.ACTIVE, Instant.now(), null, 10L, 2L, 1L, 0L, Instant.now(), null); assertThat(i.sectionName()).isEqualTo("A"); }
}
