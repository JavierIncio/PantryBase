package com.pantrybase.api.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for rate limiting.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        long capacity,
        long refillIntervalMillis,
        List<String> excludedPaths
) {}
