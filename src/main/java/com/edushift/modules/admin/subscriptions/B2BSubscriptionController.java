package com.edushift.modules.admin.subscriptions;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.shared.api.ApiResponse;
import com.edushift.shared.multitenancy.TenantContext;
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
    public ApiResponse<SubscriptionResponse> get(@PathVariable("uuid") UUID tenantPublicUuid) {
        return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () ->
                subscriptionService.getActiveForTenant(tenantPublicUuid)
                        .map(ApiResponse::ok)
                        .orElse(ApiResponse.error("NOT_FOUND", "No active subscription")));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign or change plan for a tenant")
    public ApiResponse<SubscriptionResponse> assign(
            @PathVariable("uuid") UUID tenantPublicUuid,
            @Valid @RequestBody B2BSubscriptionRequest request) {
        return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL,
                () -> ApiResponse.ok(subscriptionService.assignOrChangePlan(tenantPublicUuid, request)));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription at period end")
    public ApiResponse<Void> cancel(
            @PathVariable("uuid") UUID tenantPublicUuid,
            @RequestParam(defaultValue = "No reason provided") String reason) {
        return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () -> {
            subscriptionService.cancel(tenantPublicUuid, reason);
            return ApiResponse.ok();
        });
    }

    @PostMapping("/reactivate")
    @Operation(summary = "Reactivate a canceled subscription (within 30d grace)")
    public ApiResponse<SubscriptionResponse> reactivate(@PathVariable("uuid") UUID tenantPublicUuid) {
        return TenantContext.runAs(TenantIdResolver.SUPER_ADMIN_SENTINEL, () -> {
            subscriptionService.reactivate(tenantPublicUuid);
            return ApiResponse.ok();
        });
    }
}
