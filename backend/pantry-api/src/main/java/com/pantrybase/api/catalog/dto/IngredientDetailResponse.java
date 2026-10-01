package com.pantrybase.api.catalog.dto;

import com.pantrybase.api.catalog.domain.NutrientProfile;

/**
 * Full detail of a catalog ingredient after materialization.
 *
 * <p>Merges the live provider profile (name, category, nutrients) with the
 * internal persisted id, so consumers can reference the ingredient without
 * depending on the external catalog.</p>
 */
public record IngredientDetailResponse(
        long id,
        long fdcId,
        String name,
        String dataType,
        String category,
        NutrientProfile nutrients,
        String densityClass
) {}
