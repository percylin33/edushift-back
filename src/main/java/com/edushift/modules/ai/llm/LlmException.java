package com.edushift.modules.ai.llm;

import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Terminal failure from an {@link LlmClient} (BE-7c.1).
 *
 * <p>Extends {@link ApiException} so the
 * {@code GlobalExceptionHandler} picks it up and translates it
 * to a proper {@code ApiErrorResponse}. Each code has a fixed
 * HTTP status (see the static code constants).</p>
 */
public class LlmException extends ApiException {

    public static final String TIMEOUT        = "LLM_TIMEOUT";
    public static final String NETWORK        = "LLM_NETWORK";
    public static final String RATE_LIMITED   = "LLM_RATE_LIMITED";
    public static final String AUTH           = "LLM_AUTH";
    public static final String QUOTA          = "LLM_QUOTA";
    public static final String BAD_REQUEST    = "LLM_BAD_REQUEST";
    public static final String UPSTREAM       = "LLM_UPSTREAM";
    public static final String EMPTY_RESPONSE = "LLM_EMPTY_RESPONSE";
    public static final String DISABLED       = "LLM_DISABLED";

    public LlmException(String code, String message) {
        super(httpStatusFor(code), code, message);
    }

    public LlmException(String code, String message, Throwable cause) {
        super(httpStatusFor(code), code, message, cause);
    }

    private static HttpStatus httpStatusFor(String code) {
        return switch (code) {
            // 4xx — the request itself is wrong (or auth/quota is the caller's problem).
            case AUTH           -> HttpStatus.UNAUTHORIZED;
            case QUOTA          -> HttpStatus.PAYMENT_REQUIRED;
            case BAD_REQUEST    -> HttpStatus.BAD_REQUEST;
            case RATE_LIMITED   -> HttpStatus.TOO_MANY_REQUESTS;
            // 5xx — the upstream (LLM provider) failed.
            case TIMEOUT        -> HttpStatus.GATEWAY_TIMEOUT;
            case NETWORK, UPSTREAM, EMPTY_RESPONSE, DISABLED -> HttpStatus.BAD_GATEWAY;
            default             -> HttpStatus.BAD_GATEWAY;
        };
    }
}
