package com.edushift.test;

import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Abstract base for {@code @WebMvcTest} controller slices. Provides:
 *
 * <ul>
 *   <li>Auto-import of {@link EdushiftWebMvcTestConfig} (rate-limit, JWT, etc.)</li>
 *   <li>Convenience builders for authenticated principals per role</li>
 *   <li>{@link #url(String)} helper that prepends the global {@code /v1} prefix
 *       applied by {@code com.edushift.config.WebConfiguration}</li>
 * </ul>
 *
 * <h2>Why {@code /v1}?</h2>
 * <p>The production app uses {@code server.servlet.context-path=/api} and a
 * global {@link org.springframework.web.servlet.config.annotation.PathMatchConfigurer}
 * that prepends {@code /v1} to every controller in {@code com.edushift.modules.*}.
 * However, MockMvc <strong>does not</strong> apply {@code server.servlet.context-path}
 * (it's a Servlet-container concept), so the effective mount inside the test
 * is just {@code /v1/&lt;controller-path&gt;}. Every test URL in subclasses
 * must therefore start with {@code /v1}, which is what {@link #url(String)} enforces
 * by normalizing inputs.</p>
 *
 * <p>Subclasses declare their own {@code @WebMvcTest(ControllerX.class)} plus
 * {@code @MockitoBean} for the services they exercise. This keeps the boilerplate
 * small while reusing the cross-cutting test wiring.</p>
 */
public abstract class AbstractControllerTest {

    protected static final UUID ANY_TENANT = UUID.fromString("019ec200-0000-7000-0000-000000000001");
    protected static final UUID ANY_USER = UUID.fromString("019ec200-0000-7000-0000-000000000010");

    protected static JwtAuthenticationToken principal(List<String> authorities) {
        var grants = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new JwtAuthenticationToken(
                new JwtAuthenticatedPrincipal(ANY_USER, ANY_TENANT, "test-user", "test@edushift"),
                "fake-jwt",
                grants);
    }

    protected static JwtAuthenticationToken tenantAdmin() {
        return principal(List.of("ROLE_TENANT_ADMIN"));
    }

    protected static JwtAuthenticationToken teacher() {
        return principal(List.of("ROLE_TEACHER"));
    }

    protected static JwtAuthenticationToken student() {
        return principal(List.of("ROLE_STUDENT"));
    }

    protected static JwtAuthenticationToken parent() {
        return principal(List.of("ROLE_PARENT"));
    }

    protected static JwtAuthenticationToken staff() {
        return principal(List.of("ROLE_STAFF"));
    }

    protected static JwtAuthenticationToken superAdmin() {
        return principal(List.of("ROLE_SUPER_ADMIN"));
    }

    protected static RequestPostProcessor auth(JwtAuthenticationToken token) {
        return SecurityMockMvcRequestPostProcessors.authentication(token);
    }

    /**
     * Returns a path with the global {@code /v1} prefix guaranteed, so
     * subclasses can write {@code url("/announcements")} instead of
     * remembering the prefix rule from {@code WebConfiguration}.
     *
     * <p>If the caller already supplied {@code /v1/...} or {@code /api/v1/...},
     * the value is normalized down to the MockMvc-effective form
     * (no {@code /api}, exactly one {@code /v1}).</p>
     *
     * @param path the controller-relative path, e.g. {@code "/announcements"}
     * @return the MockMvc-effective path, e.g. {@code "/v1/announcements"}
     */
    protected static String url(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        String p = path;
        if (p.startsWith("/api/v1")) {
            p = p.substring("/api".length());
        } else if (p.startsWith("/api/")) {
            p = p.substring("/api".length());
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (!p.startsWith("/v1/") && !p.equals("/v1")) {
            p = "/v1" + (p.equals("/") ? "" : p);
        }
        return p;
    }
}
