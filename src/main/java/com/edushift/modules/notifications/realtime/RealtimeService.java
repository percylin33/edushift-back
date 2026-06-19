package com.edushift.modules.notifications.realtime;

import com.edushift.modules.notifications.entity.Notification;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Realtime push service (Sprint 10 / BE-10.4, ADR-10.3, ADR-10.4).
 *
 * <p>Thin wrapper over Spring's {@link SimpMessagingTemplate} that
 * enforces the channel naming convention:</p>
 *
 * <pre>
 *   /topic/tenant/{tenantId}                  → tenant-wide broadcast (admin dashboards)
 *   /topic/tenant/{tenantId}/user/{userId}    → per-user push (the bell)
 * </pre>
 *
 * <p>The FE client subscribes to
 * {@code /topic/tenant/{currentTenantId}/user/{currentUserId}} as
 * soon as it authenticates. We never broadcast to a global topic —
 * multi-tenant safety.</p>
 *
 * <h3>Why STOMP over plain WebSocket</h3>
 * Topics, headers, and per-user auth (via the WebSocket handshake's
 * {@code Authorization} header) come for free. The FE library
 * {@code @stomp/stompjs} is 12KB gzipped.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeService {

    private final SimpMessagingTemplate broker;

    /**
     * Push a notification to a single user.
     */
    public void pushToUser(Notification n) {
        UUID tenantId = n.getTenantId();
        UUID userId = n.getRecipientUserId();
        NotificationPushPayload payload = new NotificationPushPayload(
                n.getPublicUuid(),
                userId,
                n.getTemplateKey(),
                n.getCategory(),
                n.getChannel(),
                null, // subject is computed at send time; for the bell we keep the payload lean
                n.getCreatedAt() == null ? java.time.Instant.now() : n.getCreatedAt()
        );
        String dest = userTopic(tenantId, userId);
        broker.convertAndSend(dest, payload);
        log.debug("[Realtime] pushed notification {} to {}", n.getPublicUuid(), dest);
    }

    /**
     * Broadcast to a whole tenant (e.g. "the AI chat quota was
     * just exceeded globally"). Use sparingly — most notifications
     * go to a single user.
     */
    public void pushToTenant(UUID tenantId, Object payload) {
        String dest = tenantTopic(tenantId);
        broker.convertAndSend(dest, payload);
        log.debug("[Realtime] pushed to {}: {}", dest, payload);
    }

    public static String userTopic(UUID tenantId, UUID userId) {
        return "/topic/tenant/" + tenantId + "/user/" + userId;
    }

    public static String tenantTopic(UUID tenantId) {
        return "/topic/tenant/" + tenantId;
    }

    /**
     * Resolve the current tenant id at push time. The call site
     * (e.g. NotificationEventListener) may run AFTER_COMMIT, when
     * the TenantContext has already been cleared; we accept the
     * tenant id from the notification row instead.
     */
    public static UUID currentTenantOr(Notification n) {
        return TenantContext.current().orElse(n.getTenantId());
    }
}
