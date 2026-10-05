package com.pantrybase.api.pantry.exception;

/**
 * Signals an adjustment of zero, which would be a movement that moves nothing.
 *
 * <p>Refused instead of accepted as a no-op because the ledger is an account: a zero
 * entry makes the history longer without explaining anything, and once accepted it is
 * indistinguishable from a real movement to whoever reads it later. The check lives
 * here rather than in bean validation because {@code @DecimalMin} cannot say "non-zero"
 * for a value that may be positive or negative.</p>
 */
public class ZeroAdjustmentException extends RuntimeException {

    public ZeroAdjustmentException() {
        super("An adjustment of zero is not a movement; send a positive amount to add stock "
                + "or a negative one to consume it");
    }
}