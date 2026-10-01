package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.client.FdcProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Decides whether a locally stored copy of a provider record may be served.
 *
 * <p>Extracted from the service that uses it because the two windows only make
 * sense together, and a caller that applies them ad hoc is how an off-by-one
 * between them becomes an unexplained 502 during a provider outage.</p>
 *
 * <p>All three outcomes are intentional and distinct: serve without asking
 * (inside the TTL), serve only if the provider fails (inside max-stale), or
 * refuse (past max-stale). Returning {@link Decision#EXPIRED} rather than
 * throwing keeps the failure policy in the service, where the HTTP contract
 * lives.</p>
 */
@Component
public class FdcCachePolicy {

    private final Duration cacheTtl;
    private final Duration maxStale;

    public FdcCachePolicy(FdcProperties props) {
        this.cacheTtl = props.cacheTtl();
        this.maxStale = props.maxStale();
    }

    public Duration cacheTtl() {
        return cacheTtl;
    }

    public Decision evaluate(Instant syncedAt) {
        return evaluate(syncedAt, Instant.now());
    }

    /**
     * @param now the reference instant, injectable so a test can advance time
     *            without sleeping
     */
    public Decision evaluate(Instant syncedAt, Instant now) {
        if (syncedAt == null) {
            return Decision.EXPIRED;
        }
        Duration age = Duration.between(syncedAt, now);
        if (age.isNegative()) {
            // A row stamped in the future means a clock skew or a restored dump, not
            // a fresh record. Treating it as fresh would pin stale data indefinitely.
            return Decision.EXPIRED;
        }
        if (age.compareTo(cacheTtl) < 0) {
            return Decision.FRESH;
        }
        if (age.compareTo(maxStale) < 0) {
            return Decision.STALE;
        }
        return Decision.EXPIRED;
    }

    public enum Decision {
        /** Inside the TTL: answer without touching the provider. */
        FRESH,
        /** Past the TTL but inside max-stale: answer only if the provider fails. */
        STALE,
        /** Past max-stale: the provider must answer. */
        EXPIRED
    }

    /**
     * Runs the provider call, falling back to the local copy when it fails.
     *
     * <p>Only {@link Decision#STALE} has a fallback: a fresh copy needs no call, and
     * an expired one has no usable fallback. The distinction between a provider
     * failure and a missing record is preserved — an unknown fdcId is not an error,
     * so a fallback must never be substituted for it.</p>
     */
    public <T> T withStaleFallback(Decision decision,
                                   Supplier<T> localCopy,
                                   Supplier<T> providerCall) {
        if (decision == Decision.FRESH) {
            return localCopy.get();
        }
        try {
            return providerCall.get();
        } catch (RuntimeException e) {
            if (decision != Decision.STALE) {
                throw e;
            }
            T local = localCopy.get();
            if (local == null) {
                throw e;
            }
            return local;
        }
    }
}
