package com.edushift.modules.notifications.controller;

import com.edushift.modules.notifications.dto.AnnouncementResponse;
import com.edushift.modules.notifications.dto.CreateAnnouncementRequest;
import com.edushift.modules.notifications.service.AnnouncementService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Announcement REST controller (Sprint 9 / BE-9.4).
 *
 * <p>Mounted at {@code /api/v1/announcements}. Two surfaces:</p>
 * <ul>
 *   <li><b>User</b> ({@code isAuthenticated()}): list recent published,
 *       get one, mark read.</li>
 *   <li><b>Admin</b> ({@code LMS_ANNOUNCEMENTS_CREATE}): create, update,
 *       publish, delete.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;
    private final CurrentUserProvider currentUserProvider;

    private UUID me() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    // ---------------- User surface ----------------

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AnnouncementResponse>> listPublished(
            @RequestParam(defaultValue = "20") int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        List<AnnouncementResponse> data = service.listPublishedRecent(safe).stream()
                .map(AnnouncementResponse::from)
                .toList();
        return ApiResponse.ok(data);
    }

    @GetMapping("/{publicUuid}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AnnouncementResponse> get(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(AnnouncementResponse.from(service.get(publicUuid)));
    }

    @PostMapping("/{publicUuid}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID publicUuid) {
        boolean ok = service.markRead(publicUuid, me());
        return ok
                ? ResponseEntity.ok(ApiResponse.ok(null))
                : ResponseEntity.notFound().build();
    }

    // ---------------- Admin surface ----------------

    @PostMapping
    @PreAuthorize("hasAuthority('LMS_ANNOUNCEMENTS_CREATE')")
    public ApiResponse<AnnouncementResponse> create(
            @Valid @RequestBody CreateAnnouncementRequest req) {
        var a = service.create(me(), req.title(), req.bodyHtml(),
                req.audienceType(), req.audienceIds(),
                req.pinned(), req.publishAt());
        return ApiResponse.ok(AnnouncementResponse.from(a));
    }

    @PatchMapping("/{publicUuid}")
    @PreAuthorize("hasAuthority('LMS_ANNOUNCEMENTS_CREATE')")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody CreateAnnouncementRequest req) {
        var a = service.update(publicUuid, req.title(), req.bodyHtml(),
                req.audienceType(), req.audienceIds(), req.pinned());
        return ApiResponse.ok(AnnouncementResponse.from(a));
    }

    @PostMapping("/{publicUuid}/publish")
    @PreAuthorize("hasAuthority('LMS_ANNOUNCEMENTS_CREATE')")
    public ApiResponse<AnnouncementResponse> publish(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(AnnouncementResponse.from(service.publish(publicUuid)));
    }

    @DeleteMapping("/{publicUuid}")
    @PreAuthorize("hasAuthority('LMS_ANNOUNCEMENTS_CREATE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID publicUuid) {
        service.delete(publicUuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('LMS_ANNOUNCEMENTS_CREATE')")
    public ApiResponse<List<AnnouncementResponse>> listForAdmin(Pageable pageable) {
        Page<com.edushift.modules.notifications.entity.Announcement> page =
                service.listForAdmin(pageable);
        return ApiResponse.ok(
                page.getContent().stream().map(AnnouncementResponse::from).toList(),
                ApiResponse.Meta.of(page));
    }
}
