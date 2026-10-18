package com.pantrybase.api.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TokenBucketRateLimiter implements a token bucket rate limiting algorithm using Redis.
 * It allows for controlling the rate of requests by consuming tokens from a bucket.
 *
 * <p>The bucket capacity and refill interval are arguments rather than properties read
 * inside this class because the mechanism is policy-agnostic: the filter applies
 * different policies (per-user vs per-IP, with stricter overrides) to the same
 * algorithm. Keeping the policy out of here means a new tier is a filter change only,
 * never a change to the script or the Redis interaction.</p>
 */
@Component
public class TokenBucketRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate,
                                  DefaultRedisScript<List> script) {
        this.redisTemplate = redisTemplate;
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
     * @param bucketKey          the key representing the bucket in Redis
     * @param capacity           the bucket capacity for this policy
     * @param refillIntervalMillis milliseconds per token refill for this policy
     * @return a RateLimitResult indicating whether the request is allowed and the remaining tokens
     */
    public RateLimitResult consume(String bucketKey, long capacity, long refillIntervalMillis) {
        List<?> result = redisTemplate.execute(
                script,
                List.of(bucketKey),
                String.valueOf(capacity),
                String.valueOf(refillIntervalMillis),
                String.valueOf(System.currentTimeMillis())
        );

        Long allowed = (Long) result.get(0);
        Long remainingTokens = (Long) result.get(1);

        return new RateLimitResult(allowed == 1L, remainingTokens);
    }
}
