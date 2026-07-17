package com.edushift.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.auth.service.JwtService.JwtClaims;
import com.edushift.modules.auth.service.JwtService.TokenType;
import com.edushift.shared.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>The filter has zero JPA / Spring container coupling, so a plain Mockito
 * test exercising it via {@link MockHttpServletRequest} / {@link MockHttpServletResponse}
 * is the cheapest way to cover all branches:
 * <ul>
 *   <li>No header / non-bearer header / blank bearer → no Authentication.</li>
 *   <li>Invalid JWT (any failure mode) → no Authentication, chain still runs.</li>
 *   <li>Refresh token in {@code Authorization: Bearer} → no Authentication
 *       (refresh tokens may only be presented to {@code /v1/auth/refresh}).</li>
 *   <li>Subject not a UUID / missing tenant_id → no Authentication.</li>
 *   <li>Valid access JWT → {@link JwtAuthenticationToken} populated correctly.</li>
 *   <li>Existing Authentication is preserved (filter is non-clobbering).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private FilterChain chain;

	@Mock
	private com.edushift.shared.security.LmsRoleAuthorityMapper authorityMapper;

	@Mock
	private com.edushift.modules.tenants.repository.TenantRepository tenantRepository;

	@InjectMocks
	private JwtAuthenticationFilter filter;

	private MockHttpServletRequest request;

	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		request = new MockHttpServletRequest("GET", "/v1/auth/me");
		response = new MockHttpServletResponse();
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		// Belt-and-braces: the OncePerRequestFilter does not clear the holder,
		// SecurityContextHolderFilter does that in production. Keep tests
		// hermetic so a leak in one test cannot cascade into another.
		SecurityContextHolder.clearContext();
	}

	// ===========================================================================
	// "No-op" branches: filter must never block the chain
	// ===========================================================================

	@Nested
	@DisplayName("does not authenticate when …")
	class NoAuth {

		@Test
		@DisplayName("the request has no Authorization header")
		void noHeader() throws Exception {
			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
			verify(jwtService, never()).parseAndValidate(anyString());
		}

		@Test
		@DisplayName("the Authorization header is not a bearer scheme")
		void nonBearerHeader() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
			verify(jwtService, never()).parseAndValidate(anyString());
		}

		@Test
		@DisplayName("the bearer is just the prefix with no token")
		void bearerWithEmptyToken() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
			verify(jwtService, never()).parseAndValidate(anyString());
		}

		@Test
		@DisplayName("the bearer prefix is mixed-case (RFC 6750 case-insensitive)")
		void bearerCaseInsensitivePrefixIsAccepted() throws Exception {
			// Mixed-case scheme should still trigger validation.
			request.addHeader(HttpHeaders.AUTHORIZATION, "BeArEr xyz");
			when(jwtService.parseAndValidate("xyz"))
					.thenThrow(new UnauthorizedException("INVALID_TOKEN", "bad"));

			filter.doFilter(request, response, chain);

			verify(jwtService, times(1)).parseAndValidate("xyz");
			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("the JWT fails parse / validation")
		void invalidToken() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.value");
			when(jwtService.parseAndValidate("invalid.jwt.value"))
					.thenThrow(new UnauthorizedException("INVALID_TOKEN",
							"Token signature is invalid"));

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("the token is a refresh token (typ=refresh)")
		void refreshTokenRejected() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer refresh.jwt");
			when(jwtService.parseAndValidate("refresh.jwt"))
					.thenReturn(claims(UUID.randomUUID().toString(), TokenType.REFRESH,
							UUID.randomUUID(), null, Set.of()));

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("the subject claim is not a UUID")
		void subjectNotAUuid() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer x");
			when(jwtService.parseAndValidate("x"))
					.thenReturn(claims("not-a-uuid", TokenType.ACCESS,
							UUID.randomUUID(), "demo", Set.of()));

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("the tenant_id claim is missing")
		void tenantIdMissing() throws Exception {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer x");
			when(jwtService.parseAndValidate("x"))
					.thenReturn(claims(UUID.randomUUID().toString(), TokenType.ACCESS,
							/* tenantId */ null, "demo", Set.of()));

			filter.doFilter(request, response, chain);

			assertNoAuthSet();
			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("an Authentication is already on the SecurityContext")
		void preservesExistingAuthentication() throws Exception {
			JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
					UUID.randomUUID(), UUID.randomUUID(), "preset", "preset@x.test");
			JwtAuthenticationToken existing = new JwtAuthenticationToken(
					principal, "preset.token", java.util.List.of());
			SecurityContextHolder.getContext().setAuthentication(existing);

			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer different.jwt");

			filter.doFilter(request, response, chain);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).as("existing authentication preserved").isSameAs(existing);
			verify(jwtService, never()).parseAndValidate(anyString());
			verify(chain, times(1)).doFilter(request, response);
		}

	}

	// ===========================================================================
	// Happy paths
	// ===========================================================================

	@Nested
	@DisplayName("authenticates with a JwtAuthenticationToken when …")
	class HappyPath {

		@Test
		@DisplayName("a valid access JWT is presented (no roles)")
		void validAccessTokenNoRoles() throws Exception {
			UUID publicUuid = UUID.randomUUID();
			UUID tenantId = UUID.randomUUID();
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good.access.jwt");
			when(jwtService.parseAndValidate("good.access.jwt"))
					.thenReturn(claims(publicUuid.toString(), TokenType.ACCESS,
							tenantId, "demo", Set.of()));

			filter.doFilter(request, response, chain);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
			assertThat(auth.isAuthenticated()).isTrue();
			assertThat(auth.getName())
					.as("getName() must be the publicUuid string for AuthService.currentUser()")
					.isEqualTo(publicUuid.toString());
			assertThat(auth.getCredentials())
					.as("raw bearer kept as credentials for downstream propagation")
					.isEqualTo("good.access.jwt");
			assertThat(auth.getAuthorities()).isEmpty();

			JwtAuthenticatedPrincipal principal = (JwtAuthenticatedPrincipal) auth.getPrincipal();
			assertThat(principal.id()).isEqualTo(publicUuid);
			assertThat(principal.tenantId()).isEqualTo(tenantId);
			assertThat(principal.tenantSlug()).isEqualTo("demo");

			verify(chain, times(1)).doFilter(request, response);
		}

		@Test
		@DisplayName("a valid access JWT is presented with roles → ROLE_ prefix added")
		void validAccessTokenWithRoles() throws Exception {
			UUID publicUuid = UUID.randomUUID();
			UUID tenantId = UUID.randomUUID();
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good.jwt");
			when(jwtService.parseAndValidate("good.jwt"))
					.thenReturn(claims(publicUuid.toString(), TokenType.ACCESS,
							tenantId, "demo", Set.of("ADMIN", "TEACHER")));

			filter.doFilter(request, response, chain);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			assertThat(auth.getAuthorities())
					.extracting(GrantedAuthority::getAuthority)
					.containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_TEACHER");
		}

		@Test
		@DisplayName("a valid access JWT with already-prefixed roles → no double prefix")
		void rolesAlreadyPrefixed() throws Exception {
			UUID publicUuid = UUID.randomUUID();
			UUID tenantId = UUID.randomUUID();
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good.jwt");
			when(jwtService.parseAndValidate("good.jwt"))
					.thenReturn(claims(publicUuid.toString(), TokenType.ACCESS,
							tenantId, "demo", Set.of("ROLE_ADMIN")));

			filter.doFilter(request, response, chain);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth.getAuthorities())
					.extracting(GrantedAuthority::getAuthority)
					.containsExactly("ROLE_ADMIN");
		}

		@Test
		@DisplayName("TENANT_ADMIN role expands to LMS_AI_GENERATE (and the rest) via LmsRoleAuthorityMapper")
		void tenantAdminGetsLmsAiGenerate() throws Exception {
			// Use the real mapper (not a mock) so the test catches regressions in the
			// role→authority matrix itself, not just the wiring of the filter.
			com.edushift.shared.security.LmsRoleAuthorityMapper realMapper =
					new com.edushift.shared.security.LmsRoleAuthorityMapper(
							mock(com.edushift.modules.tenants.service.PermissionOverrideService.class));
			JwtAuthenticationFilter realFilter =
					new JwtAuthenticationFilter(jwtService, realMapper, mock(com.edushift.modules.tenants.repository.TenantRepository.class));

			UUID publicUuid = UUID.randomUUID();
			UUID tenantId = UUID.randomUUID();
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good.jwt");
			when(jwtService.parseAndValidate("good.jwt"))
					.thenReturn(claims(publicUuid.toString(), TokenType.ACCESS,
							tenantId, "demo", Set.of("TENANT_ADMIN")));

			realFilter.doFilter(request, response, chain);

			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(auth).isNotNull();
			// Coarse ROLE_ kept for hasRole(...) checks.
			assertThat(auth.getAuthorities())
					.extracting(GrantedAuthority::getAuthority)
					.contains("ROLE_TENANT_ADMIN");
			// Granular LMS_ authorities populated for hasAuthority(...) checks.
			assertThat(auth.getAuthorities())
					.extracting(GrantedAuthority::getAuthority)
					.contains("LMS_AI_GENERATE", "LMS_QUIZ_CREATE", "LMS_TASK_READ");
		}

	}

	// ===========================================================================
	// Helpers
	// ===========================================================================

	private static JwtClaims claims(String subject, TokenType type, UUID tenantId,
	                                 String tenantSlug, Set<String> roles) {
		Instant now = Instant.now();
		return new JwtClaims(subject, tenantId, tenantSlug, roles, type, UUID.randomUUID(), now,
				now.plusSeconds(900));
	}

	private static void assertNoAuthSet() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).as("filter must not set an Authentication").isNull();
	}

}
