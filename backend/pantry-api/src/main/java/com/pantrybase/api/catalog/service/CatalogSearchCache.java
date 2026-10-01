package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Caches provider search results in Redis.
 *
 * <p>Only the search path is cached here. The detail path has a copy of everything
 * it returns in the local catalog, and Redis cannot hold that better than Postgres
 * already does; search is the one query whose result is never materialized, so
 * without this every keystroke in the search box would spend provider quota.</p>
 *
 * <p>Redis is a hard dependency of this application already (the rate limiter uses
 * it), so failing to reach it is not a degraded mode worth special handling: the
 * lookup simply misses and the provider is asked, which is the behaviour without a
 * cache at all.</p>
 */
@Component
public class CatalogSearchCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogSearchCache.class);

    private static final String KEY_PREFIX = "catalog:fdc:search:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public CatalogSearchCache(StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              FdcCachePolicy policy) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = policy.cacheTtl();
    }

    /**
     * @return the cached hits, or empty on a miss, an unreadable payload or a Redis
     * outage — in all of which cases the caller asks the provider
     */
    public Optional<List<FoodSearchHit>> get(String query, int limit) {
        String key = key(query, limit);
        try {
            String payload = redis.opsForValue().get(key);
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(objectMapper.readValue(payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FoodSearchHit.class))));
        } catch (Exception e) {
            // A cache that cannot be read must not fail the request: the provider can
            // still answer, which is exactly what happened before this class existed.
            log.warn("Catalog search cache unreadable for key {}: {}", key, e.toString());
            return Optional.empty();
        }
    }

    public void put(String query, int limit, List<FoodSearchHit> hits) {
        String key = key(query, limit);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(hits), ttl);
        } catch (Exception e) {
            log.warn("Catalog search cache not written for key {}: {}", key, e.toString());
        }
    }

    /**
     * Hashes the query instead of embedding it.
     *
     * <p>The query is user input, so it can contain spaces, newlines, colons and
     * arbitrary length; putting it in the key verbatim invites malformed keys and
     * makes eviction patterns unreliable. It also keeps the key free of the search
     * terms themselves, which are personal data in a multi-user deployment.</p>
     */
    private String key(String query, int limit) {
        return KEY_PREFIX + digest(query.trim().toLowerCase()) + ":" + limit;
    }

    private String digest(String normalized) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JLS but unavailable", e);
        }
    }
}
