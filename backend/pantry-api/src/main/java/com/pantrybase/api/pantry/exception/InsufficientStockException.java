package com.pantrybase.api.pantry.exception;

import java.math.BigDecimal;

/**
 * Signals that an adjustment would take the item's quantity to zero or below.
 *
 * <p>The request is refused rather than clamped. A line that can hold a negative amount
 * lets the user cook against stock that is not there: the recipe in front of them says
 * they have 400 g and the absence of an error says the same, so the dish is started on
 * an ingredient nobody has. Being short of an ingredient should cost them the recipe,
 * not the surprise at the table.</p>
 *
 * <p>Leaving exactly zero is allowed, so consuming the last of something is a normal
 * outcome rather than a special case. The user removes the line themselves, which is
 * the moment a note about it is still worth writing.</p>
 *
 * <p>The message carries all three numbers because "not enough" without them is not
 * actionable: the caller has to recompute the shortfall by hand against a quantity that
 * may already have moved since they loaded it.</p>
 */
public class InsufficientStockException extends RuntimeException {

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public InsufficientStockException(long itemId, BigDecimal delta, BigDecimal current,
                                      BigDecimal wouldRemain, String unitCode) {
        super("Pantry item " + itemId + ": an adjustment of " + plain(delta) + " " + unitCode
                + " would leave " + plain(wouldRemain) + " " + unitCode
                + ", but it holds " + plain(current) + " " + unitCode);
    }
}