package com.edushift.modules.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.config.MiniMaxProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link MiniMaxLlmClient} (BE-7c.1.1).
 *
 * <p>Exercises the retry loop and the OpenAI-compatible wire shape
 * against a Mockito-stubbed {@link HttpClient} (no real HTTP, no
 * network). The test injects the stubbed client via the
 * {@link MiniMaxLlmClient#setHelperForTesting} package-private seam,
 * so the production constructor (which Spring uses) stays single and
 * unambiguous.</p>
 *
 * <p>Note: the wire shape is identical to OpenRouter's (because
 * MiniMax's API Platform is OpenAI-compatible), so we lean on the
 * shared {@link OpenAiHttpHelper} rather than re-testing the JSON
 * parser — that coverage is owned by the (unchanged) OpenRouter
 * spec. The MiniMax-specific assertions are: provider id, auth
 * header, base URL, and retry policy.</p>
 */
class MiniMaxLlmClientTest {

    private MiniMaxProperties props;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        props = new MiniMaxProperties();
        props.setApiKey("test-minimax-key");
        props.setBaseUrl("https://api.minimax.chat/v1");
        props.setDefaultModel("MiniMax-M3");
        props.setTimeout(Duration.ofSeconds(5));
        props.setMaxRetries(2);
        objectMapper = new ObjectMapper();
    }

    /** Build a {@link MiniMaxLlmClient} with a stubbed {@link HttpClient}
     *  via the package-private {@code setHelperForTesting} seam. */
    private MiniMaxLlmClient newClientWithStubbedHttp(HttpClient stubbedHttp) {
        MiniMaxLlmClient client = new MiniMaxLlmClient(props, objectMapper);
        OpenAiHttpHelper helper = new OpenAiHttpHelper(
                objectMapper,
                props.getBaseUrl(),
                "minimax",
                "Bearer " + props.getApiKey(),
                java.util.Map.of(),
                props.getTimeout(),
                stubbedHttp
        );
        client.setHelperForTesting(helper);
        return client;
    }

    @Test
    @DisplayName("providerId() returns 'minimax' (lowercase) for stable audit keys")
    void providerIdIsLowercase() {
        var client = newClientWithStubbedHttp(HttpClient.newHttpClient());
        assertThat(client.providerId()).isEqualTo("minimax");
    }

    @Test
    @DisplayName("happy path: sends POST to /chat/completions with Bearer auth + body, returns the model's text")
    @SuppressWarnings("unchecked")
    void happyPath() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "id": "cmpl-1",
                  "model": "MiniMax-M3",
                  "choices": [
                    { "index": 0, "message": {"role": "assistant", "content": "{\\"questions\\":[]}"} , "finish_reason": "stop" }
                  ],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 22, "total_tokens": 33}
                }
                """);
        org.mockito.Mockito.doReturn(response).when(http).send(any(HttpRequest.class), any());

        var client = newClientWithStubbedHttp(http);
        var resp = client.complete(new LlmClient.LlmRequest(
                "MiniMax-M3",
                "You are EduShift AI.",
                "Suggest a question about fracciones.",
                0.2,
                2048,
                null,
                null
        ));

        assertThat(resp.text()).contains("questions");
        assertThat(resp.model()).isEqualTo("MiniMax-M3");
        assertThat(resp.tokensIn()).isEqualTo(11);
        assertThat(resp.tokensOut()).isEqualTo(22);
        assertThat(resp.latencyMs()).isGreaterThanOrEqualTo(0L);

        // Verify the request shape: URL, Authorization header, body has model.
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertThat(req.uri().toString()).isEqualTo("https://api.minimax.chat/v1/chat/completions");
        assertThat(req.headers().firstValue("Authorization")).hasValue("Bearer test-minimax-key");
        assertThat(req.headers().firstValue("Content-Type")).hasValue("application/json");
    }

    @Test
    @DisplayName("429 → retried up to maxRetries, then throws LlmException(RATE_LIMITED)")
    @SuppressWarnings("unchecked")
    void retriesOn429() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"error\":\"rate limited\"}");
        org.mockito.Mockito.doReturn(response).when(http).send(any(HttpRequest.class), any());

        var client = newClientWithStubbedHttp(http);
        assertThatThrownBy(() -> client.complete(new LlmClient.LlmRequest(
                "MiniMax-M3", "sys", "user", 0.2, 1024, null, null
        )))
                .isInstanceOf(LlmException.class)
                .satisfies(t -> assertThat(((LlmException) t).getCode()).isEqualTo(LlmException.RATE_LIMITED));

        // maxRetries=2 → 3 total attempts.
        verify(http, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("401 → does NOT retry (permanent), throws LlmException(AUTH)")
    @SuppressWarnings("unchecked")
    void noRetryOn401() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":\"bad key\"}");
        org.mockito.Mockito.doReturn(response).when(http).send(any(HttpRequest.class), any());

        var client = newClientWithStubbedHttp(http);
        assertThatThrownBy(() -> client.complete(new LlmClient.LlmRequest(
                "MiniMax-M3", "sys", "user", 0.2, 1024, null, null
        )))
                .isInstanceOf(LlmException.class)
                .satisfies(t -> assertThat(((LlmException) t).getCode()).isEqualTo(LlmException.AUTH));

        verify(http, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("timeout → retried, eventually throws LlmException(TIMEOUT)")
    @SuppressWarnings("unchecked")
    void retriesOnTimeout() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("simulated timeout"));

        var client = newClientWithStubbedHttp(http);
        assertThatThrownBy(() -> client.complete(new LlmClient.LlmRequest(
                "MiniMax-M3", "sys", "user", 0.2, 1024, null, null
        )))
                .isInstanceOf(LlmException.class)
                .satisfies(t -> assertThat(((LlmException) t).getCode()).isEqualTo(LlmException.TIMEOUT));

        verify(http, times(3)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("empty choices array → LlmException(EMPTY_RESPONSE), no retry")
    @SuppressWarnings("unchecked")
    void emptyChoices() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                { "id": "x", "model": "MiniMax-M3", "choices": [], "usage": null }
                """);
        org.mockito.Mockito.doReturn(response).when(http).send(any(HttpRequest.class), any());

        var client = newClientWithStubbedHttp(http);
        assertThatThrownBy(() -> client.complete(new LlmClient.LlmRequest(
                "MiniMax-M3", "sys", "user", 0.2, 1024, null, null
        )))
                .isInstanceOf(LlmException.class)
                .satisfies(t -> assertThat(((LlmException) t).getCode()).isEqualTo(LlmException.EMPTY_RESPONSE));

        verify(http, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("empty API key → IllegalStateException at construction time")
    void emptyApiKeyFailsFast() {
        props.setApiKey("");
        assertThatThrownBy(() -> new MiniMaxLlmClient(props, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key is empty");
    }
}
