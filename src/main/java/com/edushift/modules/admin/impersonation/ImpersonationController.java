package com.edushift.modules.admin.impersonation;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/impersonate")
@Validated
@Tag(name = "Admin Impersonation", description = "User impersonation for support (Sprint 15)")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ImpersonationController {

    private final ImpersonationService impersonationService;
    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    @PostMapping("/token")
    @Operation(summary = "Generate a temporary impersonation JWT (15 min)")
    public ApiResponse<Map<String, Object>> generateToken(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal admin,
            @RequestBody ImpersonateTokenRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        User target = impersonationService.resolveTarget(
                request.targetUserPublicUuid(), admin.getId());

        Tenant targetTenant = tenantRepository.findById(target.getTenantId())
                .orElseGet(() -> {
                    Tenant fallback = new Tenant();
                    fallback.setId(target.getTenantId() != null
                            ? target.getTenantId()
                            : TenantIdResolver.SUPER_ADMIN_SENTINEL);
                    fallback.setSlug("unknown");
                    fallback.setName("unknown");
                    return fallback;
                });

        String accessToken = jwtService.issueImpersonationToken(
                target, targetTenant, target.getRoleNames(), admin.getId());

        impersonationService.logImpersonation(admin.getId(),
                target.getPublicUuid(), target.getTenantId(),
                "START", "/admin/impersonate/token", "POST", ip, null);

        Map<String, Object> response = new LinkedHashMap<>(3);
        response.put("accessToken", accessToken);
        response.put("expiresInSec", jwtService.impersonationTokenTtlSeconds());
        response.put("targetUserPublicUuid", target.getPublicUuid().toString());

        return ApiResponse.ok(response);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }

    public record ImpersonateTokenRequest(
            @NotBlank UUID targetUserPublicUuid
    ) {}
}
