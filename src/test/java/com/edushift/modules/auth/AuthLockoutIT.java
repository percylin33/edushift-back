package com.edushift.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.IntegrationTest;
import com.edushift.modules.auth.entity.FailedLoginAttempt;
import com.edushift.modules.auth.entity.User;
import com.edushift.modules.auth.entity.UserRole;
import com.edushift.modules.auth.entity.UserStatus;
import com.edushift.modules.auth.repository.FailedLoginAttemptRepository;
import com.edushift.modules.auth.repository.UserRepository;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Sprint 14 (MVP Closure) / DEBT-AUTH-7 — full-stack IT.
 *
 * <p>End-to-end verification that:
 * <ul>
 *   <li>5 failed login attempts lock the account for 15 min.</li>
 *   <li>Locked user gets a 429 with Retry-After header on the 6th try.</li>
 *   <li>Successful login resets the counter and clears the lock.</li>
 * </ul>
 *
 * <p>Mirrors the {@code AuthTenantIsolationIT} shape but focuses on the
 * lockout path. Uses a unique tenant slug per run (UUID-suffixed) so
 * the {@code static} Postgres container can be shared safely.
 */
class AuthLockoutIT extends IntegrationTest {

	@LocalServerPort private int port;

	@Autowired private TenantRepository tenantRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private FailedLoginAttemptRepository attemptRepository;
	@Autowired private PasswordEncoder passwordEncoder;

	private Tenant tenant;
	private User student;
	private final String tenantSlug = "lockout-" + UUID.randomUUID().toString().substring(0, 8);
	private final org.springframework.web.client.RestTemplate http =
			new org.springframework.web.client.RestTemplate(
					new org.springframework.http.client.SimpleClientHttpRequestFactory());
	private int requestNumber;

	private static final String EMAIL = "victim@lockout.test";
	private static final String RAW_PWD = "EduShift2026!";

	@BeforeEach
	void setUp() {
		tenant = newTestTenant();
		tenant.setName("Lockout Test Tenant");
		tenant.setSlug(tenantSlug);
		tenant.setStatus(TenantStatus.ACTIVE);
		tenant = tenantRepository.saveAndFlush(tenant);
		com.edushift.shared.multitenancy.TenantContext.set(tenant.getId());

		student = new User();
		student.setPublicUuid(UUID.randomUUID());
		student.setEmail(EMAIL);
		student.setFirstName("Victim");
		student.setLastName("Test");
		student.setRoles(new String[]{UserRole.STUDENT.name()});
		student.setStatus(UserStatus.ACTIVE);
		student.setEmailVerified(true);
		student.setPasswordHash(passwordEncoder.encode(RAW_PWD));
		student = com.edushift.shared.multitenancy.TenantContext.runAs(
				tenant.getId(), () -> userRepository.saveAndFlush(student));
	}

	@org.junit.jupiter.api.AfterEach
	void clearTenant() {
		com.edushift.shared.multitenancy.TenantContext.clear();
	}

	private ResponseEntity<String> attemptLogin(String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Tenant-Slug", tenantSlug);
		// Keep this test focused on per-account lockout. The application also
		// rate-limits by IP, so each simulated source uses its own address.
		headers.set("X-Forwarded-For", "198.19.0." + (++requestNumber));

		String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
		HttpEntity<String> req = new HttpEntity<>(body, headers);
		try {
			return http.exchange(
					"http://localhost:" + port + "/api/v1/auth/login",
					HttpMethod.POST,
					req,
					String.class);
		}
		catch (HttpStatusCodeException e) {
			return new ResponseEntity<>(e.getResponseBodyAsString(),
					e.getResponseHeaders(), e.getStatusCode());
		}
	}

	@Test
	@DisplayName("DEBT-AUTH-7: 5 wrong-password attempts → 6th try returns 429 with Retry-After")
	void fiveFailuresLockTheAccount() {
		// Burn 5 failed attempts — none of these lock yet.
		for (int i = 0; i < FailedLoginAttempt.MAX_ATTEMPTS_BEFORE_LOCK; i++) {
			ResponseEntity<String> r = attemptLogin("wrong-password");
			assertThat(r.getStatusCode().value()).isIn(HttpStatus.UNAUTHORIZED.value());
		}

		// The 6th attempt: lock should be active now.
		ResponseEntity<String> r = attemptLogin("wrong-password");
		assertThat(r.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
		String retryAfter = r.getHeaders().getFirst("Retry-After");
		assertThat(retryAfter).isNotNull();
		assertThat(Integer.parseInt(retryAfter)).isBetween(1, 900);

		// Even valid creds are now rejected.
		ResponseEntity<String> rValid = attemptLogin(RAW_PWD);
		assertThat(rValid.getStatusCode().value())
				.isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
	}

	@Test
	@DisplayName("DEBT-AUTH-7: 4 failures + 1 success → counter cleared, login works again")
	void successClearsCounterBeforeLock() {
		for (int i = 0; i < 4; i++) {
			attemptLogin("wrong");
		}

		// The 5th attempt is a SUCCESS — should clear the counter.
		ResponseEntity<String> r = attemptLogin(RAW_PWD);
		// May be 200 (success) or 401 (counter was already at 4 and now incremented to 5 → lock).
		// Actually: with 4 failures, the 5th attempt is the threshold. If wrong → lock.
		// If RIGHT → success + clear.

		assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(attemptLogin(RAW_PWD).getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
