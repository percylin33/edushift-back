package com.edushift.test;

import com.edushift.infrastructure.ratelimit.RateLimitConfiguration;
import com.edushift.infrastructure.ratelimit.SimpleRateLimiter;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;

/**
 * Shared wiring for {@code @WebMvcTest} slices.
 *
 * <p>The default {@code @WebMvcTest} slice only loads MVC-layer beans
 * (controllers, advice, filters, interceptors). It does NOT load regular
 * {@code @Component}s nor {@code @ConfigurationProperties} bindings. This
 * leaves several infrastructure beans missing — failures all surface as
 * {@code NoSuchBeanDefinitionException} at startup with the offending
 * bean named after a {@code HandlerInterceptor}, {@code Filter} or
 * {@code WebMvcConfigurer} that one of our infrastructure classes
 * declares.</p>
 *
 * <p>Beans this config registers:</p>
 * <ol>
 *   <li>{@code RateLimitProperties} — imported via
 *       {@link RateLimitConfiguration}; the rate-limit interceptor needs
 *       it.</li>
 *   <li>{@code SimpleRateLimiter} — pure in-memory primitive; the rate
 *       limit interceptor delegates bucket counting here.</li>
 *   <li>{@code CurrentUserProvider} — a stand-in that mirrors the
 *       production class by reading from {@code SecurityContextHolder}.
 *       Tests asserting specific user behaviour can override via
 *       {@code @MockitoBean}.</li>
 *   <li>{@code ImpersonationService} — mocked. The production bean
 *       pulls in {@code UserRepository} and {@code AuditLogger}, neither
 *       of which belong in a controller slice; only the filter's
 *       existence matters here.</li>
 * </ol>
 *
 * <p>This class is imported by every {@code @WebMvcTest} via
 * {@code @Import(EdushiftWebMvcTestConfig.class)} (or by the
 * {@code @WebMvcSlice} meta-annotation, once present).</p>
 */
@TestConfiguration
@Import(RateLimitConfiguration.class)
public class EdushiftWebMvcTestConfig {

	/**
	 * Test-scope {@link CurrentUserProvider}. See class javadoc.
	 */
	@Bean
	CurrentUserProvider currentUserProvider() {
		return new TestCurrentUserProvider();
	}

	/**
	 * Real {@link SimpleRateLimiter}. The limiter is a pure in-memory
	 * primitive (no external IO), so wiring the real bean is safe in tests.
	 */
	@Bean
	SimpleRateLimiter simpleRateLimiter() {
		return new SimpleRateLimiter();
	}

	/**
	 * Stand-in {@code ImpersonationService}. The auto-detected
	 * {@code ImpersonationFilter} requires this dependency; tests that
	 * exercise impersonation override this with {@code @MockitoBean}.
	 */
	@Bean
	com.edushift.modules.admin.impersonation.ImpersonationService impersonationService() {
		return mock(com.edushift.modules.admin.impersonation.ImpersonationService.class);
	}

	/**
	 * Stand-in {@code JwtService}. The auto-detected
	 * {@code JwtAuthenticationFilter} (also a {@code Filter} component)
	 * requires this. Controller-slice tests that actually exercise auth
	 * should re-declare it with {@code @MockitoBean} to stub
	 * {@code parseAndValidate}; the default mock returns Mockito's
	 * zero-return value so the filter is a no-op.
	 */
	@Bean
	com.edushift.modules.auth.service.JwtService jwtService() {
		return mock(com.edushift.modules.auth.service.JwtService.class);
	}

	/**
	 * Stand-in {@code GoogleAuthService}. Only
	 * {@code AuthController} depends on it; the production bean pulls
	 * in REST client + JSON config which doesn't belong in a
	 * controller slice.
	 */
	@Bean
	com.edushift.modules.auth.service.GoogleAuthService googleAuthService() {
		return mock(com.edushift.modules.auth.service.GoogleAuthService.class);
	}

