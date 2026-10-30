package com.edushift.modules.notifications.controller;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.notifications.dto.AnnouncementResponse;
import com.edushift.modules.notifications.dto.AudiencePreviewRequest;
import com.edushift.modules.notifications.dto.AudiencePreviewResponse;
import com.edushift.modules.notifications.dto.CreateAnnouncementRequest;
import com.edushift.modules.notifications.dto.TeachingScopeResponse;
import com.edushift.modules.notifications.entity.Announcement.Status;
import com.edushift.modules.notifications.service.AnnouncementService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsAuthorities;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
 * Announcements. Readers list and open what their audience includes.
 * Authors (tenant admin, or a teacher limited to their own sections and
 * courses) create, schedule, publish and archive. A student never authors,
 * even if a tenant override granted the authority.
 */
@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private static final String CAN_AUTHOR =
            "hasAnyAuthority('" + LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE + "','"
                    + LmsAuthorities.LMS_ANNOUNCEMENTS_CREATE_OWN + "')";

    private final AnnouncementService service;
    private final CurrentUserProvider currentUserProvider;

    private UUID me() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    private void rejectStudentCaller() {
        if (currentUserProvider.currentUserRole().orElse(null) == UserRole.STUDENT) {
            throw new ForbiddenException(
                    "STUDENT_CANNOT_PUBLISH_ANNOUNCEMENTS",
                    "Students cannot create or publish announcements");
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<java.util.List<AnnouncementResponse>> listPublished(
            @RequestParam(defaultValue = "20") int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        return ApiResponse.ok(service.listPublishedForUser(me(), safe));
    }

    @GetMapping("/admin")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<java.util.List<AnnouncementResponse>> listForAdmin(
            @RequestParam(required = false) Status status,
            Pageable pageable) {
        rejectStudentCaller();
        var page = service.listForAdmin(status, null, pageable);
        return ApiResponse.ok(
                page.getContent().stream().map(AnnouncementResponse::from).toList(),
                ApiResponse.Meta.of(page));
    }

    @GetMapping("/teaching-scope")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<TeachingScopeResponse> teachingScope() {
        rejectStudentCaller();
        return ApiResponse.ok(service.teachingScope(me()));
    }

    @PostMapping("/preview-audience")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<AudiencePreviewResponse> preview(@Valid @RequestBody AudiencePreviewRequest req) {
        rejectStudentCaller();
        return ApiResponse.ok(service.preview(
                req.audienceType(),
                req.audienceIds(),
                Boolean.TRUE.equals(req.includeGuardians())));
    }

    @GetMapping("/{publicUuid}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AnnouncementResponse> get(@PathVariable UUID publicUuid) {
        return ApiResponse.ok(AnnouncementResponse.from(service.getForCaller(publicUuid, me())));
    }

    @PostMapping("/{publicUuid}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID publicUuid) {
        boolean ok = service.markRead(publicUuid, me());
        return ok
                ? ResponseEntity.ok(ApiResponse.ok(null))
                : ResponseEntity.notFound().build();
    }

    @PostMapping
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<AnnouncementResponse> create(@Valid @RequestBody CreateAnnouncementRequest req) {
        rejectStudentCaller();
        var a = service.create(me(), req.title(), req.bodyHtml(),
                req.audienceType(), req.audienceIds(),
                req.pinned(), req.publishAt(),
                Boolean.TRUE.equals(req.includeGuardians()));
        return ApiResponse.ok(AnnouncementResponse.from(a));
    }

    @PatchMapping("/{publicUuid}")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable UUID publicUuid,
            @Valid @RequestBody CreateAnnouncementRequest req) {
        rejectStudentCaller();
        var a = service.update(publicUuid, req.title(), req.bodyHtml(),
                req.audienceType(), req.audienceIds(), req.pinned(), req.publishAt(),
                Boolean.TRUE.equals(req.includeGuardians()));
        return ApiResponse.ok(AnnouncementResponse.from(a));
    }

    @PostMapping("/{publicUuid}/publish")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<AnnouncementResponse> publish(@PathVariable UUID publicUuid) {
        rejectStudentCaller();
        return ApiResponse.ok(AnnouncementResponse.from(service.publish(publicUuid)));
    }

    @PostMapping("/{publicUuid}/archive")
    @PreAuthorize(CAN_AUTHOR)
    public ApiResponse<AnnouncementResponse> archive(@PathVariable UUID publicUuid) {
        rejectStudentCaller();
        return ApiResponse.ok(AnnouncementResponse.from(service.archive(publicUuid)));
    }

    @DeleteMapping("/{publicUuid}")
    @PreAuthorize(CAN_AUTHOR)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID publicUuid) {
        rejectStudentCaller();
        service.delete(publicUuid);
        return ResponseEntity.noContent().build();
    }
}
