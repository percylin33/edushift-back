package com.edushift.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request and exposes it in:
 * <ul>
 *   <li>MDC key {@value #MDC_CORRELATION_ID} (consumed by Logback patterns)</li>
 *   <li>MDC key {@value #MDC_TRACE_ID} as a mirror, for compatibility with
 *       {@code GlobalExceptionHandler} and Micrometer Tracing patterns</li>
 *   <li>Response header {@value #HEADER} so the client can echo it back</li>
 * </ul>
 * Resolution order:
 * <ol>
 *   <li>Incoming {@value #HEADER} header (propagated from upstream/gateway)</li>
 *   <li>Existing MDC {@code traceId} (Micrometer Tracing)</li>
 *   <li>New short id ({@code UUIDv4} first 8 hex chars)</li>
 * </ol>
 * MDC is cleared in {@code finally} to prevent leaks across pooled threads.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Correlation-Id";

	public static final String MDC_CORRELATION_ID = "correlationId";

	public static final String MDC_TRACE_ID = "traceId";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		String correlationId = resolve(request);
		MDC.put(MDC_CORRELATION_ID, correlationId);
		if (MDC.get(MDC_TRACE_ID) == null) {
			MDC.put(MDC_TRACE_ID, correlationId);
		}
		response.setHeader(HEADER, correlationId);

		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_CORRELATION_ID);
			MDC.remove(MDC_TRACE_ID);
		}
	}

	private String resolve(HttpServletRequest request) {
		String incoming = request.getHeader(HEADER);
		if (incoming != null && !incoming.isBlank()) {
			return incoming.trim();
		}
		String existing = MDC.get(MDC_TRACE_ID);
		if (existing != null && !existing.isBlank()) {
			return existing;
		}
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
