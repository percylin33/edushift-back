package com.edushift.modules.admin.subscriptions;

import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tenants/{uuid}/subscription")
@Validated
@Tag(name = "Admin Subscriptions", description = "B2B subscription management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class B2BSubscriptionController {

    private final B2BSubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "Get the active subscription for a tenant")
    public ApiResponse<SubscriptionResponse> get(@PathVariable("uuid") UUID tenantId) {
        return subscriptionService.getActiveForTenant(tenantId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("NOT_FOUND", "No active subscription"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign or change plan for a tenant")
    public ApiResponse<SubscriptionResponse> assign(
            @PathVariable("uuid") UUID tenantId,
            @Valid @RequestBody B2BSubscriptionRequest request) {
        return ApiResponse.ok(subscriptionService.assignOrChangePlan(tenantId, request));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription at period end")
    public ApiResponse<Void> cancel(
            @PathVariable("uuid") UUID tenantId,
            @RequestParam(defaultValue = "No reason provided") String reason) {
        subscriptionService.cancel(tenantId, reason);
        return ApiResponse.ok();
    }

    @PostMapping("/reactivate")
    @Operation(summary = "Reactivate a canceled subscription (within 30d grace)")
    public ApiResponse<SubscriptionResponse> reactivate(@PathVariable("uuid") UUID tenantId) {
        subscriptionService.reactivate(tenantId);
        return ApiResponse.ok();
    }
}
