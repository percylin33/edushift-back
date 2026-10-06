package com.edushift.modules.notifications.service;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.notifications.dto.AnnouncementResponse;
import com.edushift.modules.notifications.dto.AudiencePreviewResponse;
import com.edushift.modules.notifications.dto.TeachingScopeResponse;
import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.modules.notifications.entity.Announcement.Status;
import com.edushift.modules.notifications.entity.AnnouncementRecipient;
import com.edushift.modules.notifications.event.AnnouncementPublishedEvent;
import com.edushift.modules.notifications.repository.AnnouncementRecipientRepository;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
import com.edushift.modules.notifications.service.AnnouncementAudienceResolver.AudienceBreakdown;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.exception.BusinessException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.NotFoundException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.multitenancy.TenantContext;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcement lifecycle: draft, optional schedule, publish (recipient rows
 * + in-app fan-out), archive. Students cannot author. Teachers may only
 * target sections and courses they are assigned to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private static final int RECIPIENT_SAVE_BATCH = 500;

    private final AnnouncementRepository announcementRepo;
    private final AnnouncementRecipientRepository recipientRepo;
    private final AnnouncementAudienceResolver audienceResolver;
    private final NotificationTemplateEngine engine;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public Announcement create(UUID authorUserId, String title, String bodyHtml,
                                AudienceType audienceType, List<String> audienceIds,
                                boolean pinned, Instant publishAt, boolean includeGuardians) {
        rejectStudent();
        enforceAudience(audienceType, audienceIds);
        Announcement a = new Announcement();
        a.setTenantId(TenantContext.currentRequired());
        a.setAuthorUserId(authorUserId);
        a.setTitle(title);
        a.setBodyHtml(engine.sanitizeBody(bodyHtml));
        a.setAudienceType(audienceType);
        a.setAudienceIds(audienceIds == null ? List.of() : audienceIds);
        a.setPinned(pinned);
        a.setIncludeGuardians(includeGuardians);
        a.setPublishAt(publishAt);
        a.setStatus(publishAt == null ? Status.DRAFT : Status.SCHEDULED);
        return announcementRepo.save(a);
    }

    @Transactional
    public Announcement update(UUID publicUuid, String title, String bodyHtml,
                                AudienceType audienceType, List<String> audienceIds,
                                boolean pinned, Instant publishAt, boolean includeGuardians) {
        rejectStudent();
        Announcement a = mustFind(publicUuid);
        enforceCanManage(a);
        if (a.getStatus() == Status.PUBLISHED || a.getStatus() == Status.ARCHIVED) {
            throw new BusinessException(
                    "ANNOUNCEMENT_ALREADY_PUBLISHED",
                    "An already-published announcement cannot be edited");
        }
        enforceAudience(audienceType, audienceIds);
        a.setTitle(title);
        a.setBodyHtml(engine.sanitizeBody(bodyHtml));
        a.setAudienceType(audienceType);
        a.setAudienceIds(audienceIds == null ? List.of() : audienceIds);
        a.setPinned(pinned);
        a.setIncludeGuardians(includeGuardians);
        a.setPublishAt(publishAt);
        if (a.getStatus() != Status.PUBLISHED) {
            a.setStatus(publishAt == null ? Status.DRAFT : Status.SCHEDULED);
        }
        return announcementRepo.save(a);
    }

    @Transactional
    public void delete(UUID publicUuid) {
        rejectStudent();
        Announcement a = mustFind(publicUuid);
        enforceCanManage(a);
        a.markDeleted();
        announcementRepo.save(a);
    }

    @Transactional
    public Announcement publish(UUID publicUuid) {
        rejectStudent();
        Announcement a = mustFind(publicUuid);
        enforceCanManage(a);
        enforceAudience(a.getAudienceType(), a.getAudienceIds());
        return publishResolved(a);
    }

    /** Scheduler entry. No caller, so authoring rules are not re-checked. */
    @Transactional
    public Announcement publishScheduled(UUID publicUuid) {
        return publishResolved(mustFind(publicUuid));
    }

    @Transactional
    public Announcement archive(UUID publicUuid) {
        rejectStudent();
        Announcement a = mustFind(publicUuid);
        enforceCanManage(a);
        if (a.getStatus() != Status.PUBLISHED) {
            throw new BusinessException(
                    "ANNOUNCEMENT_NOT_PUBLISHED",
                    "Only a published announcement can be archived");
        }
        a.setStatus(Status.ARCHIVED);
        return announcementRepo.save(a);
    }

    @Transactional(readOnly = true)
    public AudiencePreviewResponse preview(AudienceType audienceType, List<String> audienceIds,
                                           boolean includeGuardians) {
        rejectStudent();
        enforceAudience(audienceType, audienceIds);
        Announcement probe = new Announcement();
        probe.setAudienceType(audienceType);
        probe.setAudienceIds(audienceIds == null ? List.of() : audienceIds);
        probe.setIncludeGuardians(includeGuardians);
        AudienceBreakdown breakdown = audienceResolver.resolveDetailed(probe);
        return new AudiencePreviewResponse(
                breakdown.students().size(),
                breakdown.teachers().size(),
                breakdown.guardians().size(),
                breakdown.distinct().size());
    }

    @Transactional(readOnly = true)
    public TeachingScopeResponse teachingScope(UUID teacherUserId) {
        rejectStudent();
        if (!teacherScoped()) {
            throw new ForbiddenException(
                    "ANNOUNCEMENT_SCOPE_FORBIDDEN",
                    "Teaching scope is only available to teachers");
        }
        return new TeachingScopeResponse(
                audienceResolver.sectionsForTeacher(teacherUserId),
                audienceResolver.coursesForTeacher(teacherUserId));
    }

    @Transactional(readOnly = true)
    public Page<Announcement> listForAdmin(Status status, UUID authorFilter, Pageable pageable) {
        rejectStudent();
        UUID author = teacherScoped() ? callerId() : authorFilter;
        if (author != null && status != null) {
            return announcementRepo.findForAdminByAuthorAndStatus(author, status, pageable);
        }
        if (author != null) {
            return announcementRepo.findForAdminByAuthor(author, pageable);
        }
        if (status != null) {
            return announcementRepo.findForAdminByStatus(status, pageable);
        }
        return announcementRepo.findAllForAdmin(pageable);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listPublishedForUser(UUID userId, int limit) {
        UUID tenantId = TenantContext.currentRequired();
        List<Announcement> rows = announcementRepo.findPublishedForUser(
                tenantId, userId,
                org.springframework.data.domain.PageRequest.of(0, limit)).getContent();
        Map<UUID, Boolean> readById = readFlags(userId, rows);
        return rows.stream()
                .map(a -> AnnouncementResponse.from(a, readById.get(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Announcement getForCaller(UUID publicUuid, UUID userId) {
        Announcement a = mustFind(publicUuid);
        if (!isStudent() && canManage(a, userId)) {
            return a;
        }
        if (a.getStatus() != Status.PUBLISHED) {
            throw notFound();
        }
        if (a.getAudienceType() == AudienceType.SCHOOL) {
            return a;
        }
        if (recipientRepo.existsByAnnouncementIdAndUserId(a.getId(), userId)) {
            return a;
        }
        throw notFound();
    }

    @Transactional
    public boolean markRead(UUID announcementPublicUuid, UUID userId) {
        Announcement a = getForCaller(announcementPublicUuid, userId);
        return recipientRepo.markRead(a.getId(), userId) > 0;
    }

    private Announcement publishResolved(Announcement a) {
        if (a.getStatus() == Status.PUBLISHED) {
            throw new BusinessException(
                    "ANNOUNCEMENT_ALREADY_PUBLISHED",
                    "This announcement was already published");
        }
        if (a.getStatus() == Status.ARCHIVED) {
            throw new BusinessException(
                    "ANNOUNCEMENT_ARCHIVED",
                    "An archived announcement cannot be published");
        }
        a.setStatus(Status.PUBLISHED);
        a.setPublishedAt(Instant.now());
        a = announcementRepo.save(a);

        List<UUID> recipients = audienceResolver.resolve(a);
        log.info("[Announcement] publishing publicUuid={} title='{}' recipients={}",
                a.getPublicUuid(), a.getTitle(), recipients.size());

        Instant now = Instant.now();
        List<AnnouncementRecipient> batch = new ArrayList<>(Math.min(recipients.size(), RECIPIENT_SAVE_BATCH));
        for (UUID uid : recipients) {
            AnnouncementRecipient r = new AnnouncementRecipient();
            r.setTenantId(a.getTenantId());
            r.setAnnouncementId(a.getId());
            r.setUserId(uid);
            r.setDeliveredAt(now);
            batch.add(r);
            if (batch.size() >= RECIPIENT_SAVE_BATCH) {
                recipientRepo.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            recipientRepo.saveAll(batch);
        }

        String senderName = userRepository.findByPublicUuid(a.getAuthorUserId())
                .map(u -> {
                    String name = u.fullName();
                    return name == null || name.isBlank() ? "Colegio" : name;
                })
                .orElse("Colegio");
        String tenantName = tenantRepository.findById(a.getTenantId())
                .map(t -> t.getName() == null ? "" : t.getName())
                .orElse("");

        eventPublisher.publishEvent(new AnnouncementPublishedEvent(
                a.getTenantId(),
                a.getPublicUuid(),
                a.getId(),
                a.getTitle(),
                a.getBodyHtml(),
                excerpt(a.getBodyHtml()),
                senderName,
                tenantName,
                a.getAuthorUserId(),
                List.copyOf(recipients)));
        return a;
    }

    private Map<UUID, Boolean> readFlags(UUID userId, List<Announcement> rows) {
        if (rows.isEmpty()) return Map.of();
        List<UUID> ids = rows.stream().map(Announcement::getId).toList();
        Map<UUID, Boolean> flags = new HashMap<>();
        for (AnnouncementRecipient r : recipientRepo.findByUserAndAnnouncements(userId, ids)) {
            flags.put(r.getAnnouncementId(), r.getReadAt() != null);
        }
        return flags;
    }

    private void enforceAudience(AudienceType type, List<String> audienceIds) {
        if (!teacherScoped()) return;
        audienceResolver.assertTeacherAssigned(callerId(), type, audienceIds);
    }

    private void enforceCanManage(Announcement a) {
        rejectStudent();
        if (teacherScoped()) {
            if (!callerId().equals(a.getAuthorUserId())) {
                throw new ForbiddenException(
                        "ANNOUNCEMENT_NOT_AUTHOR",
                        "Teachers can only manage their own announcements");
            }
            return;
        }
        if (!hasAuthority(LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE)) {
            throw new ForbiddenException(
                    "ANNOUNCEMENT_FORBIDDEN",
                    "You cannot manage announcements");
        }
    }

    private boolean canManage(Announcement a, UUID userId) {
        if (isStudent()) return false;
        if (teacherScoped()) return userId.equals(a.getAuthorUserId());
        return hasAuthority(LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE);
    }

    private void rejectStudent() {
        if (isStudent()) {
            throw new ForbiddenException(
                    "STUDENT_CANNOT_PUBLISH_ANNOUNCEMENTS",
                    "Students cannot create or publish announcements");
        }
    }

    private boolean isStudent() {
        return hasRole(UserRole.STUDENT);
    }

    private boolean teacherScoped() {
        return hasRole(UserRole.TEACHER)
                && !hasRole(UserRole.TENANT_ADMIN)
                && !hasRole(UserRole.SUPER_ADMIN);
    }

    private boolean hasRole(UserRole role) {
        return authorities().stream().anyMatch(a -> ("ROLE_" + role.name()).equals(a));
    }

    private boolean hasAuthority(String authority) {
        return authorities().stream().anyMatch(authority::equals);
    }

    private List<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private UUID callerId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    private Announcement mustFind(UUID publicUuid) {
        return announcementRepo.findByPublicUuidAndTenantId(
                        publicUuid, TenantContext.currentRequired())
                .orElseThrow(this::notFound);
    }

    private NotFoundException notFound() {
        return new NotFoundException(
                "ANNOUNCEMENT_NOT_FOUND",
                "Announcement not found in the current tenant");
    }

    static String excerpt(String html) {
        if (html == null || html.isBlank()) return "";
        String plain = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        plain = plain.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (plain.length() <= 180) return plain;
        return plain.substring(0, 177) + "...";
    }
}
