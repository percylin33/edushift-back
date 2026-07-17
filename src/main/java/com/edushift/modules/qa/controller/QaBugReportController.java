package com.edushift.modules.qa.controller;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.qa.QaBugReport;
import com.edushift.modules.qa.dto.CreateBugReportRequest;
import com.edushift.modules.qa.dto.QaBugReportResponse;
import com.edushift.modules.qa.dto.UpdateBugReportStatusRequest;
import com.edushift.modules.qa.service.QaBugReportService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.UnauthorizedException;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QA bug reports endpoints for the Centro de Pruebas ({@code /help}) FE wizard.
 *
 * <h3>Why these endpoints exist</h3>
 * <p>The wizard runs steps against the real backend with the role of the
 * authenticated user. When a step fails the user can persist a structured
 * report so the team can triage. These endpoints are the back-half of that
 * flow (the FE already shows the failure inline before asking for confirmation).</p>
 *
 * <h3>Tenant isolation</h3>
 * <ul>
 *   <li>{@code POST /bug-reports} — tenant comes from session; NULL for SA.</li>
 *   <li>{@code GET /bug-reports} — non-SA sees only its own reports; SA sees
 *       the tenant-scoped subset.</li>
 *   <li>{@code PATCH /bug-reports/{publicUuid}/status} — only owner or SA.</li>
 * </ul>
 */
@RestController
@RequestMapping("/qa/bug-reports")
@Validated
@RequiredArgsConstructor
@Tag(name = "QA Bug Reports", description = "Bug reports created from /help wizard (Sprint 17)")
public class QaBugReportController {

    private static final int MAX_PAGE_SIZE = 200;

    private final QaBugReportService service;
    private final CurrentUserProvider currentUser;
    private final UserRepository userRepository;

    /**
     * Create a bug report from a failed step. Rate-limited at 30/min/IP
     * via interceptor (see {@code SimpleRateLimiter}).
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a QA bug report for a failed /help wizard step")
    public ApiResponse<QaBugReportResponse> create(@Valid @RequestBody CreateBugReportRequest body) {
        QaBugReportService.CreateCommand cmd = new QaBugReportService.CreateCommand(
                body.capabilityId(),
                body.stepId(),
                body.stepLabel(),
                body.severity(),
                body.notes(),
                body.request());
        return ApiResponse.ok(service.create(cmd));
    }

    /**
     * List bug reports. Non-SA actors only see their own; SA actors see
     * the tenant-scoped subset (NULL tenant = cross-tenant SA).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List bug reports (own for non-SA, tenant for SA)")
    public ApiResponse<Page<QaBugReportResponse>> list(
            @RequestParam(required = false) String capabilityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID requesterId = currentUser.currentUserId()
                .map(this::resolveInternalUserId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_REQUIRED", "Authentication required."));
        int pageIndex = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);

        return ApiResponse.ok(
                service.search(capabilityId, status, requesterId,
                        PageRequest.of(pageIndex, pageSize)));
    }

    @PatchMapping("/{publicUuid}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change bug report status (OPEN/ACKNOWLEDGED/RESOLVED)")
    public ApiResponse<QaBugReportResponse> updateStatus(
            @PathVariable("publicUuid") UUID publicUuid,
            @Valid @RequestBody UpdateBugReportStatusRequest body) {
        return ApiResponse.ok(service.updateStatus(publicUuid, body.status()));
    }

    private UUID resolveInternalUserId(UUID publicUuid) {
        return userRepository.findByPublicUuid(publicUuid)
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(
                        "USER_NOT_FOUND",
                        "Authenticated user no longer exists."));
    }
}