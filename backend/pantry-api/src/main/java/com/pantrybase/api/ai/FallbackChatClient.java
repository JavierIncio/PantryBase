package com.pantrybase.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class FallbackChatClient {

    private final Map<String, ChatClient> clientsByProvider;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public FallbackChatClient(List<ChatClient> clients,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry,
                              RateLimiterRegistry rateLimiterRegistry) {
        this.clientsByProvider = clients.stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getClass().getSimpleName().replace("ChatClientImpl", "").toLowerCase(),
                        c -> c
                ));
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    public String callTextWithFallback(Prompt prompt) {
        for (Map.Entry<String, ChatClient> entry : clientsByProvider.entrySet()) {
            String provider = entry.getKey();
            ChatClient client = entry.getValue();

            try {
                return executeWithResilience(provider, client, prompt);
            } catch (Exception e) {
                continue;
            }
        }
        throw new AiFallbackException("All AI providers failed");
    }

    public String callWithFunction(Prompt prompt, String functionName, ObjectNode functionSchema) {
        for (Map.Entry<String, ChatClient> entry : clientsByProvider.entrySet()) {
            String provider = entry.getKey();
            ChatClient client = entry.getValue();

            try {
                return executeWithFunction(provider, client, prompt, functionName, functionSchema);
            } catch (Exception e) {
                continue;
            }
        }
        throw new AiFallbackException("All AI providers failed");
    }

    private String executeWithResilience(String provider, ChatClient client, Prompt prompt) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
        Retry retry = retryRegistry.retry(provider);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(provider);

        Supplier<String> supplier = () -> callClient(client, prompt);

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(cb, () -> callClient(client, prompt));
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);

        return decoratedSupplier.get();
    }

    private String executeWithFunction(String provider, ChatClient client, Prompt prompt, String functionName, ObjectNode functionSchema) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
        Retry retry = retryRegistry.retry(provider);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(provider);

        ToolCallback toolCallback = FunctionToolCallback.builder(functionName, (input) -> {
            return input;
        })
                .description("Submit recipe recommendations")
                .inputSchema(functionSchema.toString())
                .build();

        Supplier<String> supplier = () -> {
            var call = client.prompt()
                    .messages(prompt.getInstructions())
                    .toolCallbacks(toolCallback)
                    .call();
            return call.content();
        };

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(
                circuitBreakerRegistry.circuitBreaker(provider), supplier);
        decoratedSupplier = Retry.decorateSupplier(retryRegistry.retry(provider), decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiterRegistry.rateLimiter(provider), decoratedSupplier);

        return decoratedSupplier.get();
    }

    private String callClient(ChatClient client, Prompt prompt) {
        return client.prompt().messages(prompt.getInstructions()).call().content();
    }

    public static class AiFallbackException extends RuntimeException {
        public AiFallbackException(String message) {
            super(message);
        }
        public AiFallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}