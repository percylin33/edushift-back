package com.edushift.modules.admin.plans;

import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/plans")
@RequiredArgsConstructor
@Tag(name = "Admin AI plan policies", description = "AI quotas for platform plans")
public class PlanAiPolicyController {

    private final PlanAiPolicyService service;

    @GetMapping("/{planId}/ai-policy")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get numeric AI limits of a plan")
    public ApiResponse<PlanAiPolicyResponse> get(@PathVariable UUID planId) {
        return ApiResponse.ok(service.get(planId));
    }

    @GetMapping("/ai-policies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List configured AI plan limits")
    public ApiResponse<java.util.List<PlanAiPolicyResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PutMapping("/{planId}/ai-policy")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Set numeric AI limits of a plan")
    public ApiResponse<PlanAiPolicyResponse> save(
            @PathVariable UUID planId, @Valid @RequestBody PlanAiPolicyRequest request) {
        return ApiResponse.ok(service.save(planId, request));
    }
}
