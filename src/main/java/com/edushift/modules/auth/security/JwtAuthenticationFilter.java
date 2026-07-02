package com.edushift.modules.auth.security;

import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.JwtService.TokenType;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates HTTP requests carrying a {@code Authorization: Bearer <jwt>}
 * header.
 *
 * <h3>Behavior</h3>
 * <ol>
 *   <li>If no {@code Authorization} header (or it doesn't start with
 *       {@code "Bearer "}) → continue the chain unauthenticated. The
 *       {@code SecurityFilterChain} then decides per-endpoint whether that's
 *       acceptable (public allow-list) or a 401 (anything else).</li>
 *   <li>If the header is present but the JWT fails to parse, has the wrong
 *       signature, is expired, or is a refresh token (not access) → also
 *       continue unauthenticated. We deliberately do <em>not</em> short-circuit
 *       with a 401 here, because:
 *       <ul>
 *         <li>It would prevent reaching public endpoints with a stale bearer
 *             cached by the client.</li>
 *         <li>It centralizes the 401 decision in
 *             {@code authenticationEntryPoint}, which formats the body
 *             consistently with {@code GlobalExceptionHandler}.</li>
 *       </ul></li>
 *   <li>Otherwise we build a {@link JwtAuthenticationToken} and put it on
 *       the {@link SecurityContext}.</li>
 * </ol>
 *
 * <h3>Tenant binding</h3>
 * This filter does <em>not</em> set {@link com.edushift.shared.multitenancy.TenantContext}
 * directly. That is the job of:
 * <ul>
 *   <li>{@code TenantFilter} (lowest precedence − 100), which runs after the
 *       Spring Security chain and reads the tenant from the now-populated
 *       principal via {@code HttpTenantResolver}; and</li>
 *   <li>{@code TenantInterceptor}, which then validates that the principal's
 *       tenant matches whatever was bound and rejects mismatches.</li>
 * </ul>
 * Keeping the JWT filter focused on authentication only avoids subtle ordering
 * bugs and keeps the multi-tenancy chain reusable for non-JWT auth in the
 * future (e.g., M2M API keys).
 *
 * <h3>Stateless</h3>
 * The {@link SecurityContextHolder} is a {@link ThreadLocal}. Spring Security's
 * {@code SecurityContextHolderFilter} clears it at the end of every request
 * when {@code SessionCreationPolicy.STATELESS} is used, so the filter doesn't
 * need its own {@code finally} cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final LmsRoleAuthorityMapper lmsRoleAuthorityMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain chain) throws ServletException, IOException {
		String token = extractBearer(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			tryAuthenticate(token, request);
		}
		chain.doFilter(request, response);
	}

	private static String extractBearer(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null) {
			return null;
		}
		// Bearer scheme is case-insensitive per RFC 6750 §2.1.
		if (header.length() <= BEARER_PREFIX.length()
				|| !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}

	private void tryAuthenticate(String token, HttpServletRequest request) {
		JwtClaims claims;
		try {
			claims = jwtService.parseAndValidate(token);
		}
		catch (RuntimeException e) {
			// JwtServiceImpl already classifies failures into INVALID_TOKEN /
			// TOKEN_EXPIRED / INVALID_SIGNATURE; we log at DEBUG so attackers
			// don't trivially fill our logs by spamming bad tokens.
			log.debug("[auth] reject bearer ({}): {}", request.getRequestURI(), e.getMessage());
			return;
		}

		if (claims == null) {
			log.debug("[auth] reject bearer ({}): claims are null", request.getRequestURI());
			return;
		}

		if (claims.type() != TokenType.ACCESS) {
			log.debug("[auth] reject bearer ({}): not an access token (type={})",
					request.getRequestURI(), claims.type());
			return;
		}

		UUID publicUuid = parseSubject(claims.subject(), request);
		if (publicUuid == null) {
			return;
		}

		if (claims.tenantId() == null) {
			log.debug("[auth] reject bearer ({}): missing tenant_id claim",
					request.getRequestURI());
			return;
		}

		List<GrantedAuthority> authorities = mapAuthorities(claims.roles());
		JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
				publicUuid,
				claims.tenantId(),
				claims.tenantSlug(),
				/* email */ null);

		JwtAuthenticationToken auth = new JwtAuthenticationToken(principal, token, authorities);
		SecurityContextHolder.getContext().setAuthentication(auth);

		log.debug("[auth] authenticated publicUuid={} tenantSlug={} path={}",
				publicUuid, claims.tenantSlug(), request.getRequestURI());
	}

	private static UUID parseSubject(String subject, HttpServletRequest request) {
		if (subject == null || subject.isBlank()) {
			log.debug("[auth] reject bearer ({}): subject claim is missing",
					request.getRequestURI());
			return null;
		}
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException e) {
			log.debug("[auth] reject bearer ({}): subject is not a UUID",
					request.getRequestURI());
			return null;
		}
	}

	private List<GrantedAuthority> mapAuthorities(Set<String> roles) {
		if (roles == null || roles.isEmpty()) {
			return List.of();
		}
		// (Sprint 7a / BE-7a.3) Two-tier authority shape:
		//   1. Coarse ROLE_* authorities (backward-compat for
		//      @PreAuthorize("hasRole('TENANT_ADMIN')") and friends).
		//   2. Granular LMS_* authorities (the new
		//      @PreAuthorize("hasAuthority('LMS_TASK_SUBMIT')") pattern).
		// The set is dedup'd via LinkedHashSet for stable iteration
		// order (testability).
		Set<String> authorities = new LinkedHashSet<>();
		List<UserRole> parsedRoles = new ArrayList<>();
		for (String r : roles) {
			if (r == null || r.isBlank()) continue;
			String roleName = r.startsWith("ROLE_") ? r.substring("ROLE_".length()) : r;
			authorities.add("ROLE_" + roleName);
			UserRole parsed = UserRole.fromName(roleName);
			if (parsed != null) {
				parsedRoles.add(parsed);
			}
		}
		// Add the LMS_* mapping. Unknown / future roles silently
		// contribute no granular authority (defensive).
		authorities.addAll(lmsRoleAuthorityMapper.mapAuthorities(parsedRoles));
		return authorities.stream()
				.<GrantedAuthority>map(SimpleGrantedAuthority::new)
				.toList();
	}

}
