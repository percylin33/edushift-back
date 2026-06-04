package com.edushift.infrastructure.web;

import com.edushift.shared.constants.LoggerNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-request access log: method, path, status, duration, client IP, user-agent.
 * <p>
 * Emits one INFO line per request through the {@code edushift.requests} logger
 * (routed to its own rolling file in production). Skips noisy paths (actuator,
 * error dispatch).
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(LoggerNames.REQUESTS);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path == null
				|| path.contains("/actuator/")
				|| path.equals("/error")
				|| path.endsWith("/favicon.ico");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			long durationMs = (System.nanoTime() - start) / 1_000_000L;
			log.info("request method={} path={} query=\"{}\" status={} durationMs={} ip={} ua=\"{}\"",
					request.getMethod(),
					request.getRequestURI(),
					nullToEmpty(request.getQueryString()),
					response.getStatus(),
					durationMs,
					clientIp(request),
					truncate(request.getHeader("User-Agent"), 200));
		}
	}

	private static String clientIp(HttpServletRequest req) {
		String header = req.getHeader("X-Forwarded-For");
		if (header != null && !header.isBlank()) {
			int comma = header.indexOf(',');
			return (comma > 0 ? header.substring(0, comma) : header).trim();
		}
		return req.getRemoteAddr();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() > max ? value.substring(0, max) : value;
	}

}
