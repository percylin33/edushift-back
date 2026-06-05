package com.edushift.config;

import com.edushift.modules.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Application security configuration.
 *
 * <h3>Sprint 1 / BE-1.6 status</h3>
 * The chain is now <strong>stateless + bearer-authenticated</strong>:
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} runs before
 *       {@link UsernamePasswordAuthenticationFilter} and populates the
 *       {@link org.springframework.security.core.context.SecurityContext}
 *       from the {@code Authorization: Bearer ...} header.</li>
 *   <li>Sessions are disabled (no {@code JSESSIONID}) — the bearer is the
 *       only auth on every call.</li>
 *   <li>CSRF is disabled for the same reason: no cookie-bound session means
 *       no CSRF surface.</li>
 *   <li>401 / 403 responses are routed through Spring MVC's
 *       {@link HandlerExceptionResolver} so the existing
 *       {@code GlobalExceptionHandler} formats them as {@code ApiErrorResponse}
 *       (consistent shape with all other API errors).</li>
 * </ul>
 *
 * <h3>Public allow-list</h3>
 * The matchers in {@link #PUBLIC_PATHS} stay accessible without authentication
 * because:
 * <ul>
 *   <li>{@code /v1/auth/login}, {@code /v1/auth/refresh} — bootstrap a session;
 *       the user does not yet have a bearer.</li>
 *   <li>{@code /v1/auth/logout} — the refresh token IS the proof of identity
 *       to revoke it; layering bearer auth on top is defense-in-depth only and
 *       would break logout from clients whose access token has expired.</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — API docs (springdoc).</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info} — liveness probes.</li>
 * </ul>
 *
 * <h3>Path matchers and the {@code /api} context-path</h3>
 * The matchers below are written without the {@code /api} prefix because
 * {@code server.servlet.context-path=/api} strips it before Spring Security
 * sees the URI. Inside the chain we only see e.g. {@code /v1/auth/login}.
 */
@Configuration
@EnableWebSecurity
// Activates @PreAuthorize / @PostAuthorize on @RestController methods.
// Sprint 2 (BE-2.4) uses it to gate PATCH /v1/tenants/me on TENANT_ADMIN;
// Sprint 3+ will lean heavily on it for per-resource authorization.
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * BCrypt cost factor for password hashing.
	 * <p>Tuning notes:
	 * <ul>
	 *   <li>10 = ~50ms / hash on commodity hardware (Spring default)</li>
	 *   <li>12 = ~250ms / hash; recommended for SaaS auth in 2026</li>
	 *   <li>14 = ~1s / hash; only if you can afford the latency</li>
	 * </ul>
	 * Re-tune as hardware evolves.
	 */
	private static final int BCRYPT_STRENGTH = 12;

	/**
	 * Routes that must stay public regardless of how the authentication
	 * filter chain evolves.
	 */
	private static final String[] PUBLIC_PATHS = {
			"/v1/auth/login",
			"/v1/auth/refresh",
			"/v1/auth/logout",
			// `tenants/by-slug` powers the tenant-aware login screen — anyone
			// rendering /auth/login on a given subdomain needs to read its
			// branding before having any credentials. Sensitive fields are
			// withheld at the DTO level (TenantSummary), so opening this path
			// to the public web is safe by construction.
			"/v1/tenants/by-slug/*",
			// `tenants/register` is the public self-signup entry point.
			// Anyone on the open internet can create a TRIAL tenant; the
			// per-IP rate-limit landing in a future hardening sprint will
			// keep abuse manageable. The controller validates the body
			// strictly and the response is shaped exactly like /auth/login.
			"/v1/tenants/register",
			// User-invitation public flow (BE-3.2):
			//   * `by-token/{token}` — preflight: returns recipient + tenant
			//     name so the accept page can greet the user. Public-safe
			//     because the response only carries fields the recipient
			//     just typed back to the system.
			//   * `accept` — token redemption: creates the new user in the
			//     invitation's tenant and returns a session envelope.
			// Both are gated by token entropy (~192 bits) and a partial
			// unique index on `token` is the global namespace guarantee.
			"/v1/users/invitations/by-token/*",
			"/v1/users/invitations/accept",
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/v3/api-docs",
			"/v3/api-docs/**",
			"/actuator/health",
			"/actuator/info"
	};

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	private final HandlerExceptionResolver handlerExceptionResolver;

	private final CorsConfigurationSource corsConfigurationSource;

	/**
	 * Spring auto-creates several {@link HandlerExceptionResolver} beans (e.g.
	 * one for {@code @ExceptionHandler} methods, one for {@code ResponseStatus}
	 * exceptions, etc.) and exposes a composite under the well-known name
	 * {@code handlerExceptionResolver}. We bind to that composite via
	 * {@link Qualifier} so a 401 / 403 originating from the security filter
	 * chain runs through the whole MVC error pipeline — including
	 * {@code GlobalExceptionHandler} — and not just one of the resolvers.
	 *
	 * The {@link CorsConfigurationSource} comes from {@link CorsConfig} and
	 * is wired into Spring Security via {@link HttpSecurity#cors(Customizer)}
	 * below, so a single source of truth governs both Spring MVC's CORS
	 * filter and the security chain's pre-flight handling.
	 */
	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
			CorsConfigurationSource corsConfigurationSource) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.handlerExceptionResolver = handlerExceptionResolver;
		this.corsConfigurationSource = corsConfigurationSource;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.authorizeHttpRequests(auth -> auth
						/* CORS pre-flight: browsers fire OPTIONS without auth headers
						 * BEFORE any cross-origin request. Spring's CorsFilter runs
						 * earlier in the chain and short-circuits these with the proper
						 * Access-Control-* headers, but the security chain still sees
						 * them. Allowing OPTIONS unconditionally avoids the
						 * 403-on-preflight class of bug entirely. */
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(eh -> eh
						.authenticationEntryPoint(restAuthenticationEntryPoint())
						.accessDeniedHandler(restAccessDeniedHandler()))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	/**
	 * Routes anonymous / unauthenticated requests to MVC's exception
	 * resolver, which lets {@code GlobalExceptionHandler.handleAuthentication}
	 * produce the canonical {@code ApiErrorResponse} body.
	 */
	private AuthenticationEntryPoint restAuthenticationEntryPoint() {
		return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) ->
				handlerExceptionResolver.resolveException(request, response, /* handler */ null, authException);
	}

	/**
	 * Same idea as {@link #restAuthenticationEntryPoint()} but for 403s
	 * (authenticated user, missing role / forbidden resource).
	 */
	private AccessDeniedHandler restAccessDeniedHandler() {
		return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) ->
				handlerExceptionResolver.resolveException(request, response, /* handler */ null, accessDeniedException);
	}

	/**
	 * Password encoder used by the auth service to hash passwords on creation
	 * and verify them on login. BCrypt with cost {@value #BCRYPT_STRENGTH}.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

}
