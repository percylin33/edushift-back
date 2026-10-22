package com.edushift.modules.ai.controller;

import com.edushift.modules.ai.dto.AiCapabilitiesResponse;
import com.edushift.modules.ai.service.AiCapabilitiesService;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/capabilities")
@RequiredArgsConstructor
@Tag(name = "AI capabilities", description = "Effective AI access for the current tenant and user")
public class AiCapabilitiesController {

    private final AiCapabilitiesService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get AI assistants allowed by the tenant plan and user role")
    public ApiResponse<AiCapabilitiesResponse> current(Authentication authentication) {
        return ApiResponse.ok(service.current(authentication));
    }
}
