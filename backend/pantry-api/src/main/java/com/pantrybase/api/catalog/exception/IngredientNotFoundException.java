package com.pantrybase.api.catalog.exception;

/**
 * Signals that a requested fdcId does not exist in the external catalog.
 *
 * <p>This is a legitimate domain result ("no such food"), NOT a provider
 * failure — the provider answered correctly. Maps to HTTP 404, unlike
 * {@link FdcProviderException} which maps to 502.</p>
 */
public class IngredientNotFoundException extends RuntimeException {
    public IngredientNotFoundException(long fdcId) {
        super(String.format("Ingredient %d not found in external catalog", fdcId));
    }
}
