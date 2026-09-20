package com.pantrybase.api.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TokenBucketRateLimiter implements a token bucket rate limiting algorithm using Redis.
 * It allows for controlling the rate of requests by consuming tokens from a bucket.
 */
@Component
public class TokenBucketRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate,
                                  RateLimitProperties properties,
                                  DefaultRedisScript<List> script) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.script = script;
    }

    /**
     * Record representing the result of a rate limit check.
     *
     * @param allowed         whether the request is allowed (1) or not (0)
     * @param remainingTokens the number of tokens remaining in the bucket
     */
    public record RateLimitResult(boolean allowed, long remainingTokens) {}

    /**
     * Tries to consume a token from the bucket for the given bucket key.
     *
     * @param bucketKey the key representing the bucket in Redis
     * @return a RateLimitResult indicating whether the request is allowed and the remaining tokens
     */
    public RateLimitResult consume(String bucketKey) {
        List<?> result = redisTemplate.execute(
                script,
                List.of(bucketKey),
                String.valueOf(properties.capacity()),
                String.valueOf(properties.refillIntervalMillis()),
                String.valueOf(System.currentTimeMillis())
        );

        Long allowed = (Long) result.get(0);
        Long remainingTokens = (Long) result.get(1);

        return new RateLimitResult(allowed == 1L, remainingTokens);
    }
}
