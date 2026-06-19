package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * Chat session is not in the {@code ACTIVE} state (Sprint 8 / BE-8.3).
 * Sent to a session that is {@code ARCHIVED} or {@code DELETED}.
 *
 * <p>Maps to HTTP 409 with code {@code CHAT_SESSION_NOT_ACTIVE}.
 */
public class ChatSessionNotActiveException extends AiModuleException {

    public ChatSessionNotActiveException() {
        super(HttpStatus.CONFLICT, "CHAT_SESSION_NOT_ACTIVE",
                "This chat session is archived or deleted; create a new one");
    }
}
