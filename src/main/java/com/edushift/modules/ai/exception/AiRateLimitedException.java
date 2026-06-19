package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * AI rate limit exceeded (Sprint 8 / SEC-8.1).
 *
 * <p>Thrown by {@code RateLimitService} when a user has exceeded the
 * per-user hourly or daily cap on AI generations. Maps to HTTP 429
 * with code {@code AI_RATE_LIMITED}.</p>
 */
public class AiRateLimitedException extends AiModuleException {

    public AiRateLimitedException(String reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", reason);
    }
}
