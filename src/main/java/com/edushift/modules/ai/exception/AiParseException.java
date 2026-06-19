package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the LLM response cannot be parsed into the
 * {@code QuestionSuggestion} shape (BE-7c.1). Maps to HTTP 502
 * (the LLM is the upstream we depend on; if it gives us garbage
 * we surface a 502 to the client).
 */
public class AiParseException extends AiModuleException {

    public static final String CODE = "AI_PARSE_ERROR";

    public AiParseException(String message) {
        super(HttpStatus.BAD_GATEWAY, CODE, message);
    }

    public AiParseException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, CODE, message, cause);
    }
}
