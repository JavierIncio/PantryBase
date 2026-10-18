package com.pantrybase.api.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for rate limiting.
 *
 * <p>The policy comes in two tiers because the quota payer changes along the request
 * lifecycle: a default policy for authenticated buckets (keyed by user id) and an
 * {@code anonymous} policy for requests that carry no identity yet (keyed by client IP).
 * The anonymous tier also supports {@code strict-paths}, tighter overrides for the
 * credential endpoints an attacker can brute-force from a single machine.</p>
 *
 * @param capacity            maximum tokens per authenticated-user bucket
 * @param refillIntervalMillis milliseconds per token refill for authenticated-user buckets
 * @param excludedPaths       request paths never rate limited (health, swagger, ...)
 * @param anonymous           policy for requests arriving without authentication
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        long capacity,
        long refillIntervalMillis,
        List<String> excludedPaths,
        Anonymous anonymous
) {

    /**
     * Policy applied to requests that reach the filter without an authenticated user.
     *
     * @param capacity             maximum tokens per IP bucket
     * @param refillIntervalMillis milliseconds per token refill for IP buckets
     * @param strictPaths          tighter overrides matched against the request path
     */
    public record Anonymous(
            long capacity,
            long refillIntervalMillis,
            List<StrictPath> strictPaths
    ) {

        /**
         * A stricter capacity for a set of path patterns (Ant-style).
         *
         * @param paths              path patterns the override applies to
         * @param capacity           maximum tokens per bucket for those paths
         * @param refillIntervalMillis milliseconds per token refill for those paths
         */
        public record StrictPath(
                List<String> paths,
                long capacity,
                long refillIntervalMillis
        ) {}
    }
}
