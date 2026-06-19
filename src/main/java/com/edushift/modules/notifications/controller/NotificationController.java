package com.edushift.modules.notifications.controller;

import com.edushift.modules.notifications.dto.NotificationResponse;
import com.edushift.modules.notifications.dto.UnreadCountResponse;
import com.edushift.modules.notifications.dto.UpdatePreferenceRequest;
import com.edushift.modules.notifications.dto.UpdateTemplateRequest;
import com.edushift.modules.notifications.entity.Notification;
import com.edushift.modules.notifications.entity.NotificationPreference;
import com.edushift.modules.notifications.entity.NotificationTemplate;
import com.edushift.modules.notifications.service.NotificationService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification REST controller (Sprint 9 / BE-9.1).
 *
 * <p>Two surfaces:</p>
 * <ul>
 *   <li><b>User-facing</b> (any authenticated user): list, mark read, prefs.</li>
 *   <li><b>Admin</b> (TENANT_ADMIN): list + edit templates.</li>
 * </ul>
 *
 * <h3>Tenant isolation</h3>
 * The current user's id is taken from the JWT (never from a path
 * param or body). Queries are auto-scoped via Hibernate
 * {@code @TenantId}. Mark-read is double-checked by the JPQL
 * {@code WHERE recipient_user_id = :userId} clause (defense in depth).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    private UUID me() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new com.edushift.shared.exception.UnauthorizedException("Authentication required"));
    }

    // ----------------------------------------------------------------
    // User-facing
    // ----------------------------------------------------------------

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        UUID me = me();
        Page<Notification> page = notificationService.listForUser(me, unreadOnly, pageable);
        List<NotificationResponse> data = page.getContent().stream()
                .map(n -> NotificationResponse.from(
                        n,
                        n.getTemplateKey() + " · " + n.getSentAt(),
                        n.getPayload()))
                .toList();
        return ApiResponse.ok(data, ApiResponse.Meta.of(page));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        UUID me = me();
        return ApiResponse.ok(new UnreadCountResponse(notificationService.countUnread(me)));
    }

    @PatchMapping("/{publicUuid}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID publicUuid) {
        UUID me = me();
        boolean ok = notificationService.markRead(publicUuid, me);
        return ok
                ? ResponseEntity.ok(ApiResponse.ok(null))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Integer> markAllRead() {
        UUID me = me();
        return ApiResponse.ok(notificationService.markAllRead(me));
    }

    // ----------------------------------------------------------------
    // Preferences (FE-9.4)
    // ----------------------------------------------------------------

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<NotificationPreference>> getPreferences() {
        UUID me = me();
        return ApiResponse.ok(notificationService.getPreferences(me));
    }

    @PostMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationPreference> setPreference(
            @Valid @RequestBody UpdatePreferenceRequest req) {
        UUID me = me();
        return ApiResponse.ok(notificationService.setPreference(
                me, req.channel(), req.category(), req.enabled()));
    }

    // ----------------------------------------------------------------
    // Templates (admin only) — FE-9.4 admin tab
    // ----------------------------------------------------------------

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('LMS_NOTIFICATIONS_MANAGE')")
    public ApiResponse<List<NotificationTemplate>> listTemplates() {
        return ApiResponse.ok(notificationService.listTemplates());
    }

    @PatchMapping("/templates/{publicUuid}")
    @PreAuthorize("hasAuthority('LMS_NOTIFICATIONS_MANAGE')")
    public ApiResponse<NotificationTemplate> updateTemplate(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody UpdateTemplateRequest req) {
        return ApiResponse.ok(notificationService.updateTemplate(
                publicUuid, req.subject(), req.bodyHtml()));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------
}
