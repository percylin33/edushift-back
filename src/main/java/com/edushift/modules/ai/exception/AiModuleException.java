package com.edushift.modules.ai.exception;

import com.edushift.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Base of the AI module's exception hierarchy (BE-7c.1). Extends
 * {@link ApiException} so the {@code GlobalExceptionHandler}
 * (see {@code shared/exception}) picks it up and translates it
 * to an {@code ApiErrorResponse} without any extra wiring.
 *
 * <p>Subclasses bind an HTTP {@link HttpStatus} and a stable
 * error code. The {@code message} is human-readable (Spanish or
 * English — the FE renders the code, the human reads the message).</p>
 */
public abstract class AiModuleException extends ApiException {

    protected AiModuleException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    protected AiModuleException(HttpStatus status, String code, String message, Throwable cause) {
        super(status, code, message, cause);
    }
}
