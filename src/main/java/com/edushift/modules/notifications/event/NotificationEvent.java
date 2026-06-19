package com.edushift.modules.notifications.event;

import com.edushift.modules.notifications.entity.Notification.Category;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event that triggers a notification (Sprint 9 / BE-9.3).
 *
 * <p>Decoupled from the {@code NotificationService} so the source
 * modules (attendance, grading, quizzes, tasks, AI feedback) don't
 * need to know how notifications are dispatched. The
 * {@link NotificationEventListener} consumes these and forwards to
 * the service.</p>
 *
 * <h3>Why events, not direct calls</h3>
 * <ul>
 *   <li>Source modules stay decoupled from notifications.</li>
 *   <li>Future consumers (audit log, real-time socket) can subscribe
 *       to the same event without touching the source.</li>
 *   <li>Event publication is non-blocking; the listener processes
 *       on a separate thread (see {@code @AsyncEventListener}).</li>
 * </ul>
 */
public record NotificationEvent(
        /** Stable identifier of the template to use (e.g. "STUDENT_ABSENT"). */
        String templateKey,
        /** Notification category. */
        Category category,
        /** All users to notify (parent, student, teacher — depends on event). */
        List<Recipient> recipients,
        /** Map of {@code {{key}}} values for template interpolation. */
        Map<String, Object> payload,
        /** Source entity id, for audit / idempotency (e.g. sessionUuid, evaluationUuid). */
        UUID sourceId,
        /** When the event happened (for sorting/audit). */
        Instant occurredAt
) {

    /**
     * Recipient of a notification: the user id + optional email.
     * The email is the SMTP target for EMAIL/BOTH channels.
     */
    public record Recipient(UUID userId, String email) {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String templateKey;
        private Category category;
        private List<Recipient> recipients;
        private Map<String, Object> payload = Map.of();
        private UUID sourceId;
        private Instant occurredAt = Instant.now();

        public Builder templateKey(String key) { this.templateKey = key; return this; }
        public Builder category(Category c) { this.category = c; return this; }
        public Builder recipients(List<Recipient> r) { this.recipients = r; return this; }
        public Builder payload(Map<String, Object> p) { this.payload = p; return this; }
        public Builder sourceId(UUID id) { this.sourceId = id; return this; }
        public Builder occurredAt(Instant t) { this.occurredAt = t; return this; }
        public Builder put(String key, Object value) {
            if (payload.isEmpty()) payload = new java.util.HashMap<>();
            if (payload instanceof java.util.HashMap) {
                ((java.util.HashMap<String, Object>) payload).put(key, value);
            } else {
                java.util.HashMap<String, Object> m = new java.util.HashMap<>(payload);
                m.put(key, value);
                payload = m;
            }
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(
                    templateKey, category, recipients == null ? List.of() : recipients,
                    payload, sourceId, occurredAt);
        }
    }
}
