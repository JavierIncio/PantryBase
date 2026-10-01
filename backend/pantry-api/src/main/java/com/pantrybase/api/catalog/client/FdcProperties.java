package com.pantrybase.api.catalog.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the USDA FoodData Central API client.
 *
 * <p>Validated at startup on purpose. The API key falls back to the public
 * {@code DEMO_KEY} when the environment variable is absent, and that fallback is
 * rate-limited to 30 requests per hour, so an unnoticed missing key would degrade
 * the catalog to almost unusable instead of failing loudly where it can be
 * noticed. The timeouts and page size are checked for the same reason: a zero or
 * negative timeout is a misconfiguration that would only show up as mysterious
 * provider failures at runtime.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.catalog.fdc")
public record FdcProperties(
        @NotBlank(message = "app.catalog.fdc.api-key must not be blank")
        String apiKey,

        @NotBlank(message = "app.catalog.fdc.base-url must not be blank")
        String baseUrl,

        @NotEmpty(message = "app.catalog.fdc.data-types must not be empty")
        List<String> dataTypes,

        @Min(value = 1, message = "app.catalog.fdc.page-size must be at least 1")
        int pageSize,

        @NotNull(message = "app.catalog.fdc.connect-timeout must be set")
        Duration connectTimeout,

        @NotNull(message = "app.catalog.fdc.read-timeout must be set")
        Duration readTimeout,

        @NotNull(message = "app.catalog.fdc.cache-ttl must be set")
        Duration cacheTtl,

        @NotNull(message = "app.catalog.fdc.max-stale must be set")
        Duration maxStale
) {

    /**
     * Whether {@code maxStale} is longer than {@code cacheTtl}.
     *
     * <p>The two windows are only meaningful in that order: a copy inside
     * {@code cacheTtl} is served without asking the provider, and one between the
     * two is served only when the provider fails. If the stale window were the
     * shorter, an entry could be discarded while the provider is healthy and be
     * unresolvable once it is not, which is the opposite of what the setting is
     * for. Caught at startup because the symptom otherwise only appears as an
     * unexplained 502 under a provider outage.</p>
     */
    @AssertTrue(message = "app.catalog.fdc.max-stale must be greater than or equal to cache-ttl")
    public boolean isMaxStaleNotShorterThanCacheTtl() {
        return cacheTtl != null && maxStale != null
                && !maxStale.minus(cacheTtl).isNegative();
    }

    /**
     * Whether the client is running on the shared public key, which is capped at
     * 30 requests per hour. Callers use it to warn loudly at startup and to label
     * the provider as rate-limited in metrics.
     *
     * <p>Compared by value so that a real key is never logged, not even truncated.</p>
     */
    public boolean isDemoKey() {
        return "DEMO_KEY".equals(apiKey);
    }

    /**
     * A zero or negative timeout is a misconfiguration that otherwise surfaces as an
     * obscure provider failure, and Boot binds {@code 0s} happily.
     *
     * <p>Bean Validation has no constraint for {@link Duration}, hence the method.</p>
     */
    @AssertTrue(message = "app.catalog.fdc.connect-timeout must be greater than zero")
    public boolean isConnectTimeoutPositive() {
        return connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative();
    }

    @AssertTrue(message = "app.catalog.fdc.read-timeout must be greater than zero")
    public boolean isReadTimeoutPositive() {
        return readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative();
    }

    @AssertTrue(message = "app.catalog.fdc.cache-ttl must be greater than zero")
    public boolean isCacheTtlPositive() {
        return cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative();
    }

    @AssertTrue(message = "app.catalog.fdc.max-stale must be greater than zero")
    public boolean isMaxStalePositive() {
        return maxStale != null && !maxStale.isZero() && !maxStale.isNegative();
    }
}
