package com.edushift.modules.notifications.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
import com.edushift.modules.notifications.repository.AnnouncementRecipientRepository;
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
class AnnouncementServiceTest {
    @Mock AnnouncementRepository announcementRepo; @Mock AnnouncementRecipientRepository recipientRepo;
    @Mock AnnouncementAudienceResolver audienceResolver; @Mock NotificationService notificationService;
    @Mock NotificationTemplateEngine engine;
    @InjectMocks AnnouncementService service;
    @BeforeEach void setUp() { TenantContext.set(UUID.randomUUID()); }
    @AfterEach void tearDown() { TenantContext.clear(); }
    @Test void create() { when(engine.sanitizeBody(any())).thenReturn("b"); service.create(UUID.randomUUID(), "T", "B", Announcement.AudienceType.SCHOOL, java.util.List.of(), false, null); verify(announcementRepo).save(any()); }
    @Test void delete() { var a = new Announcement(); a.setStatus(Announcement.Status.DRAFT); when(announcementRepo.findByPublicUuidAndTenantId(any(), any())).thenReturn(Optional.of(a)); service.delete(UUID.randomUUID()); verify(announcementRepo).save(any()); }
}