	/**
	 * Stand-in {@code SessionsPdfExportService}. OpenPDF + filesystem
	 * wiring does not belong in the controller slice. The
	 * {@code /sessions/{id}/pdf} endpoint is integration-tested by
	 * {@code LearningSessionsPdfIT}.
	 */
	@Bean
	com.edushift.modules.sessions.learning.service.SessionsPdfExportService sessionsPdfExportService() {
		return mock(com.edushift.modules.sessions.learning.service.SessionsPdfExportService.class);
	}

	/**
	 * Stand-in {@code LmsRoleAuthorityMapper}. The auto-loaded
	 * {@code JwtAuthenticationFilter} (a {@code Filter} component)
	 * consumes it to expand role→authorities. The real mapper requires
	 * {@code PermissionOverrideService}, which pulls in JPA — too heavy
	 * for a controller slice, so we wire the real mapper with a mocked
	 * override service. The {@code grantFor(...)} path it exercises
	 * during JWT validation is unrelated to override plumbing.
	 */
	@Bean
	com.edushift.shared.security.LmsRoleAuthorityMapper lmsRoleAuthorityMapper() {
		return new com.edushift.shared.security.LmsRoleAuthorityMapper(
				mock(com.edushift.modules.tenants.service.PermissionOverrideService.class));
	}

	/**
	 * Stand-in {@code GoogleIdentityProvider}. The Google OAuth adapter
	 * pulls in {@code WebClient} + a JSON parser configuration that
	 * belong to {@code @SpringBootTest}, not to the controller slice.
	 */
	@Bean
	com.edushift.infrastructure.integrations.google.GoogleIdentityProvider googleIdentityProvider() {
		return mock(com.edushift.infrastructure.integrations.google.GoogleIdentityProvider.class);
	}

	/**
	 * Stand-in {@code TenantResolver}. {@link com.edushift.infrastructure.multitenancy.MultiTenancyConfiguration}
	 * registers the tenant filter and exposes a bean that needs it;
	 * the resolver itself depends on {@code HttpServletRequest} parsing
	 * and is irrelevant to most controller-slice tests.
	 */
	@Bean
	com.edushift.shared.multitenancy.TenantResolver tenantResolver() {
		return mock(com.edushift.shared.multitenancy.TenantResolver.class);
	}

	/**
	 * Stand-in {@code TenantRepository}. Only used by
	 * {@code AuthController} for the {@code /auth/tenants/by-slug}
	 * public lookup; the controller-slice test for that endpoint
	 * overrides this bean with {@code @MockitoBean} to stub a real
	 * response.
	 */
	@Bean
	com.edushift.modules.tenants.repository.TenantRepository tenantRepository() {
		return mock(com.edushift.modules.tenants.repository.TenantRepository.class);
	}

	/**
	 * Stand-in {@code MfaService}. Pulls in {@code TotpEncoder} +
	 * {@code RecoveryCodeHasher} + {@code AuditLogger} — all wiring
	 * that doesn't belong to a controller slice. Individual MFA-endpoint
	 * tests override this bean with {@code @MockitoBean} to drive
	 * specific enrol/verify scenarios.
	 */
	@Bean
	com.edushift.modules.auth.service.MfaService mfaService() {
		return mock(com.edushift.modules.auth.service.MfaService.class);
	}

	static final class TestCurrentUserProvider implements CurrentUserProvider {

		@Override
		public Optional<UUID> currentUserId() {
			return authentication()
					.map(Authentication::getPrincipal)
					.flatMap(TestCurrentUserProvider::resolveUserId);
		}

		@Override
		public Optional<String> currentUsername() {
			return authentication().map(Authentication::getName);
		}

		@Override
		public Optional<UUID> currentTenantId() {
			return authentication()
					.map(Authentication::getPrincipal)
					.flatMap(principal -> {
						if (principal instanceof com.edushift.infrastructure.security.AuthenticatedPrincipal ap) {
							return Optional.ofNullable(ap.getTenantId());
						}
						return Optional.empty();
					});
		}

		private static Optional<Authentication> authentication() {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
				return Optional.empty();
			}
			return Optional.of(auth);
		}

		private static Optional<UUID> resolveUserId(Object principal) {
			if (principal instanceof com.edushift.infrastructure.security.AuthenticatedPrincipal ap) {
				return Optional.ofNullable(ap.getId());
			}
			return Optional.empty();
		}
	}
}


