package com.edushift.modules.attendance.mapper;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.attendance.entity.AttendanceRecord;
import com.edushift.modules.attendance.entity.AttendanceRecordStatus;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class AttendanceMapperTest {
    private final AttendanceMapper mapper = new AttendanceMapper();
    @Test void toSessionResponse() { var s = stub(); var r = mapper.toResponse(s); assertThat(r.slot()).isEqualTo(AttendanceSessionSlot.MORNING); }
    @Test void toSessionResponseNull() { assertThat(mapper.toResponse(null)).isNull(); }
    @Test void toResponseWithCounts() { var s = stub(); assertThat(mapper.toResponseWithCounts(s, 10L, 2L, 1L, 0L, Map.of()).presentCount()).isEqualTo(10L); }
    @Test void toListItem() { var s = stub(); assertThat(mapper.toListItem(s, 10L, 2L, 1L, 0L).presentCount()).isEqualTo(10L); }
    @Test void toRecordResponse() { var rec = new AttendanceRecord(); rec.setPublicUuid(UUID.randomUUID()); rec.setStatus(AttendanceRecordStatus.PRESENT); rec.setOccurredAt(java.time.Instant.now()); assertThat(mapper.toResponse(rec, Map.of()).studentPublicUuid()).isNull(); }
    private static AttendanceSession stub() { var s = new AttendanceSession(); s.setPublicUuid(UUID.randomUUID()); s.setOccurredOn(java.time.LocalDate.now()); s.setSlot(AttendanceSessionSlot.MORNING); s.setStatus(AttendanceSessionStatus.ACTIVE); s.setStartsAt(java.time.Instant.now()); return s; }
}
