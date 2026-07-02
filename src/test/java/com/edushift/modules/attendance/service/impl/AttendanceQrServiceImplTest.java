package com.edushift.modules.attendance.service.impl;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.edushift.modules.attendance.repository.StudentAttendanceQrRepository;
import com.edushift.modules.attendance.service.QrTokenService;
import com.edushift.modules.attendance.audit.AttendanceAuditLogger;
import com.edushift.modules.students.repository.StudentRepository;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class AttendanceQrServiceImplTest {
    @Mock StudentRepository studentRepo; @Mock StudentAttendanceQrRepository qrRepo;
    @Mock QrTokenService qrTokenService; @Mock CurrentUserProvider currentUser; @Mock AttendanceAuditLogger auditLogger;
    @InjectMocks AttendanceQrServiceImpl service;
    @Test void getOrIssueThrowsForMissingStudent() { when(studentRepo.findByPublicUuid(any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> service.getOrIssueQr(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class); }
}
