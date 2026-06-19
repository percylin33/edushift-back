package com.edushift.modules.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.ai.llm.LlmClient.LlmRequest;
import com.edushift.modules.ai.llm.LlmClient.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MockLlmClient} (BE-7c.1).
 *
 * <p>Verifies the dev/test default: returns a valid JSON stub
 * that the {@code LmsAiService} can parse into suggestions, the
 * provider id is {@code "mock"}, and the math topic gets the
 * "fracciones" specialised stub.</p>
 */
class MockLlmClientTest {

    private final MockLlmClient client = new MockLlmClient();

    @Test
    @DisplayName("providerId: returns 'mock'")
    void providerIdIsMock() {
        assertThat(client.providerId()).isEqualTo("mock");
    }

    @Test
    @DisplayName("complete: returns non-blank text + model + token counts")
    void completeReturnsTextModelTokens() {
        LlmRequest req = new LlmRequest(
                "openai/gpt-4o-mini",
                "system",
                "TEMA: Algo",
                0.2, 1024, null, null
        );
        LlmResponse resp = client.complete(req);
        assertThat(resp.text()).isNotBlank();
        assertThat(resp.model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(resp.tokensIn()).isEqualTo(42);
        assertThat(resp.tokensOut()).isEqualTo(28);
    }

    @Test
    @DisplayName("complete: text parses as JSON with a 'questions' array (default stub)")
    void completeTextIsValidJson() {
        LlmResponse resp = client.complete(new LlmRequest(
                "m", "s", "TEMA: General Knowledge", 0.2, 1024, null, null));
        // The stub is a pretty multi-line JSON. The trim+lastIndexOf pattern
        // locates the actual last closing brace (the one closing the root '{').
        String trimmed = resp.text().stripTrailing();
        assertThat(trimmed).startsWith("{");
        assertThat(trimmed.lastIndexOf("}")).isEqualTo(trimmed.length() - 1);
        assertThat(resp.text()).contains("\"questions\"");
        assertThat(resp.text()).contains("París").contains("\"type\": \"TF\"");
    }

    @Test
    @DisplayName("complete: 'fracciones' topic switches to the math stub (1 question)")
    void completeFraccionesStub() {
        LlmResponse resp = client.complete(new LlmRequest(
                "m", "s", "TEMA: Suma de fracciones", 0.2, 1024, null, null));
        assertThat(resp.text()).contains("¿Cuánto es 1/2 + 1/4?");
        assertThat(resp.text()).doesNotContain("capital de Francia");
    }

    @Test
    @DisplayName("complete: extra map in request is ignored (mock doesn't use it)")
    void completeIgnoresExtra() {
        LlmResponse resp = client.complete(new LlmRequest(
                "m", "s", "TEMA: X", 0.2, 1024, null,
                Map.of("response_format", List.of("json_object"))));
        assertThat(resp.text()).isNotBlank();
    }
}
