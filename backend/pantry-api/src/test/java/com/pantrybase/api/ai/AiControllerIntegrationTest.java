package com.pantrybase.api.ai;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the HTTP contract of the AI endpoint when the provider chain is unreachable.
 *
 * <p>The test context disables the AI auto-configuration, so no {@code ChatModel} is
 * present, {@link FallbackChatClient} resolves an empty chain and every call fails —
 * exactly the Ollama-down situation the endpoint must report as 503, not 500.</p>
 */
class AiControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String RECOMMEND = "/api/ai/recommend";

    @Test
    void recommend_withoutReachableProvider_returnsServiceUnavailable() {
        HttpHeaders headers = authenticate();

        ResponseEntity<ErrorResponse> response =
                rest.exchange(RECOMMEND, HttpMethod.POST, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, RECOMMEND);
    }

    @Test
    void recommend_withoutAuthentication_isUnauthorized() {
        assertUnauthorized(RECOMMEND, HttpMethod.POST);
    }
}
