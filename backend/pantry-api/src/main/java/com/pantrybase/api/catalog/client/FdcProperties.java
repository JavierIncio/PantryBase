package com.pantrybase.api.catalog.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the USDA FoodData Central API client.
 *
 * <p>The API key is bound from the environment variable {@code USDA_FDC_API_KEY}
 * in application.yml, defaulting to the public {@code DEMO_KEY}, which is enough
 * for development but severely rate-limited (30 req/h).</p>
 */
@ConfigurationProperties(prefix = "app.catalog.fdc")
public record FdcProperties(
        String apiKey,
        String baseUrl,
        List<String> dataTypes,
        int pageSize,
        Duration connectTimeout,
        Duration readTimeout
) {}
