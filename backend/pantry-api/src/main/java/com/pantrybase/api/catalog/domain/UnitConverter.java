package com.pantrybase.api.catalog.domain;

import java.math.BigDecimal;

public interface UnitConverter {
    /** Normalize for a given category: 'CUP' → factor → 'ML'. */
    QuantityInfo toCanonical(BigDecimal amount, String unitCode);
    /**
     * Converts unit ↔ unit from the same category or across categories using a density.
     *
     * <p>Resolution of the density for volume↔weight, in order: the ingredient's own
     * household measure for the volume unit involved, then any other measure it has,
     * then the density class of the given ingredient, and finally the class passed as a
     * fallback. The provider's category is never used for density because it is too
     * coarse to carry one.</p>
     *
     * @param ingredientId optional ingredient to prefer its own measures and class
     * @param densityClass fallback density class when the ingredient has neither measure nor class
     */
    QuantityInfo convert(BigDecimal amount, String fromCode, String toCode,
                         Long ingredientId, String densityClass);
}