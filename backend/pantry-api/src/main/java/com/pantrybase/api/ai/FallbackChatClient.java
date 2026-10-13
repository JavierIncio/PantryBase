package com.pantrybase.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class FallbackChatClient {

    private final Map<String, ChatClient> clientsByProvider;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final MeterRegistry meterRegistry;

    // Per-provider metrics
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> fallbackCounters = new ConcurrentHashMap<>();

    public FallbackChatClient(List<ChatModel> chatModels,
                              AiProviderProperties providerProperties,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry,
                              RateLimiterRegistry rateLimiterRegistry,
                              MeterRegistry meterRegistry) {
        this.clientsByProvider = resolveClients(chatModels, providerProperties);
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.meterRegistry = meterRegistry;

        // Initialize metrics for each provider
        for (String provider : clientsByProvider.keySet()) {
            latencyTimers.put(provider, Timer.builder("ai.provider.latency")
                    .tag("provider", provider)
                    .description("Latency of AI provider calls")
                    .register(meterRegistry));
            successCounters.put(provider, Counter.builder("ai.provider.calls")
                    .tag("provider", provider)
                    .tag("result", "success")
                    .description("Successful AI provider calls")
                    .register(meterRegistry));
            failureCounters.put(provider, Counter.builder("ai.provider.calls")
                    .tag("provider", provider)
                    .tag("result", "failure")
                    .description("Failed AI provider calls")
                    .register(meterRegistry));
            fallbackCounters.put(provider, Counter.builder("ai.provider.fallbacks")
                    .tag("provider", provider)
                    .description("Fallbacks to next provider")
                    .register(meterRegistry));
        }
    }

    /**
     * Resolves the ordered provider → client map from the configured chain, so each
     * provider key (e.g. {@code ollama}) matches the Resilience4j instance names.
     * Providers declared in the chain without a matching {@link ChatModel} on the
     * classpath are skipped.
     */
    private static Map<String, ChatClient> resolveClients(List<ChatModel> chatModels,
                                                          AiProviderProperties providerProperties) {
        Map<String, ChatClient> clients = new LinkedHashMap<>();
        for (AiProviderProperties.Provider provider : providerProperties.getProviders()) {
            ChatModel model = findChatModel(chatModels, provider.getType());
            if (model != null) {
                clients.put(provider.getType().name().toLowerCase(), ChatClient.builder(model).build());
            }
        }
        return clients;
    }

    private static ChatModel findChatModel(List<ChatModel> chatModels, AiProviderProperties.Provider.Type type) {
        return chatModels.stream().filter(model -> supports(type, model)).findFirst().orElse(null);
    }

    private static boolean supports(AiProviderProperties.Provider.Type type, ChatModel model) {
        return switch (type) {
            case OLLAMA -> model instanceof OllamaChatModel;
        };
    }

    public String callTextWithFallback(Prompt prompt) {
        for (Map.Entry<String, ChatClient> entry : clientsByProvider.entrySet()) {
            String provider = entry.getKey();
            ChatClient client = entry.getValue();

            try {
                return executeWithResilience(provider, client, prompt);
            } catch (Exception e) {
                recordFallback(provider);
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
                recordFallback(provider);
                continue;
            }
        }
        throw new AiFallbackException("All AI providers failed");
    }

    private String executeWithResilience(String provider, ChatClient client, Prompt prompt) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
        Retry retry = retryRegistry.retry(provider);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(provider);

        Timer.Sample sample = Timer.start(meterRegistry);

        Supplier<String> supplier = () -> callClient(client, prompt);

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(cb, () -> callClient(client, prompt));
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);

        try {
            String result = decoratedSupplier.get();
            recordSuccess(provider, sample);
            return result;
        } catch (Exception e) {
            recordFailure(provider, sample);
            throw e;
        }
    }

    private String executeWithFunction(String provider, ChatClient client, Prompt prompt, String functionName, ObjectNode functionSchema) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
        Retry retry = retryRegistry.retry(provider);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(provider);

        Timer.Sample sample = Timer.start(meterRegistry);

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

        try {
            String result = decoratedSupplier.get();
            recordSuccess(provider, sample);
            return result;
        } catch (Exception e) {
            recordFailure(provider, sample);
            throw e;
        }
    }

    private void recordSuccess(String provider, Timer.Sample sample) {
        sample.stop(latencyTimers.get(provider));
        successCounters.get(provider).increment();
    }

    private void recordFailure(String provider, Timer.Sample sample) {
        sample.stop(latencyTimers.get(provider));
        failureCounters.get(provider).increment();
    }

    private void recordFallback(String provider) {
        fallbackCounters.get(provider).increment();
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