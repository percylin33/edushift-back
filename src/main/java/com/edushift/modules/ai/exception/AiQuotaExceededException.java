package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the tenant has exhausted its daily request quota
 * OR its monthly token quota (BE-7c.1). Maps to HTTP 429.
 */
public class AiQuotaExceededException extends AiModuleException {

    public static final String CODE = "AI_QUOTA_EXCEEDED";

    public AiQuotaExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, CODE, message);
    }
}
