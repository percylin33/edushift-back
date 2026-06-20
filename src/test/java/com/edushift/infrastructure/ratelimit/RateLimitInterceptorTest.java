package com.edushift.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitInterceptor}. Covers the public-tenant-signup
 * rate-limit policy (DEBT-TEN-6): 5 requests per hour per IP.
 */
class RateLimitInterceptorTest {

	private RateLimitInterceptor interceptor;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		interceptor = new RateLimitInterceptor(objectMapper);
	}

	private HttpServletRequest requestWithIp(String ip, String forwardedFor) {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRemoteAddr()).thenReturn(ip);
		if (forwardedFor != null) {
			when(req.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
		}
		when(req.getRequestURI()).thenReturn("/v1/tenants/register");
		return req;
	}

	@Test
	@DisplayName("Allows the first 5 requests in the window for one IP")
	void allowsUpToLimit() throws Exception {
		String ip = "203.0.113.10";
		HttpServletRequest req = requestWithIp(ip, null);

		for (int i = 1; i <= RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			MockHttpServletResponse resp = new MockHttpServletResponse();
			boolean allowed = interceptor.preHandle(req, resp, new Object());
			assertThat(allowed)
					.as("request #%d in window should be allowed", i)
					.isTrue();
			assertThat(resp.getStatus()).isEqualTo(200);
		}
	}

	@Test
	@DisplayName("6th request from the same IP in the window is REJECTED with 429 + RATE_LIMITED")
	void rejectsOverLimit() throws Exception {
		String ip = "203.0.113.20";
		HttpServletRequest req = requestWithIp(ip, null);

		// Burn through the limit
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		}

		// The 6th must be blocked
		MockHttpServletResponse resp = new MockHttpServletResponse();
		boolean allowed = interceptor.preHandle(req, resp, new Object());
		assertThat(allowed).isFalse();
		assertThat(resp.getStatus()).isEqualTo(429);
		assertThat(resp.getContentType()).contains("application/json");
		assertThat(resp.getContentAsString()).contains("RATE_LIMITED");
	}

	@Test
	@DisplayName("Different IPs are isolated (IP A's quota does not affect IP B)")
	void differentIpsAreIndependent() throws Exception {
		HttpServletRequest reqA = requestWithIp("198.51.100.1", null);
		HttpServletRequest reqB = requestWithIp("198.51.100.2", null);

		// Exhaust A
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(reqA, new MockHttpServletResponse(), new Object());
		}
		MockHttpServletResponse blockedA = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(reqA, blockedA, new Object())).isFalse();

		// B should still be allowed
		MockHttpServletResponse allowedB = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(reqB, allowedB, new Object())).isTrue();
		assertThat(allowedB.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("X-Forwarded-For (first hop) is honored when behind a reverse proxy")
	void honorsXForwardedFor() throws Exception {
		String realClientIp = "192.0.2.42";
		HttpServletRequest req = requestWithIp("10.0.0.1", realClientIp + ", 10.0.0.1");

		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		}
		MockHttpServletResponse blocked = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(req, blocked, new Object())).isFalse();
		assertThat(blocked.getStatus()).isEqualTo(429);
	}

	@Test
	@DisplayName("X-Forwarded-For with a single IP is honored verbatim")
	void honorsXForwardedForSingle() throws Exception {
		String realClientIp = "192.0.2.99";
		HttpServletRequest req = requestWithIp("10.0.0.1", realClientIp);

		// Burn through with the XFF IP
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		}
		MockHttpServletResponse blocked = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(req, blocked, new Object())).isFalse();
	}

	@Test
	@DisplayName("Fallback to request.getRemoteAddr() when no X-Forwarded-For header present")
	void fallsBackToRemoteAddr() throws Exception {
		String ip = "203.0.113.50";
		HttpServletRequest req = requestWithIp(ip, null);

		// Burn through with the direct remote addr
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		}
		MockHttpServletResponse blocked = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(req, blocked, new Object())).isFalse();
	}

	@Test
	@DisplayName("Null remoteAddr is treated as 'unknown' rather than NPE")
	void nullRemoteAddrDoesNotThrow() throws Exception {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRemoteAddr()).thenReturn(null);
		when(req.getRequestURI()).thenReturn("/v1/tenants/register");

		// Should not throw; first request is always allowed.
		boolean allowed = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		assertThat(allowed).isTrue();
	}

	@Test
	@DisplayName("Response body is the standard ApiResponse<T> error envelope with RATE_LIMITED code")
	void responseBodyIsApiResponseShape() throws Exception {
		String ip = "203.0.113.60";
		HttpServletRequest req = requestWithIp(ip, null);

		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
		}

		MockHttpServletResponse resp = new MockHttpServletResponse();
		interceptor.preHandle(req, resp, new Object());

		String body = resp.getContentAsString();
		assertThat(body)
				.contains("\"success\":false")
				.contains("RATE_LIMITED")
				.contains("\"timestamp\"");
	}
}
