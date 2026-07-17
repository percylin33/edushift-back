package com.edushift.modules.admin.plans;

import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/plans")
@Validated
@Tag(name = "Admin Plans", description = "Platform plan catalog management (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformPlanController {

    private final PlatformPlanService planService;

    @GetMapping
    @Operation(summary = "List all active plans")
    public ApiResponse<List<PlanResponse>> list() {
        return ApiResponse.ok(planService.listAll());
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get a plan by ID")
    public ApiResponse<PlanResponse> get(@PathVariable("uuid") UUID id) {
        return ApiResponse.ok(planService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new plan")
    public ApiResponse<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return ApiResponse.ok(planService.create(request));
    }

    @PatchMapping("/{uuid}")
    @Operation(summary = "Update a plan")
    public ApiResponse<PlanResponse> update(
            @PathVariable("uuid") UUID id,
            @Valid @RequestBody UpdatePlanRequest request) {
        return ApiResponse.ok(planService.update(id, request));
    }

    @DeleteMapping("/{uuid}")
    @Operation(summary = "Deactivate a plan (soft delete)")
    public ApiResponse<Void> deactivate(@PathVariable("uuid") UUID id) {
        planService.deactivate(id);
        return ApiResponse.ok();
    }
}
