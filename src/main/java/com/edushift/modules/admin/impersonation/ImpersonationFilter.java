package com.edushift.modules.admin.impersonation;

import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImpersonationFilter extends OncePerRequestFilter {

    private static final String IMPERSONATE_HEADER = "X-Impersonate-User";

    private final ImpersonationService impersonationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String impersonateHeader = request.getHeader(IMPERSONATE_HEADER);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (impersonateHeader != null && !impersonateHeader.isBlank()
                && auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof JwtAuthenticatedPrincipal adminPrincipal) {

            boolean isSuperAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

            if (isSuperAdmin) {
                UUID targetUuid;
                try {
                    targetUuid = UUID.fromString(impersonateHeader.trim());
                } catch (IllegalArgumentException e) {
                    log.warn("[impersonation] invalid X-Impersonate-User UUID: {}", impersonateHeader);
                    chain.doFilter(request, response);
                    return;
                }

                try {
                    User target = impersonationService.resolveTarget(targetUuid, adminPrincipal.getId());
                    String ip = resolveClientIp(request);
                    String userAgent = request.getHeader("User-Agent");
                    String bodyHash = sha256BodyHash(request, userAgent);

                    impersonationService.logImpersonation(
                            adminPrincipal.getId(), target.getPublicUuid(), target.getTenantId(),
                            request.getRequestURI(), request.getRequestURI(),
                            request.getMethod(), ip, bodyHash);

                    JwtAuthenticatedPrincipal impersonatedPrincipal = new JwtAuthenticatedPrincipal(
                            target.getPublicUuid(),
                            target.getTenantId(),
                            null,
                            target.getEmail());

                    var impersonatedAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            impersonatedPrincipal, null, auth.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(impersonatedAuth);

                    response.setHeader("X-Impersonated-By", adminPrincipal.getId().toString());
                    response.setHeader("X-Impersonated-For", target.getPublicUuid().toString());

                    log.info("[impersonation] admin={} impersonating user={} at {} {}",
                            adminPrincipal.getId(), targetUuid, request.getMethod(),
                            request.getRequestURI());
                } catch (Exception e) {
                    log.warn("[impersonation] rejected: {} (admin={}, target={})",
                            e.getMessage(), adminPrincipal.getId(), targetUuid);
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Stable, non-reversible fingerprint of the request for the audit log.
     * Combines path + method + (optional) User-Agent + body hash so two
     * identical requests produce identical fingerprints but distinct
     * mutations are visible to investigators.
     */
    private static String sha256BodyHash(HttpServletRequest request, String userAgent) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(request.getRequestURI().getBytes());
            md.update((byte) '|');
            md.update(request.getMethod().getBytes());
            md.update((byte) '|');
            if (userAgent != null) {
                md.update(userAgent.getBytes());
            }
            return HexFormat.of().formatHex(md.digest());
        }
        catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma < 0 ? xff.trim() : xff.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }
}
