package com.pantrybase.api.catalog.dto;

/**
 * An ingredient's curated density class after an assignment.
 *
 * <p>Distinct from {@link IngredientDetailResponse} on purpose: this reports only the
 * curated state, and does not pretend to carry the provider's data type or nutrients,
 * which are not reloaded here.</p>
 */
public record IngredientDensityClassResponse(
        long id,
        long fdcId,
        String name,
        String densityClass
) {}
