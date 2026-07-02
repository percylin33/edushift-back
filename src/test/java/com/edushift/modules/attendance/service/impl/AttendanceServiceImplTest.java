package com.edushift.modules.attendance.service.impl;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.edushift.modules.attendance.dto.CreateSessionRequest;
import com.edushift.modules.attendance.entity.AttendanceSession;
import com.edushift.modules.attendance.entity.AttendanceSessionSlot;
import com.edushift.modules.attendance.entity.AttendanceSessionStatus;
import com.edushift.modules.attendance.exception.SessionAlreadyOpenException;
import com.edushift.modules.attendance.exception.SessionClosedException;
import com.edushift.modules.attendance.mapper.AttendanceMapper;
import com.edushift.modules.attendance.repository.AttendanceRecordRepository;
import com.edushift.modules.attendance.repository.AttendanceSessionRepository;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.AttendanceUserCache;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.shared.security.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {
    @Mock AttendanceSessionRepository sessionRepo; @Mock AttendanceRecordRepository recordRepo; @Mock StudentAttendanceQrRepository qrRepo;
    @Mock AttendanceMapper mapper; @Mock AttendanceUserCache userCache; @Mock QrTokenService qrTokenService; @Mock CurrentUserProvider currentUser;
    @Mock com.edushift.modules.academic.section.repository.SectionRepository sectionRepo;
    @Mock com.edushift.modules.students.repository.StudentRepository studentRepo;
    @Mock com.edushift.modules.students.enrollments.repository.StudentEnrollmentRepository enrollmentRepo;
    @Mock com.edushift.modules.attendance.audit.AttendanceAuditLogger auditLogger;
    @Mock com.edushift.modules.tenants.service.TenantSettingsService tenantSettings;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock com.edushift.modules.attendance.events.AttendanceEventPublisher realtimePublisher;
    @InjectMocks AttendanceServiceImpl service;
    @BeforeEach void setUp() { when(currentUser.currentTenantId()).thenReturn(Optional.of(UUID.randomUUID())); }
    @Test void openSessionThrowsWhenOpen() { var s = new AttendanceSession(); s.setStatus(AttendanceSessionStatus.ACTIVE); when(sectionRepo.findByPublicUuid(any())).thenReturn(Optional.of(new com.edushift.modules.academic.section.entity.Section())); when(sessionRepo.findActiveBySectionDaySlot(any(), any(), any())).thenReturn(Optional.of(s)); assertThatThrownBy(() -> service.openSession(new CreateSessionRequest(UUID.randomUUID(), LocalDate.now(), AttendanceSessionSlot.MORNING, Instant.now(), null))).isInstanceOf(SessionAlreadyOpenException.class); }
    @Test void closeSessionThrowsWhenClosed() { var s = new AttendanceSession(); s.setStatus(AttendanceSessionStatus.CLOSED); when(sessionRepo.findByPublicUuid(any())).thenReturn(Optional.of(s)); assertThatThrownBy(() -> service.closeSession(UUID.randomUUID())).isInstanceOf(SessionClosedException.class); }
}
