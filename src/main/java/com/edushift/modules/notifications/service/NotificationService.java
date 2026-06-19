package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.entity.EmailOutbox;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import com.edushift.modules.notifications.entity.NotificationPreference;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationPreferenceRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification service (Sprint 9 / BE-9.1).
 *
 * <p>The single entry point for creating notifications. All domain
 * modules (attendance, evaluations, quizzes, tasks, AI) call
 * {@link #notify(NotifyCommand)} when an event happens. This service:</p>
 * <ol>
 *   <li>Resolves the template (per tenant + locale).</li>
 *   <li>Enforces the recipient's preferences (skip if opted out).</li>
 *   <li>Persists a {@link Notification} row (in-app history).</li>
 *   <li>Enqueues an {@link EmailOutbox} row if the channel includes
 *       {@code EMAIL}.</li>
 * </ol>
 *
 * <h3>Outbox pattern (ADR-9.1)</h3>
 * The actual {@code JavaMailSender.send()} call happens in
 * {@code EmailOutboxProcessor} on a separate thread ({@code @Scheduled}).
 * This keeps the caller (e.g. the attendance endpoint) fast and
 * lets us retry without blocking the user.
 *
 * <h3>Multi-tenant</h3>
 * Reads {@code tenantId} from {@link TenantContext}. Templates,
 * notifications, preferences, and outbox are all auto-scoped via
 * Hibernate {@code @TenantId}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final NotificationTemplateRepository templateRepo;
    private final NotificationPreferenceRepository preferenceRepo;
    private final EmailOutboxRepository outboxRepo;
    private final NotificationTemplateEngine engine;
    private final ObjectMapper objectMapper;

    /**
     * Notify a single recipient about an event. Returns the
     * {@link Notification#getPublicUuid()} (so the caller can audit),
     * or {@code Optional.empty()} if the event was fully suppressed
     * (e.g. user opted out of every channel).
     */
    @Transactional
    public Optional<UUID> notify(NotifyCommand cmd) {
        UUID tenantId = TenantContext.currentRequired();

        // 1) Check preferences. If opted out of BOTH channels, skip.
        if (isOptedOut(cmd.recipientUserId(), Channel.IN_APP, cmd.category())
                && isOptedOut(cmd.recipientUserId(), Channel.EMAIL, cmd.category())) {
            log.debug("[Notify] user={} opted out of all channels for category={}, skipping",
                    cmd.recipientUserId(), cmd.category());
            return Optional.empty();
        }

        // 2) Resolve the template.
        NotificationTemplate template = templateRepo
                .findByKeyAndLocale(cmd.templateKey(), "es-PE")
                .orElse(null);
        if (template == null) {
            log.warn("[Notify] no template for tenant={} key={}; recording FAILED audit row",
                    tenantId, cmd.templateKey());
            Notification failed = persistFailed(tenantId, cmd, "TEMPLATE_NOT_FOUND",
                    "Template " + cmd.templateKey() + " not found in this tenant");
            return Optional.of(failed.getPublicUuid());
        }

        // 3) Render subject + body (interpolate payload).
        String payloadJson = serializePayload(cmd.payload());
        NotificationTemplateEngine.Rendered rendered = engine.render(template, payloadJson);

        // 4) Persist the in-app notification row.
        Channel channel = resolveChannel(cmd.recipientUserId(), cmd.category(), cmd.preferredChannel());
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientUserId(cmd.recipientUserId());
        n.setTemplateKey(cmd.templateKey());
        n.setCategory(cmd.category());
        n.setChannel(channel);
        n.setPayload(payloadJson);
        n.setStatus(isOptedOut(cmd.recipientUserId(), Channel.IN_APP, cmd.category())
                ? Status.SKIPPED : Status.SENT);
        n.setSentAt(java.time.Instant.now());
        n = notificationRepo.save(n);

        // 5) If the channel includes EMAIL (and not opted-out), enqueue.
        if ((channel == Channel.EMAIL || channel == Channel.BOTH)
                && !isOptedOut(cmd.recipientUserId(), Channel.EMAIL, cmd.category())) {
            String toEmail = cmd.recipientEmail();
            if (toEmail != null && !toEmail.isBlank()) {
                EmailOutbox row = new EmailOutbox();
                row.setTenantId(tenantId);
                row.setNotificationId(n.getId());
                row.setToEmail(toEmail);
                row.setSubject(rendered.subject());
                row.setBodyHtml(rendered.bodyHtml());
                outboxRepo.save(row);
            }
        }
        return Optional.of(n.getPublicUuid());
    }

    /**
     * Notify many recipients in a single transaction (bulk). Used by
     * the attendance and grade hooks (1 teacher marks 30 students
     * absent → 30 emails).
     */
    @Transactional
    public List<UUID> notifyAll(List<NotifyCommand> cmds) {
        return cmds.stream()
                .map(this::notify)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Same as {@link #notifyAll} but returns the actual {@link Notification}
     * rows. Used by the realtime layer (Sprint 10 / BE-10.4) so we
     * can push the full payload via STOMP without a second query.
     */
    @Transactional
    public List<Notification> notifyAllAndReturnRows(List<NotifyCommand> cmds) {
        List<UUID> publicUuids = notifyAll(cmds);
        if (publicUuids.isEmpty()) return List.of();
        return notificationRepo.findAllByPublicUuidIn(publicUuids);
    }

    // ------------------------------------------------------------------
    // Read APIs (used by the controller)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Notification> listForUser(
            UUID userId, boolean unreadOnly, org.springframework.data.domain.Pageable pageable) {
        return unreadOnly
                ? notificationRepo.findUnreadByRecipient(userId, pageable)
                : notificationRepo.findByRecipient(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepo.countUnreadByRecipient(userId);
    }

    @Transactional
    public boolean markRead(UUID publicUuid, UUID userId) {
        return notificationRepo.markRead(publicUuid, userId) > 0;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepo.markAllRead(userId);
    }

    // ------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationPreference> getPreferences(UUID userId) {
        return preferenceRepo.findAllByUser(userId);
    }

    @Transactional
    public NotificationPreference setPreference(UUID userId, Channel channel, Category category, boolean enabled) {
        NotificationPreference pref = preferenceRepo
                .findByUserIdAndChannelAndCategory(userId, channel, category)
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setTenantId(TenantContext.currentRequired());
                    p.setUserId(userId);
                    p.setChannel(channel);
                    p.setCategory(category);
                    return p;
                });
        pref.setEnabled(enabled);
        return preferenceRepo.save(pref);
    }

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationTemplate> listTemplates() {
        return templateRepo.findAllOrdered();
    }

    @Transactional
    public NotificationTemplate updateTemplate(UUID publicUuid, String newSubject, String newBodyHtml) {
        NotificationTemplate t = templateRepo.findAll().stream()
                .filter(x -> x.getPublicUuid().equals(publicUuid))
                .findFirst()
                .orElseThrow(() -> new com.edushift.modules.notifications.exception.NotificationTemplateNotFoundException());
        t.setSubject(engine.sanitizeSubject(newSubject));
        t.setBodyHtml(engine.sanitizeBody(newBodyHtml));
        return templateRepo.saveWithVersionBump(t);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Default-enabled rule: if no row exists for the triple, treat as enabled.
     * This matches the "default = everything on" UX.
     */
    private boolean isOptedOut(UUID userId, Channel channel, Category category) {
        return preferenceRepo.findByUserIdAndChannelAndCategory(userId, channel, category)
                .map(p -> !p.isEnabled())
                .orElse(false);
    }

    private Channel resolveChannel(UUID userId, Category category, Channel preferred) {
        boolean inApp = !isOptedOut(userId, Channel.IN_APP, category);
        boolean email = !isOptedOut(userId, Channel.EMAIL, category);
        if (inApp && email) return Channel.BOTH;
        if (inApp) return Channel.IN_APP;
        if (email) return Channel.EMAIL;
        // both opted out — caller should have already short-circuited
        return preferred == null ? Channel.IN_APP : preferred;
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("[Notify] failed to serialize payload, using empty: {}", e.getMessage());
            return "{}";
        }
    }

    private Notification persistFailed(UUID tenantId, NotifyCommand cmd, String code, String msg) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientUserId(cmd.recipientUserId());
        n.setTemplateKey(cmd.templateKey());
        n.setCategory(cmd.category());
        n.setChannel(Channel.IN_APP);
        n.setPayload(serializePayload(cmd.payload()));
        n.setStatus(Status.FAILED);
        n.setErrorCode(code);
        n.setErrorMessage(msg);
        return notificationRepo.save(n);
    }

    /**
     * Command to {@link #notify(NotifyCommand)}.
     *
     * @param recipientUserId   who receives the notification.
     * @param recipientEmail    SMTP target (resolved by the caller via the
     *                          users table; pass null if the channel is IN_APP).
     * @param templateKey       stable identifier (e.g. {@code "STUDENT_ABSENT"}).
     * @param category          the category (used for preference lookup).
     * @param payload           JSON-serializable map; keys are interpolated
     *                          in the template via {@code {{key}}}.
     * @param preferredChannel  hint for the channel; if the user is opted-out
     *                          of one, the other is used. Defaults to BOTH.
     */
    public record NotifyCommand(
            UUID recipientUserId,
            String recipientEmail,
            String templateKey,
            Category category,
            Map<String, Object> payload,
            Channel preferredChannel
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private UUID recipientUserId;
            private String recipientEmail;
            private String templateKey;
            private Category category;
            private Map<String, Object> payload = Map.of();
            private Channel preferredChannel = Channel.BOTH;

            public Builder recipient(UUID userId) { this.recipientUserId = userId; return this; }
            public Builder email(String email)   { this.recipientEmail = email; return this; }
            public Builder template(String key)  { this.templateKey = key; return this; }
            public Builder category(Category c)  { this.category = c; return this; }
            public Builder payload(Map<String, Object> p) { this.payload = p; return this; }
            public Builder put(String key, Object value) {
                if (this.payload == null || this.payload.isEmpty()) {
                    this.payload = new java.util.HashMap<>();
                }
                if (this.payload instanceof java.util.HashMap) {
                    ((java.util.HashMap<String, Object>) this.payload).put(key, value);
                } else {
                    java.util.Map<String, Object> m = new java.util.HashMap<>(this.payload);
                    m.put(key, value);
                    this.payload = m;
                }
                return this;
            }
            public Builder channel(Channel c) { this.preferredChannel = c; return this; }

            public NotifyCommand build() {
                return new NotifyCommand(recipientUserId, recipientEmail, templateKey,
                        category, payload == null ? Map.of() : payload, preferredChannel);
            }
        }
    }
}
