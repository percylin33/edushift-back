package com.edushift.modules.notifications.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.repository.AnnouncementRecipientRepository;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {
    @Mock AnnouncementRepository announcementRepo;
    @Mock AnnouncementRecipientRepository recipientRepo;
    @Mock AnnouncementAudienceResolver audienceResolver;
    @Mock NotificationTemplateEngine engine;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock UserRepository userRepository;
    @Mock TenantRepository tenantRepository;
    @InjectMocks AnnouncementService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n", List.of(
                        new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"),
                        new SimpleGrantedAuthority(LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE))));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create() {
        when(engine.sanitizeBody(any())).thenReturn("b");
        service.create(UUID.randomUUID(), "T", "B", Announcement.AudienceType.SCHOOL, java.util.List.of(), false, null, false);
        verify(announcementRepo).save(any());
    }

    @Test
    void create_rejectsStudentEvenWithAuthority() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "n", List.of(
                        new SimpleGrantedAuthority("ROLE_STUDENT"),
                        new SimpleGrantedAuthority(LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE))));
        assertThatThrownBy(() -> service.create(
                UUID.randomUUID(), "T", "B", Announcement.AudienceType.SCHOOL, List.of(), false, null, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete() {
        var a = new Announcement();
        a.setStatus(Announcement.Status.DRAFT);
        when(announcementRepo.findByPublicUuidAndTenantId(any(), any())).thenReturn(Optional.of(a));
        service.delete(UUID.randomUUID());
        verify(announcementRepo).save(any());
    }
}
