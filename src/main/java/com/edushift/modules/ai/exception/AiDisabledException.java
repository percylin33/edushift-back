package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the AI assistant is disabled for the current tenant
 * (BE-7c.1). Maps to HTTP 403.
 */
public class AiDisabledException extends AiModuleException {

    public static final String CODE = "AI_DISABLED";

    public AiDisabledException() {
        super(HttpStatus.FORBIDDEN, CODE,
                "AI assistant is disabled for this tenant. "
                        + "An admin can enable it from the tenant settings panel.");
    }
}
