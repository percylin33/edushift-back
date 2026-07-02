package com.edushift.modules.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmClientRecordsTest {

    @Test
    @DisplayName("LlmRequest rejects null/blank model, systemPrompt, userPrompt")
    void requestValidation() {
        assertThatThrownBy(() -> new LlmClient.LlmRequest(null, "sys", "user",
                null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model is required");
        assertThatThrownBy(() -> new LlmClient.LlmRequest("m", null, "user",
                null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemPrompt is required");
        assertThatThrownBy(() -> new LlmClient.LlmRequest("m", "sys", "  ",
                null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userPrompt is required");
    }

    @Test
    @DisplayName("LlmRequest has a 7-arg convenience constructor that defaults history to empty")
    void convenienceConstructor() {
        var req = new LlmClient.LlmRequest("m", "sys", "user",
                0.2, 1024, List.of("STOP"), Map.of("response_format", "json_object"));
        assertThat(req.model()).isEqualTo("m");
        assertThat(req.history()).isEmpty();
        assertThat(req.temperature()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("LlmResponse rejects null/blank text and null/blank model")
    void responseValidation() {
        assertThatThrownBy(() -> new LlmClient.LlmResponse(null, "m", 1, 2, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text is required");
        assertThatThrownBy(() -> new LlmClient.LlmResponse("ok", "", 1, 2, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model is required");
    }

    @Test
    @DisplayName("LlmResponse happy path")
    void responseHappy() {
        var r = new LlmClient.LlmResponse("ok", "MiniMax/MiniMax-M2", 1, 2, 150L);
        assertThat(r.text()).isEqualTo("ok");
        assertThat(r.model()).isEqualTo("MiniMax/MiniMax-M2");
        assertThat(r.tokensIn()).isEqualTo(1);
        assertThat(r.tokensOut()).isEqualTo(2);
        assertThat(r.latencyMs()).isEqualTo(150L);
    }

    @Test
    @DisplayName("HistoryItem stores role + content")
    void historyItem() {
        var h = new LlmClient.HistoryItem("user", "hola");
        assertThat(h.role()).isEqualTo("user");
        assertThat(h.content()).isEqualTo("hola");
    }

    @Test
    @DisplayName("WrappingObserver delegates to wrapped observer; onToken(false) cancels")
    void wrappingObserver() {
        boolean[] keepGoing = {true};
        var wrapped = new LlmClient.StreamObserver() {
            @Override public boolean onToken(String chunk) { return keepGoing[0]; }
            @Override public void onComplete() { /* noop */ }
            @Override public void onError(Throwable error) { /* noop */ }
        };
        var wrapping = new LlmClient.WrappingObserver(wrapped);
        assertThat(wrapping.onToken("x")).isTrue();
        keepGoing[0] = false;
        assertThat(wrapping.onToken("x")).isFalse();
        wrapping.onComplete();
        wrapping.onError(new RuntimeException());
    }

    @Test
    @DisplayName("WrappingObserver tolerates a null delegate (no NPE)")
    void nullDelegate() {
        var wrapping = new LlmClient.WrappingObserver(null);
        // onToken returns true when delegate is null
        assertThat(wrapping.onToken("x")).isTrue();
        wrapping.onComplete();           // noop
        wrapping.onError(new RuntimeException()); // noop
    }
}