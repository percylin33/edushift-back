package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * Chat session not found (Sprint 8 / BE-8.3). Thrown by
 * {@code ChatService} when a publicUuid doesn't resolve to a
 * session in the current tenant (anti-enumeration: also thrown
 * when the session belongs to another user in the same tenant, so
 * the API never reveals "this uuid belongs to someone else").
 *
 * <p>Maps to HTTP 404 with code {@code CHAT_SESSION_NOT_FOUND}.
 * The {@code AiExceptionHandler} translates it to the standard
 * {@code ApiErrorResponse} shape.
 */
public class ChatSessionNotFoundException extends AiModuleException {

    public ChatSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND",
                "Chat session not found in the current tenant");
    }
}
