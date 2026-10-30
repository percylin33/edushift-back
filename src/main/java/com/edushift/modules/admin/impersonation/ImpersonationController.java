package com.edushift.modules.admin.impersonation;

import com.edushift.infrastructure.multitenancy.TenantIdResolver;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.controller.CookieHelper;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import com.edushift.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseCookie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final UserRepository userRepository;
    private final CookieHelper cookieHelper;

    @GetMapping("/tenants/{tenantPublicUuid}/users")
    @Operation(summary = "Search active tenant users for the support impersonation picker")
    public ApiResponse<Map<String, Object>> findSupportUsers(
            @PathVariable UUID tenantPublicUuid,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        String normalizedSearch = search == null ? "" : search.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Map<String, Object> result = com.edushift.shared.multitenancy.TenantContext.runAs(
                TenantIdResolver.SUPER_ADMIN_SENTINEL, () -> {
                    Tenant tenant = tenantRepository.findByPublicUuid(tenantPublicUuid)
                            .orElseThrow(() -> new com.edushift.shared.exception.ResourceNotFoundException(
                                    "tenants/" + tenantPublicUuid));
                    Page<User> users = tenant.getStatus() == TenantStatus.ACTIVE
                            ? userRepository.findActiveSupportUsersByTenantId(
                                    tenant.getId(), normalizedSearch, pageable)
                            : Page.empty(pageable);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("content", users.getContent().stream()
                            .map(user -> new SupportUserOption(
                                    user.getPublicUuid(), user.getEmail(), buildFullName(user),
                                    List.copyOf(user.getRoleNames())))
                            .toList());
                    body.put("totalElements", users.getTotalElements());
                    body.put("totalPages", users.getTotalPages());
                    body.put("number", users.getNumber());
                    body.put("size", users.getSize());
                    return body;
                });
        return ApiResponse.ok(result);
    }

    @PostMapping("/token")
    @Operation(summary = "Generate a temporary impersonation JWT (15 min)")
    public ApiResponse<Map<String, Object>> generateToken(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal admin,
            @Valid @RequestBody ImpersonateTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ip = resolveClientIp(httpRequest);
        User target = impersonationService.resolveTarget(
                request.targetUserPublicUuid(), admin.getId());

        UUID targetTenantId = target.getTenantId() != null
                ? target.getTenantId()
                : TenantIdResolver.SUPER_ADMIN_SENTINEL;
        Tenant targetTenant = tenantRepository.findById(targetTenantId)
                .orElseGet(() -> {
                    Tenant fallback = new Tenant();
                    fallback.setId(targetTenantId);
                    fallback.setSlug("unknown");
                    fallback.setName("unknown");
                    return fallback;
                });

        String accessToken = jwtService.issueImpersonationToken(
                target, targetTenant, target.getRoleNames(), admin.getId());

        httpResponse.addHeader("Set-Cookie", cookieHelper.createImpersonationAccessCookie(
                accessToken, jwtService.impersonationTokenTtlSeconds()).toString());

        impersonationService.logImpersonation(admin.getId(),
                target.getPublicUuid(), target.getTenantId(),
                "START", "/admin/impersonate/token", "POST", ip, null, request.reason());

        Map<String, Object> response = new LinkedHashMap<>(3);
        response.put("expiresInSec", jwtService.impersonationTokenTtlSeconds());
        response.put("targetUserPublicUuid", target.getPublicUuid().toString());
        response.put("targetUser", Map.of(
                "publicUuid", target.getPublicUuid().toString(),
                "email", target.getEmail(),
                "firstName", target.getFirstName() == null ? "" : target.getFirstName(),
                "lastName", target.getLastName() == null ? "" : target.getLastName(),
                "fullName", buildFullName(target),
                "roles", target.getRoleNames(),
                "tenantId", targetTenantId.toString(),
                "tenantSlug", targetTenant.getSlug()));

        return ApiResponse.ok(response);
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the current user impersonation")
    public ApiResponse<Void> stop(
            @AuthenticationPrincipal JwtAuthenticatedPrincipal admin,
            @Valid @RequestBody StopImpersonationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        impersonationService.logImpersonation(admin.getId(), request.targetUserPublicUuid(), null,
                "STOP", "/admin/impersonate/stop", "POST", resolveClientIp(httpRequest), null, request.reason());
        httpResponse.addHeader("Set-Cookie", cookieHelper.clearImpersonationAccessCookie().toString());
        return ApiResponse.ok();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }

    private static String buildFullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    public record ImpersonateTokenRequest(
            @NotNull UUID targetUserPublicUuid,
            @NotBlank @jakarta.validation.constraints.Size(max = 500) String reason
    ) {}

    public record StopImpersonationRequest(@NotNull UUID targetUserPublicUuid, String reason) {}

    public record SupportUserOption(UUID publicUuid, String email, String fullName, List<String> roles) {}
}
