package com.edushift.modules.notifications.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.modules.notifications.repository.NotificationPreferenceRepository;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notificationRepo; @Mock NotificationTemplateRepository templateRepo;
    @Mock NotificationPreferenceRepository preferenceRepo; @Mock EmailOutboxRepository outboxRepo;
    @Mock NotificationTemplateEngine engine; @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @InjectMocks NotificationService service;
    @BeforeEach void setUp() { TenantContext.set(UUID.randomUUID()); }
    @AfterEach void tearDown() { TenantContext.clear(); }
    @Test void countUnread() { when(notificationRepo.countUnreadByRecipient(any())).thenReturn(3L); assertThat(service.countUnread(UUID.randomUUID())).isEqualTo(3L); }
}
