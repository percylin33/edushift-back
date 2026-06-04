package com.edushift.shared.constants;

/**
 * Canonical SLF4J logger names for cross-cutting categories.
 * <p>
 * Routed in {@code logback-spring.xml} to dedicated appenders / files with
 * independent rotation and retention policies.
 */
public final class LoggerNames {

	/** Authentication, authorization, JWT, ACL and security-sensitive events. */
	public static final String SECURITY = "edushift.security";

	/** HTTP access log: method, path, status, duration, ip. */
	public static final String REQUESTS = "edushift.requests";

	/** Handled and unhandled exceptions raised by the API. */
	public static final String EXCEPTIONS = "edushift.exceptions";

	/** AI module: OpenRouter calls, latency, tokens, retries. */
	public static final String AI = "edushift.ai";

	/** Persisted audit events (mirrored to file for compliance retention). */
	public static final String AUDIT = "edushift.audit";

	private LoggerNames() {
	}

}
