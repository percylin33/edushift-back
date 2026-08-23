package com.edushift.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-scope replacement for {@code com.edushift.config.SecurityConfig}.
 *
 * <p>The production config wires a full Spring Security chain with JWT and
 * impersonation filters, CORS, headers, and an entry point that routes
 * through the MVC exception resolver. The controller-slice
 * ({@code @WebMvcTest}) does not load most of those beans, so importing
 * the production config fails on missing dependencies.</p>
 *
 * <p>This minimal config exists only to satisfy what the {@code @WebMvcTest}
 * slice actually needs to faithfully reproduce the production behaviour
 * that matters for controller tests:</p>
 *
 * <ol>
 *   <li>{@link EnableMethodSecurity} — activates {@code @PreAuthorize} on
 *       controller methods. Without it, {@code hasAnyRole('TENANT_ADMIN', ...)}
 *       is silently bypassed and a {@code STUDENT} request to a
 *       {@code TENANT_ADMIN} endpoint returns 200 instead of 403.
 *       See <a href="https://stackoverflow.com/questions/75900202">SO 75900202</a>.</li>
 *   <li>CSRF disabled — production is stateless/bearer and disables CSRF
 *       too; mirroring that allows {@code POST/PUT/PATCH/DELETE} without
 *       {@code .with(csrf())} noise in tests that only care about
 *       authorization, not CSRF defence. Tests that specifically want to
 *       assert CSRF behaviour must turn it on themselves.</li>
 *   <li>Any request authenticated — ensures anonymous tests still get 401
 *       (the production behaviour).</li>
 * </ol>
 *
 * <p>It is intentionally NOT imported from
 * {@link EdushiftWebMvcTestConfig}: opting in per test class keeps the
 * change scoped. Tests that rely on {@code @WithMockUser} must
 * {@code @Import(TestSecurityConfig.class)} explicitly; otherwise
 * {@code @PreAuthorize} is bypassed and tests pass for the wrong reason.</p>
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .build();
    }
}
