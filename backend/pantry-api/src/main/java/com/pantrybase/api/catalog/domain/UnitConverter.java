package com.pantrybase.api.catalog.domain;

import java.math.BigDecimal;

public interface UnitConverter {
    /** Normalize for a given category: 'CUP' → factor → 'ML'. */
    QuantityInfo toCanonical(BigDecimal amount, String unitCode);
    /**
     * Converts unit ↔ unit from the same category or across categories using a density.
     *
     * <p>Resolution of the density for volume↔weight, in order: the ingredient's own
     * household measure for the volume unit involved, then the category density
     * (either the category of the given ingredient, or the one passed as a fallback).</p>
     *
     * @param ingredientId optional ingredient to prefer its own measures over the category
     * @param ingredientCategory fallback density category when the ingredient has no measure
     */
    QuantityInfo convert(BigDecimal amount, String fromCode, String toCode, Long ingredientId, String ingredientCategory);
}