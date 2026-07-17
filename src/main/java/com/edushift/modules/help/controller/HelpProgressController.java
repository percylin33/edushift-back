package com.edushift.modules.help.controller;

import com.edushift.modules.help.dto.CreateFeedbackRequest;
import com.edushift.modules.help.dto.HelpFeedbackResponse;
import com.edushift.modules.help.dto.HelpProgressItem;
import com.edushift.modules.help.dto.SetProgressRequest;
import com.edushift.modules.help.service.HelpProgressService;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated endpoints for help-manual progress and feedback.
 *
 * <p>These endpoints are <strong>not</strong> in {@code PUBLIC_PATHS} —
 * progress and feedback are per-user data and require a session.</p>
 *
 * <h3>Tenant isolation</h3>
 * Tenant id is always taken from the session, never from the request
 * body. Cross-tenant writes are impossible because the WHERE clause
 * always pins on the session tenant.
 */
@RestController
@RequestMapping("/help")
@Validated
@RequiredArgsConstructor
@Tag(name = "Help Progress", description = "Per-user checklist progress + feedback")
public class HelpProgressController {

    private final HelpProgressService service;
    private final CurrentUserProvider currentUser;

    /**
     * List the current user's checked/unchecked items for a chapter.
     */
    @GetMapping("/progress/{role}/{file}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the current user's progress for one chapter")
    public ApiResponse<List<HelpProgressItem>> getProgress(
            @Parameter(description = "Canonical role key", example = "TENANT_ADMIN")
            @PathVariable("role") String role,
            @Parameter(description = "Chapter filename", example = "03-autoevaluacion.md")
            @PathVariable("file") String file) {
        UUID tenantId = requireTenant();
        UUID userId = requireInternalUser();
        return ApiResponse.ok(service.getProgress(tenantId, userId, role, file));
    }

    /**
     * Upsert one checklist item's checked state.
     */
    @PutMapping("/progress/{role}/{file}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set the checked state of one checklist item")
    public ApiResponse<HelpProgressItem> setProgress(
            @PathVariable("role") String role,
            @PathVariable("file") String file,
            @Valid @RequestBody SetProgressRequest body) {
        UUID tenantId = requireTenant();
        UUID userId = requireInternalUser();
        return ApiResponse.ok(service.setProgress(
                tenantId, userId, role, file, body.itemId(), body.checked()));
    }

    /**
     * Clear the row for one item (so the next GET omits it).
     */
    @DeleteMapping("/progress/{role}/{file}/{itemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Clear one item (returns it as unchecked)")
    public ApiResponse<HelpProgressItem> clearProgress(
            @PathVariable("role") String role,
            @PathVariable("file") String file,
            @PathVariable("itemId") String itemId) {
        UUID tenantId = requireTenant();
        UUID userId = requireInternalUser();
        return ApiResponse.ok(service.clearProgress(tenantId, userId, role, file, itemId));
    }

    /**
     * Submit feedback on a manual / chapter. {@code chapterFile} may be
     * null to target the manual as a whole.
     */
    @PostMapping("/feedback")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit feedback on a manual or chapter")
    public ApiResponse<HelpFeedbackResponse> postFeedback(
            @Valid @RequestBody CreateFeedbackRequest body) {
        UUID tenantId = requireTenant();
        UUID userId = requireInternalUser();
        return ApiResponse.ok(service.postFeedback(
                tenantId, userId, body.role(), body.chapterFile(), body.body()));
    }

    /**
     * List the current user's feedback for a given role, newest first.
     */
    @GetMapping("/feedback/{role}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my feedback for one role, newest first")
    public ApiResponse<List<HelpFeedbackResponse>> listMyFeedback(
            @PathVariable("role") String role) {
        UUID tenantId = requireTenant();
        UUID userId = requireInternalUser();
        return ApiResponse.ok(service.listMyFeedback(tenantId, userId, role));
    }

    private UUID requireInternalUser() {
        UUID publicUuid = currentUser.currentUserId().orElseThrow(() ->
                new ForbiddenException("USER_REQUIRED",
                        "Authenticated session has no user id."));
        return service.resolveInternalUserId(publicUuid);
    }

    private UUID requireTenant() {
        return currentUser.currentTenantId().orElseThrow(() ->
                new ForbiddenException("TENANT_REQUIRED",
                        "Authenticated session has no tenant context."));
    }

    private UUID requireUser() {
        return currentUser.currentUserId().orElseThrow(() ->
                new ForbiddenException("USER_REQUIRED",
                        "Authenticated session has no user id."));
    }
}