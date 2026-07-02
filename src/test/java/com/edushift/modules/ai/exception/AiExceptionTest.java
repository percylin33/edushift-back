package com.edushift.modules.ai.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.ai.llm.LlmException;
import com.edushift.shared.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiExceptionTest {

    @Test
    @DisplayName("AiDisabledException → 403 AI_DISABLED")
    void disabled() {
        var ex = new AiDisabledException();
        assertThat(ex).isInstanceOf(AiModuleException.class).isInstanceOf(ApiException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo("AI_DISABLED");
        assertThat(ex.getMessage()).contains("AI assistant is disabled");
    }

    @Test
    @DisplayName("AiQuotaExceededException → 429 AI_QUOTA_EXCEEDED")
    void quota() {
        var ex = new AiQuotaExceededException("Daily quota exhausted");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getCode()).isEqualTo(AiQuotaExceededException.CODE);
        assertThat(ex.getMessage()).isEqualTo("Daily quota exhausted");
    }

    @Test
    @DisplayName("AiRateLimitedException → 429 AI_RATE_LIMITED")
    void rateLimited() {
        var ex = new AiRateLimitedException("Hourly cap reached");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getCode()).isEqualTo("AI_RATE_LIMITED");
    }

    @Test
    @DisplayName("AiParseException → 502 AI_PARSE_ERROR")
    void parse() {
        var ex = new AiParseException("LLM returned no 'questions' array");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getCode()).isEqualTo("AI_PARSE_ERROR");
    }

    @Test
    @DisplayName("AiParseException with cause chains the underlying")
    void parseWithCause() {
        var cause = new RuntimeException("io");
        var ex = new AiParseException("parse failed", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("AiGenerationNotFoundException → 404 with the uuid in the message")
    void generationNotFound() {
        UUID id = UUID.randomUUID();
        var ex = new AiGenerationNotFoundException(id);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("AI_GENERATION_NOT_FOUND");
        assertThat(ex.getMessage()).contains(id.toString());
    }

    @Test
    @DisplayName("ChatSessionNotFoundException → 404 CHAT_SESSION_NOT_FOUND")
    void chatSessionNotFound() {
        var ex = new ChatSessionNotFoundException();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo("CHAT_SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("ChatSessionNotActiveException → 409 CHAT_SESSION_NOT_ACTIVE")
    void chatSessionNotActive() {
        var ex = new ChatSessionNotActiveException();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo("CHAT_SESSION_NOT_ACTIVE");
    }

    @Test
    @DisplayName("LlmException maps the well-known codes to HTTP statuses")
    void llmException() {
        assertThat(new LlmException(LlmException.AUTH, "x").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new LlmException(LlmException.QUOTA, "x").getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(new LlmException(LlmException.BAD_REQUEST, "x").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new LlmException(LlmException.RATE_LIMITED, "x").getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(new LlmException(LlmException.TIMEOUT, "x").getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(new LlmException(LlmException.NETWORK, "x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(new LlmException(LlmException.UPSTREAM, "x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(new LlmException(LlmException.EMPTY_RESPONSE, "x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(new LlmException(LlmException.DISABLED, "x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        // Unknown code → fallback 502
        assertThat(new LlmException("SOMETHING_NEW", "x").getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("LlmException with cause chains the underlying")
    void llmWithCause() {
        var cause = new RuntimeException("io");
        var ex = new LlmException(LlmException.TIMEOUT, "boom", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}