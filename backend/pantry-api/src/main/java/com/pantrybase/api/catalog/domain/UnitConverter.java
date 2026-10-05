package com.pantrybase.api.catalog.domain;

import java.math.BigDecimal;

public interface UnitConverter {
    /** Normalize for a given category: 'CUP' → 'factor' → 'ML'. */
    QuantityInfo toCanonical(BigDecimal amount, String unitCode);

    /**
     * Converts unit → unit from the same category or across categories using a density.
     *
     * <p>Resolution of the density for volume→weight, in order: the ingredient's own
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

    /**
     * Resolves a quantity to the unit a stored quantity is kept in, which is what a
     * pantry item and a recipe line both need.
     *
     * <p>WEIGHT and VOLUME both land on {@code GRAM}: a stored inventory is compared
     * against other stored quantities far more often than it is read back as the user
     * typed it, and keeping a volume in {@code ML} would leave the comparison to a later
     * conversion that needs a density which may not exist. A count stays {@code UNIT},
     * because grams of eggs is a number nobody means.</p>
     *
     * <p>When no mass equivalent exists the canonical form is returned instead of
     * failing, so an exotic unit is stored faithfully rather than rejected. The caller
     * decides whether {@code ML} is an acceptable outcome; the engine never invents a
     * density to avoid one.</p>
     *
     * @param ingredientId optional ingredient whose measures and density class resolve
     *                     a volume-to-weight conversion; without it a volume quantity has
     *                     no mass and stays canonical
     */
    QuantityInfo toStorageUnit(BigDecimal amount, String unitCode, Long ingredientId);
}