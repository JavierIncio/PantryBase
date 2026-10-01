package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.client.FdcProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FdcCachePolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private final FdcCachePolicy policy = new FdcCachePolicy(properties(
                Duration.ofHours(24), Duration.ofDays(30)));

    private static FdcProperties properties(Duration cacheTtl, Duration maxStale) {
        return new FdcProperties("KEY", "https://api.nal.usda.gov/fdc/v1",
                java.util.List.of("Foundation"), 20, Duration.ofSeconds(2), Duration.ofSeconds(5),
                cacheTtl, maxStale);
    }

    @Test
    void insideTheTtl_isFreshAndNeedsNoProviderCall() {
        assertThat(policy.evaluate(NOW.minus(Duration.ofHours(23)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.FRESH);
    }

    @Test
    void exactlyAtTheTtl_isAlreadyStale() {
        // At the boundary the cheap path is over, so the provider must be tried; the
        // local copy is still the fallback. Anything else would extend the quota-free
        // window by one tick.
        assertThat(policy.evaluate(NOW.minus(Duration.ofHours(24)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.STALE);
    }

    @Test
    void insideMaxStaleButPastTheTtl_isStale() {
        assertThat(policy.evaluate(NOW.minus(Duration.ofDays(29)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.STALE);
    }

    @Test
    void pastMaxStale_isExpired() {
        assertThat(policy.evaluate(NOW.minus(Duration.ofDays(31)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.EXPIRED);
    }

    @Test
    void exactlyAtMaxStale_isExpired() {
        assertThat(policy.evaluate(NOW.minus(Duration.ofDays(30)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.EXPIRED);
    }

    @Test
    void timestampInTheFuture_isExpiredRatherThanFresh() {
        // A restored dump or a clock skew must not pin stale data forever by being
        // treated as "fresher than fresh".
        assertThat(policy.evaluate(NOW.plus(Duration.ofDays(1)), NOW))
                .isEqualTo(FdcCachePolicy.Decision.EXPIRED);
    }

    @Test
    void missingTimestamp_isExpired() {
        assertThat(policy.evaluate(null, NOW)).isEqualTo(FdcCachePolicy.Decision.EXPIRED);
    }

    @Test
    void fresh_decision_neverCallsTheProvider() {
        String outcome = policy.withStaleFallback(
                FdcCachePolicy.Decision.FRESH,
                () -> "local",
                () -> {
                    throw new IllegalStateException("must not be called");
                });

        assertThat(outcome).isEqualTo("local");
    }

    @Test
    void expired_decision_propagatesTheProviderFailure() {
        assertThatThrownBy(() -> policy.withStaleFallback(
                FdcCachePolicy.Decision.EXPIRED,
                () -> "local",
                () -> {
                    throw new IllegalStateException("provider down");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider down");
    }

    @Test
    void stale_decision_prefersTheProvider() {
        String outcome = policy.withStaleFallback(
                FdcCachePolicy.Decision.STALE,
                () -> "local",
                () -> "provider");

        assertThat(outcome).isEqualTo("provider");
    }

    @Test
    void stale_decision_fallsBackWhenTheProviderFails() {
        String outcome = policy.withStaleFallback(
                FdcCachePolicy.Decision.STALE,
                () -> "local",
                () -> {
                    throw new IllegalStateException("quota exhausted");
                });

        assertThat(outcome).isEqualTo("local");
    }

    @Test
    void stale_decision_withNoLocalCopy_propagates() {
        // A 404-ish result is null, not an exception: without a copy to serve there is
        // nothing to fall back to, and reporting a provider failure is the truth.
        assertThatThrownBy(() -> policy.withStaleFallback(
                FdcCachePolicy.Decision.STALE,
                () -> null,
                () -> {
                    throw new IllegalStateException("provider down");
                }))
                .isInstanceOf(IllegalStateException.class);
    }
}
