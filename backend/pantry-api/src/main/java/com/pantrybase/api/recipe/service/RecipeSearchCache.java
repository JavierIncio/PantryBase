package com.pantrybase.api.recipe.service;

import com.pantrybase.api.recipe.dto.RecipeSearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Caches TheMealDB search/filter results in Redis.
 *
 * <p>Only the search/filter paths are cached. The detail path materialises into the
 * local catalog and is served from Postgres; Redis cannot hold that better than
 * Postgres already does. Search/filter are the queries that would otherwise spend
 * provider quota on every keystroke.</p>
 *
 * <p>Redis is a hard dependency (rate limiter + catalog cache), so a cache outage
 * simply means a miss and the provider is asked — the behaviour without a cache.</p>
 */
@Component
public class RecipeSearchCache {

    private static final String KEY_PREFIX = "catalog:themdb:search:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RecipeSearchCache(StringRedisTemplate redis,
                             ObjectMapper objectMapper,
                             @Value("${themealdb.cache.ttl-hours:24}") long ttlHours) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public Optional<List<TheMealDbClient.MealSummary>> get(RecipeSearchRequest request) {
        String key = key(request);
        try {
            String payload = redis.opsForValue().get(key);
            if (payload == null) return Optional.empty();
            return Optional.of(List.copyOf(objectMapper.readValue(payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TheMealDbClient.MealSummary.class))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void put(RecipeSearchRequest request, List<TheMealDbClient.MealSummary> hits) {
        String key = key(request);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(hits), ttl);
        } catch (Exception ignored) {}
    }

    private String key(RecipeSearchRequest request) {
        String normalized = (request.name() != null ? "n:" + request.name() : "")
                + (request.ingredientId() != null ? "i:" + request.ingredientId() : "");
        return KEY_PREFIX + digest(normalized.trim().toLowerCase());
    }

    private String digest(String normalized) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}