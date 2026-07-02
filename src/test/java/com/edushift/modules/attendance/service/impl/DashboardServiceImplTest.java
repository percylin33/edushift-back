package com.edushift.modules.attendance.service.impl;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.exception.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.sql.DataSource;
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
    @Mock CurrentUserProvider currentUser; @Mock com.edushift.modules.attendance.repository.AttendanceSessionRepository sessionRepo;
    @Mock com.edushift.modules.attendance.repository.AttendanceRecordRepository recordRepo; @Mock DataSource dataSource;
    @InjectMocks DashboardServiceImpl service;
    @Test void throwsWhenNoTenant() { when(currentUser.currentTenantId()).thenReturn(Optional.empty()); assertThatThrownBy(() -> service.getOverview()).isInstanceOf(UnauthorizedException.class); }
}
