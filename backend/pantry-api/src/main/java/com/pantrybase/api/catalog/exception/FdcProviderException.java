package com.pantrybase.api.catalog.exception;

/**
 * Signals that the USDA FDC provider could not answer the request.
 *
 * <p>Thrown by the catalog adapter on provider failures (5xx, rate limit,
 * timeouts). It is NOT thrown when a food simply does not exist — that is
 * a legitimate domain result ({@code Optional.empty()}).</p>
 */
public class FdcProviderException extends RuntimeException {
    public FdcProviderException(String message) {
        super(message);
    }

    public FdcProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
