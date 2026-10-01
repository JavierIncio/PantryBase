package com.pantrybase.api.catalog.exception;

/**
 * Signals that the USDA FDC rejected the request because the quota is exhausted.
 *
 * <p>Distinct from a generic {@link FdcProviderException} because the remedy is
 * different in kind, not just in degree. A 429 is not a provider outage: the key
 * works, and the same request will succeed once the quota window resets (up to an
 * hour). Retrying immediately spends quota that does not exist and delays the
 * recovery, so callers answer from a local copy instead of calling again, and the
 * event is counted separately because with the shared {@code DEMO_KEY} it is the
 * expected steady state, not an incident.</p>
 */
public class FdcRateLimitException extends FdcProviderException {

    public FdcRateLimitException(String message) {
        super(message);
    }
}
