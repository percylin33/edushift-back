package com.edushift.modules.notifications.service;

import com.edushift.modules.notifications.dto.NotificationResponse;
import com.edushift.modules.notifications.entity.EmailOutbox;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.Notification.Category;
import com.edushift.modules.notifications.entity.Notification.Channel;
import com.edushift.modules.notifications.entity.Notification.Status;
import com.edushift.modules.notifications.entity.NotificationPreference;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.email.EmailBrandingContext;
import com.edushift.modules.notifications.email.EmailBrandingResolver;
import com.edushift.modules.notifications.email.EmailLayoutRenderer;
import com.edushift.modules.notifications.fcm.FcmSender;
import com.edushift.modules.notifications.repository.EmailOutboxRepository;
import com.edushift.modules.notifications.repository.NotificationPreferenceRepository;
import com.edushift.modules.notifications.repository.NotificationRepository;
import com.edushift.modules.notifications.repository.NotificationTemplateRepository;
import com.edushift.modules.students.provision.SyntheticEmailFactory;
import com.edushift.shared.multitenancy.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RecipientEmailResolver recipientEmailResolver;

    /**
     * Sprint cierre-C / B8 -- FCM push notifications. Injected
     * {@code required=false} because the bean only exists when
     * {@code app.integrations.firebase.enabled=true}; otherwise push
     * silently no-ops.
     */
    @Autowired(required = false)
    private FcmSender fcmSender;

    @Autowired(required = false)
    private EmailLayoutRenderer emailLayoutRenderer;

    @Autowired(required = false)
    private EmailBrandingResolver emailBrandingResolver;

    @org.springframework.beans.factory.annotation.Value("${app.notifications.email.enabled:false}")
    private boolean emailEnabled;

    /**
     * Notify a single recipient about an event. Returns the
     * {@link Notification#getPublicUuid()} (so the caller can audit),
     * or {@code Optional.empty()} if the event was fully suppressed
     * (e.g. user opted out of every channel).
     */
    @Transactional
    public Optional<UUID> notify(NotifyCommand cmd) {
        List<UUID> ids = notifyAll(cmd == null ? List.of() : List.of(cmd));
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /**
     * Notify many recipients in a single transaction (bulk). Used by
     * the attendance and grade hooks (1 teacher marks 30 students
     * absent → 30 emails). One template read per distinct key, one
     * preference read per category, and one user read for addresses
     * the commands did not already carry.
     */
    @Transactional
    public List<UUID> notifyAll(List<NotifyCommand> cmds) {
        if (cmds == null || cmds.isEmpty()) {
            return List.of();
        }
        UUID tenantId = TenantContext.currentRequired();
        Map<UUID, String> lookedUpEmails = recipientEmailResolver.resolveEmails(usersNeedingEmail(cmds));
        Map<String, NotificationTemplate> templates = loadTemplatesForSend(distinctKeys(cmds));
        Map<Category, Map<UUID, EnumMap<Channel, Boolean>>> optedOut = loadOptOuts(cmds);

        List<PendingDelivery> pending = new ArrayList<>();
        for (NotifyCommand cmd : cmds) {
            Map<UUID, EnumMap<Channel, Boolean>> byUser =
                    optedOut.getOrDefault(cmd.category(), Map.of());
            boolean inAppOptOut = optedOut(byUser, cmd.recipientUserId(), Channel.IN_APP);
            boolean emailOptOut = optedOut(byUser, cmd.recipientUserId(), Channel.EMAIL);
            if (inAppOptOut && emailOptOut) {
                log.debug("[Notify] user={} opted out of all channels for category={}, skipping",
                        cmd.recipientUserId(), cmd.category());
                continue;
            }

            NotificationTemplate template = templates.get(cmd.templateKey());
            if (template == null) {
                log.warn("[Notify] no template for tenant={} key={}; recording FAILED audit row",
                        tenantId, cmd.templateKey());
                pending.add(PendingDelivery.failed(buildFailed(tenantId, cmd)));
                continue;
            }

            String payloadJson = serializePayload(cmd.payload());
            NotificationTemplateEngine.Rendered rendered = engine.render(template, payloadJson);
            Channel channel = resolveChannel(inAppOptOut, emailOptOut, cmd.preferredChannel());
            Notification n = new Notification();
            n.setTenantId(tenantId);
            n.setRecipientUserId(cmd.recipientUserId());
            n.setTemplateKey(cmd.templateKey());
            n.setCategory(cmd.category());
            n.setChannel(channel);
            n.setPayload(payloadJson);
            n.setStatus(inAppOptOut ? Status.SKIPPED : Status.SENT);
            n.setSentAt(java.time.Instant.now());
            assignPublicUuid(n);

            String toEmail = null;
            boolean enqueue = false;
            if ((channel == Channel.EMAIL || channel == Channel.BOTH) && !emailOptOut) {
                toEmail = deliverableEmail(cmd.recipientEmail(), lookedUpEmails.get(cmd.recipientUserId()));
                if (toEmail != null) {
                    if (!emailEnabled) {
                        log.warn("[Notify] SMTP disabled (app.notifications.email.enabled=false) — "
                                        + "skipping email_outbox for template={} to={}",
                                cmd.templateKey(), toEmail);
                    } else {
                        enqueue = true;
                    }
                }
            }
            pending.add(new PendingDelivery(
                    n, enqueue, toEmail, rendered.subject(), rendered.bodyHtml(), cmd, channel, emailOptOut));
        }

        if (pending.isEmpty()) {
            return List.of();
        }

        EmailBrandingContext branding = brandingFor(tenantId, pending);
        List<Notification> rows = pending.stream().map(PendingDelivery::notification).toList();
        notificationRepo.saveAll(rows);

        List<EmailOutbox> outbox = new ArrayList<>();
        for (PendingDelivery delivery : pending) {
            if (!delivery.enqueueEmail()) {
                continue;
            }
            String bodyHtml = delivery.bodyHtml();
            if (emailLayoutRenderer != null) {
                bodyHtml = emailLayoutRenderer.wrap(bodyHtml, null, branding);
            }
            EmailOutbox row = new EmailOutbox();
            row.setTenantId(tenantId);
            row.setNotificationId(delivery.notification().getId());
            row.setToEmail(delivery.toEmail());
            row.setSubject(delivery.subject());
            row.setBodyHtml(bodyHtml);
            outbox.add(row);
        }
        if (!outbox.isEmpty()) {
            outboxRepo.saveAll(outbox);
        }

        for (PendingDelivery delivery : pending) {
            if (delivery.failed() || fcmSender == null || delivery.emailOptOut()) {
                continue;
            }
            NotifyCommand cmd = delivery.command();
            Map<String, String> data = new HashMap<>();
            data.put("notificationPublicUuid", delivery.notification().getPublicUuid().toString());
            data.put("templateKey", cmd.templateKey());
            data.put("category", cmd.category().name());
            data.put("channel", delivery.channel().name());
            fcmSender.send(cmd.recipientUserId(), delivery.subject(), delivery.bodyHtml(), data);
        }

        return pending.stream().map(d -> d.notification().getPublicUuid()).toList();
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
    // Rendering helpers
    // ------------------------------------------------------------------

    /**
     * Render a page with one template query for the distinct keys.
     * Missing keys fall back to the in-memory system seed, same text as
     * a single-notification render.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> toResponses(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        Map<String, NotificationTemplate> templates = loadTemplatesForRender(distinctKeysOf(notifications));
        List<NotificationResponse> responses = new ArrayList<>(notifications.size());
        for (Notification n : notifications) {
            NotificationTemplate template = n.getTemplateKey() == null
                    ? null
                    : templates.get(n.getTemplateKey());
            String subject;
            String body;
            if (template == null) {
                subject = fallbackSubject(n);
                body = "";
            } else {
                NotificationTemplateEngine.Rendered rendered = engine.render(template, n.getPayload());
                subject = rendered.subject();
                body = rendered.bodyHtml();
            }
            responses.add(NotificationResponse.from(n, subject, body));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public String renderSubject(Notification n) {
        if (n == null) return null;
        return toResponses(List.of(n)).get(0).subject();
    }

    @Transactional(readOnly = true)
    public String renderBody(Notification n) {
        if (n == null) return "";
        return toResponses(List.of(n)).get(0).bodyHtml();
    }

    /** Persist missing built-in templates for the current tenant (idempotent). */
    private void ensureSystemTemplates() {
        UUID tenantId = TenantContext.currentRequired();
        for (NotificationTemplate seed : com.edushift.modules.notifications.seed.NotificationTemplateSeed.all("es-PE")) {
            boolean exists = templateRepo
                    .findByKeyAndLocale(seed.getTemplateKey(), seed.getLocale())
                    .isPresent();
            if (exists) {
                continue;
            }
            NotificationTemplate row = new NotificationTemplate();
            row.setTenantId(tenantId);
            row.setTemplateKey(seed.getTemplateKey());
            row.setLocale(seed.getLocale());
            row.setSubject(seed.getSubject());
            row.setBodyHtml(seed.getBodyHtml());
            row.setSystem(true);
            row.setVersion(1);
            templateRepo.save(row);
            log.info("[Notify] seeded missing template key={} tenant={}",
                    seed.getTemplateKey(), tenantId);
        }
    }

    private String fallbackSubject(Notification n) {
        Map<String, Object> payload = NotificationResponse.parsePayload(n.getPayload());
        String detail = firstPayloadString(payload,
                "quizTitle", "taskTitle", "evaluationTitle", "title", "invoiceNumber");
        String base = switch (n.getTemplateKey() == null ? "" : n.getTemplateKey()) {
            case "QUIZ_PUBLISHED" -> "Nueva práctica disponible";
            case "GRADE_PUBLISHED" -> "Nueva calificación publicada";
            case "TASK_RETURNED" -> "Tu tarea fue devuelta";
            case "TASK_PUBLISHED" -> "Nueva tarea disponible";
            case "AI_FEEDBACK_READY" -> "Retroalimentación disponible";
            case "STUDENT_ABSENT" -> "Ausencia registrada";
            case "PAYMENT_DUE" -> "Pago pendiente";
            case "PAYMENT_EVIDENCE_SUBMITTED" -> "Voucher por revisar";
            case "PAYMENT_EVIDENCE_REJECTED" -> "Voucher rechazado";
            case "PAYMENT_RECEIVED" -> "Pago registrado";
            case "PAYMENT_OVERDUE" -> "Cuota vencida";
            case "PAYMENT_REMINDER" -> "Recordatorio de pago";
            case "ANNOUNCEMENT" -> "Anuncio";
            case "TEACHER_ASSIGNED" -> "Nueva asignación académica";
            case "SECTION_NEW_TEACHER" -> "Nuevo docente en tu sección";
            case "PASSWORD_RESET" -> "Restablece tu contraseña";
            case "WELCOME_TENANT" -> "Bienvenido a EduShift";
            default -> "Notificación";
        };
        if (detail != null && !detail.isBlank()) {
            return base + " — " + detail;
        }
        return base;
    }

    private static String firstPayloadString(Map<String, Object> payload, String... keys) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object v = payload.get(key);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
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

    private static Channel resolveChannel(boolean inAppOptOut, boolean emailOptOut, Channel preferred) {
        boolean inApp = !inAppOptOut;
        boolean email = !emailOptOut;
        if (preferred == Channel.IN_APP) {
            return Channel.IN_APP;
        }
        if (preferred == Channel.EMAIL) {
            return Channel.EMAIL;
        }
        if (inApp && email) return Channel.BOTH;
        if (inApp) return Channel.IN_APP;
        if (email) return Channel.EMAIL;
        return preferred == null ? Channel.IN_APP : preferred;
    }

    private List<UUID> usersNeedingEmail(List<NotifyCommand> cmds) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (NotifyCommand cmd : cmds) {
            if (cmd.preferredChannel() == Channel.IN_APP || cmd.recipientUserId() == null) {
                continue;
            }
            if (deliverableEmail(cmd.recipientEmail(), null) != null) {
                continue;
            }
            ids.add(cmd.recipientUserId());
        }
        return List.copyOf(ids);
    }

    private static String deliverableEmail(String explicit, String lookedUp) {
        if (explicit != null && !explicit.isBlank()
                && !SyntheticEmailFactory.isSyntheticAddress(explicit.trim())) {
            return explicit.trim();
        }
        if (lookedUp == null || lookedUp.isBlank()
                || SyntheticEmailFactory.isSyntheticAddress(lookedUp.trim())) {
            return null;
        }
        return lookedUp.trim();
    }

    private static Set<String> distinctKeys(List<NotifyCommand> cmds) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (NotifyCommand cmd : cmds) {
            if (cmd.templateKey() != null && !cmd.templateKey().isBlank()) {
                keys.add(cmd.templateKey());
            }
        }
        return keys;
    }

    private static Set<String> distinctKeysOf(List<Notification> notifications) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Notification n : notifications) {
            if (n.getTemplateKey() != null && !n.getTemplateKey().isBlank()) {
                keys.add(n.getTemplateKey());
            }
        }
        return keys;
    }

    private Map<String, NotificationTemplate> loadTemplatesForSend(Set<String> keys) {
        Map<String, NotificationTemplate> found = persistedTemplates(keys);
        List<String> missing = keys.stream().filter(key -> !found.containsKey(key)).toList();
        if (missing.isEmpty()) {
            return found;
        }
        ensureSystemTemplates();
        found.putAll(persistedTemplates(new LinkedHashSet<>(missing)));
        return found;
    }

    private Map<String, NotificationTemplate> loadTemplatesForRender(Set<String> keys) {
        Map<String, NotificationTemplate> found = new HashMap<>(persistedTemplates(keys));
        for (String key : keys) {
            if (found.containsKey(key)) {
                continue;
            }
            inMemorySeed(key).ifPresent(seed -> found.put(key, seed));
        }
        return found;
    }

    private Map<String, NotificationTemplate> persistedTemplates(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, NotificationTemplate> found = new HashMap<>();
        for (NotificationTemplate template : templateRepo.findAllByTemplateKeyInAndLocale(keys, "es-PE")) {
            if (template.getId() != null && template.getTemplateKey() != null) {
                found.put(template.getTemplateKey(), template);
            }
        }
        return found;
    }

    private static Optional<NotificationTemplate> inMemorySeed(String templateKey) {
        return com.edushift.modules.notifications.seed.NotificationTemplateSeed.all("es-PE")
                .stream()
                .filter(t -> templateKey.equals(t.getTemplateKey()))
                .findFirst();
    }

    private Map<Category, Map<UUID, EnumMap<Channel, Boolean>>> loadOptOuts(List<NotifyCommand> cmds) {
        Map<Category, LinkedHashSet<UUID>> usersByCategory = new HashMap<>();
        for (NotifyCommand cmd : cmds) {
            if (cmd.category() == null || cmd.recipientUserId() == null) {
                continue;
            }
            usersByCategory
                    .computeIfAbsent(cmd.category(), ignored -> new LinkedHashSet<>())
                    .add(cmd.recipientUserId());
        }
        Map<Category, Map<UUID, EnumMap<Channel, Boolean>>> optedOut = new HashMap<>();
        for (Map.Entry<Category, LinkedHashSet<UUID>> entry : usersByCategory.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            Map<UUID, EnumMap<Channel, Boolean>> byUser = new HashMap<>();
            for (NotificationPreference pref : preferenceRepo.findAllByUserIdInAndCategory(
                    entry.getValue(), entry.getKey())) {
                byUser.computeIfAbsent(pref.getUserId(), ignored -> new EnumMap<>(Channel.class))
                        .put(pref.getChannel(), !pref.isEnabled());
            }
            optedOut.put(entry.getKey(), byUser);
        }
        return optedOut;
    }

    private static boolean optedOut(
            Map<UUID, EnumMap<Channel, Boolean>> byUser, UUID userId, Channel channel) {
        EnumMap<Channel, Boolean> channels = byUser.get(userId);
        return channels != null && Boolean.TRUE.equals(channels.get(channel));
    }

    private EmailBrandingContext brandingFor(UUID tenantId, List<PendingDelivery> pending) {
        boolean anyEmail = pending.stream().anyMatch(PendingDelivery::enqueueEmail);
        if (!anyEmail || emailLayoutRenderer == null || emailBrandingResolver == null) {
            return null;
        }
        return emailBrandingResolver.forTenantId(tenantId);
    }

    private static void assignPublicUuid(Notification n) {
        if (n.getPublicUuid() == null) {
            n.setPublicUuid(UUID.randomUUID());
        }
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

    private Notification buildFailed(UUID tenantId, NotifyCommand cmd) {
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientUserId(cmd.recipientUserId());
        n.setTemplateKey(cmd.templateKey());
        n.setCategory(cmd.category());
        n.setChannel(Channel.IN_APP);
        n.setPayload(serializePayload(cmd.payload()));
        n.setStatus(Status.FAILED);
        n.setErrorCode("TEMPLATE_NOT_FOUND");
        n.setErrorMessage("Template " + cmd.templateKey() + " not found in this tenant");
        assignPublicUuid(n);
        return n;
    }

    private record PendingDelivery(
            Notification notification,
            boolean enqueueEmail,
            String toEmail,
            String subject,
            String bodyHtml,
            NotifyCommand command,
            Channel channel,
            boolean emailOptOut
    ) {
        static PendingDelivery failed(Notification notification) {
            return new PendingDelivery(notification, false, null, null, null, null, null, true);
        }

        boolean failed() {
            return notification.getStatus() == Status.FAILED;
        }
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
