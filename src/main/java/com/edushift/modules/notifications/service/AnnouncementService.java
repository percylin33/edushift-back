package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.entity.Announcement;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import com.edushift.modules.notifications.entity.Announcement.Status;
import com.edushift.modules.notifications.entity.AnnouncementRecipient;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.repository.AnnouncementRecipientRepository;
import com.edushift.modules.notifications.repository.AnnouncementRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcement service (Sprint 9 / BE-9.4).
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Author saves DRAFT.</li>
 *   <li>Author calls {@link #publish(UUID)}: audience is resolved,
 *       recipient rows inserted, status → PUBLISHED, in-app
 *       notifications fanned out via
 *       {@link NotificationService#notifyAll(List)}.</li>
 *   <li>Recipients see the announcement in the bell (category
 *       ANNOUNCEMENT) and call {@link #markRead(UUID, UUID)} to clear
 *       the unread flag.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository announcementRepo;
    private final AnnouncementRecipientRepository recipientRepo;
    private final AnnouncementAudienceResolver audienceResolver;
    private final NotificationService notificationService;
    private final NotificationTemplateEngine engine;

    @Transactional
    public Announcement create(UUID authorUserId, String title, String bodyHtml,
                                AudienceType audienceType, List<String> audienceIds,
                                boolean pinned, Instant publishAt) {
        Announcement a = new Announcement();
        a.setTenantId(TenantContext.currentRequired());
        a.setAuthorUserId(authorUserId);
        a.setTitle(title);
        a.setBodyHtml(engine.sanitizeBody(bodyHtml));
        a.setAudienceType(audienceType);
        a.setAudienceIds(audienceIds == null ? List.of() : audienceIds);
        a.setPinned(pinned);
        a.setPublishAt(publishAt);
        a.setStatus(publishAt == null ? Status.DRAFT : Status.SCHEDULED);
        return announcementRepo.save(a);
    }

    @Transactional
    public Announcement update(UUID publicUuid, String title, String bodyHtml,
                                AudienceType audienceType, List<String> audienceIds,
                                boolean pinned) {
        Announcement a = mustFind(publicUuid);
        if (a.getStatus() == Status.PUBLISHED) {
            throw new com.edushift.shared.exception.BusinessException(
                    "ANNOUNCEMENT_ALREADY_PUBLISHED",
                    "An already-published announcement cannot be edited");
        }
        a.setTitle(title);
        a.setBodyHtml(engine.sanitizeBody(bodyHtml));
        a.setAudienceType(audienceType);
        a.setAudienceIds(audienceIds == null ? List.of() : audienceIds);
        a.setPinned(pinned);
        return announcementRepo.save(a);
    }

    @Transactional
    public void delete(UUID publicUuid) {
        Announcement a = mustFind(publicUuid);
        a.markDeleted();
        announcementRepo.save(a);
    }

    @Transactional
    public Announcement publish(UUID publicUuid) {
        Announcement a = mustFind(publicUuid);
        if (a.getStatus() == Status.PUBLISHED) {
            throw new com.edushift.shared.exception.BusinessException(
                    "ANNOUNCEMENT_ALREADY_PUBLISHED",
                    "This announcement was already published");
        }
        a.setStatus(Status.PUBLISHED);
        a.setPublishedAt(Instant.now());
        a = announcementRepo.save(a);

        // Resolve audience + insert recipient rows + fan out notifications.
        List<UUID> recipients = audienceResolver.resolve(a);
        log.info("[Announcement] publishing publicUuid={} title='{}' recipients={}",
                a.getPublicUuid(), a.getTitle(), recipients.size());

        Instant now = Instant.now();
        for (UUID uid : recipients) {
            AnnouncementRecipient r = new AnnouncementRecipient();
            r.setTenantId(a.getTenantId());
            r.setAnnouncementId(a.getId());
            r.setUserId(uid);
            r.setDeliveredAt(now);
            recipientRepo.save(r);

            notificationService.notify(
                    NotificationService.NotifyCommand.builder()
                            .recipient(uid)
                            .template("ANNOUNCEMENT")
                            .category(Category.ANNOUNCEMENT)
                            .channel(Channel.BOTH)
                            .put("title", a.getTitle())
                            .put("body", a.getBodyHtml())
                            .put("senderName", a.getAuthorUserId().toString())
                            .put("tenantName", a.getTenantId().toString())
                            .build());
        }
        return a;
    }

    // ---------------- Read APIs ----------------

    @Transactional(readOnly = true)
    public Page<Announcement> listForAdmin(Pageable pageable) {
        return announcementRepo.findAllForAdmin(pageable);
    }

    @Transactional(readOnly = true)
    public List<Announcement> listPublishedRecent(int limit) {
        return announcementRepo.findPublished(
                org.springframework.data.domain.PageRequest.of(0, limit)).getContent();
    }

    @Transactional(readOnly = true)
    public Announcement get(UUID publicUuid) {
        return mustFind(publicUuid);
    }

    @Transactional
    public boolean markRead(UUID announcementPublicUuid, UUID userId) {
        Announcement a = mustFind(announcementPublicUuid);
        return recipientRepo.markRead(a.getId(), userId) > 0;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementRecipient> recipientsFor(UUID userId) {
        return recipientRepo.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public long countUnreadFor(UUID userId) {
        return recipientRepo.findUnreadByUser(userId).size();
    }

    private Announcement mustFind(UUID publicUuid) {
        return announcementRepo.findByPublicUuid(publicUuid)
                .orElseThrow(() -> new com.edushift.shared.exception.NotFoundException(
                        "ANNOUNCEMENT_NOT_FOUND",
                        "Announcement not found in the current tenant"));
    }
}
