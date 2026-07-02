package com.edushift.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletOutputStream;

/**
 * Sprint 14 (MVP Closure) / DEBT-TEN-6 — pure unit test for the rate
 * limit interceptor. The full integration is exercised by
 * {@code TenantRegistrationRateLimitIT}; here we focus on the in-memory
 * counter (5 req / 60 min).
 */
class RateLimitInterceptorTest {

	private RateLimitInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new RateLimitInterceptor(new ObjectMapper());
	}

	@Test
	@DisplayName("DEBT-TEN-6: 5 sequential requests pass; 6th returns 429")
	void sixthRequestIsBlocked() throws Exception {
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			HttpServletRequest req = mockRequest("1.2.3.4", "/v1/tenants/register");
			HttpServletResponse res = mockResponse();
			boolean allowed = interceptor.preHandle(req, res, new Object());
			assertThat(allowed).as("request #%d should be allowed", i + 1).isTrue();
		}

		HttpServletRequest req = mockRequest("1.2.3.4", "/v1/tenants/register");
		StringWriter body = new StringWriter();
		HttpServletResponse res = mockResponseWithWriter(body);
		boolean allowed = interceptor.preHandle(req, res, new Object());
		assertThat(allowed).isFalse();
		assertThat(body.toString()).contains("RATE_LIMITED");
	}

	@Test
	@DisplayName("DEBT-TEN-6: different IPs have independent counters")
	void differentIpsHaveIndependentCounters() throws Exception {
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			interceptor.preHandle(mockRequest("1.1.1.1", "/v1/tenants/register"),
					mockResponse(), new Object());
		}
		// ip=1.1.1.1 is now exhausted (and would 429 on the 6th call).
		// A different IP must still be on counter=1.
		HttpServletRequest req = mockRequest("2.2.2.2", "/v1/tenants/register");
		HttpServletResponse res = mockResponse();
		boolean allowed = interceptor.preHandle(req, res, new Object());
		assertThat(allowed).isTrue();
	}

	@Test
	@DisplayName("DEBT-TEN-6: honors X-Forwarded-For header (reverse proxy)")
	void honorsXForwardedFor() throws Exception {
		for (int i = 0; i < RateLimitInterceptor.MAX_REQUESTS_PER_WINDOW; i++) {
			HttpServletRequest req = mockRequestWithXff("internal-ip", "203.0.113.42",
					"/v1/tenants/register");
			interceptor.preHandle(req, mockResponse(), new Object());
		}
		HttpServletRequest req = mockRequestWithXff("internal-ip", "203.0.113.42",
				"/v1/tenants/register");
		StringWriter body = new StringWriter();
		boolean allowed = interceptor.preHandle(req, mockResponseWithWriter(body), new Object());
		assertThat(allowed).isFalse();
	}

	private HttpServletRequest mockRequest(String ip, String path) {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRemoteAddr()).thenReturn(ip);
		when(req.getRequestURI()).thenReturn(path);
		when(req.getHeader("X-Forwarded-For")).thenReturn(null);
		when(req.getHeader("X-Real-IP")).thenReturn(null);
		return req;
	}

	private HttpServletRequest mockRequestWithXff(String remoteAddr, String xff, String path) {
		HttpServletRequest req = mockRequest(remoteAddr, path);
		when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
		return req;
	}

	private HttpServletResponse mockResponse() throws Exception {
		HttpServletResponse res = mock(HttpServletResponse.class);
		return res;
	}

	private HttpServletResponse mockResponseWithWriter(StringWriter sink) throws Exception {
		HttpServletResponse res = mock(HttpServletResponse.class);
		PrintWriter pw = new PrintWriter(sink);
		when(res.getWriter()).thenReturn(pw);
		return res;
	}
}
