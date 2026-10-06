package com.edushift.modules.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.config.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OllamaLlmClientTest {

    private OllamaProperties props;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        props = new OllamaProperties();
        props.setEnabled(true);
        props.setBaseUrl("http://localhost:11434/v1");
        props.setDefaultModel("gemma4:e2b");
        props.setTimeout(Duration.ofSeconds(5));
        props.setMaxRetries(0);
        objectMapper = new ObjectMapper();
    }

    private OllamaLlmClient newClient(HttpClient stubbedHttp) {
        OllamaLlmClient client = new OllamaLlmClient(props, objectMapper);
        client.setHelperForTesting(new OpenAiHttpHelper(
                objectMapper,
                props.getBaseUrl(),
                "ollama",
                null,
                Map.of(),
                props.getTimeout(),
                stubbedHttp
        ));
        return client;
    }

    @Test
    @DisplayName("providerId is ollama")
    void providerId() {
        assertThat(newClient(HttpClient.newHttpClient()).providerId()).isEqualTo("ollama");
    }

    @Test
    @DisplayName("posts to the local OpenAI-compatible endpoint without an Authorization header")
    @SuppressWarnings("unchecked")
    void happyPathOmitsAuthAndStripsThink() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "model": "gemma4:e2b",
                  "choices": [
                    { "index": 0, "message": {"role": "assistant", "content": "<think>hidden</think>hola"} }
                  ],
                  "usage": {"prompt_tokens": 4, "completion_tokens": 1}
                }
                """);
        org.mockito.Mockito.doReturn(response).when(http).send(any(HttpRequest.class), any());

        var resp = newClient(http).complete(new LlmClient.LlmRequest(
                "gemma4:e2b",
                "Eres EduShift.",
                "Di hola.",
                0.2,
                128,
                null,
                Map.of("thinking", Map.of("type", "disabled"))
        ));

        assertThat(resp.text()).isEqualTo("hola");
        assertThat(resp.model()).isEqualTo("gemma4:e2b");
        assertThat(resp.tokensIn()).isEqualTo(4);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertThat(req.uri().toString()).isEqualTo("http://localhost:11434/v1/chat/completions");
        assertThat(req.headers().firstValue("Authorization")).isEmpty();
        assertThat(req.headers().firstValue("Content-Type")).hasValue("application/json");
    }
}
