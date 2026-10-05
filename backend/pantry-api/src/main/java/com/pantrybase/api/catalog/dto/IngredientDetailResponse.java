package com.pantrybase.api.catalog.dto;

import com.pantrybase.api.catalog.domain.NutrientProfile;

import java.util.List;

/**
 * Full detail of a catalog ingredient after materialization.
 *
 * <p>Merges the live provider profile (name, category, nutrients) with
 * the internal persisted id, so consumers can reference the ingredient without
 * depending on the external catalog.</p>
 *
 * <p>The measures ride along here rather than in a separate response: this lookup is the
 * call that persists them, so they are always as fresh as the rest of the payload, and a
 * client that has to enter a quantity in household measures learns what they weigh from
 * the same round trip that told it the food's name. An empty list means the provider
 * weighed no household measure for this food -- a normal outcome, not a failure, and
 * never {@code null} so callers do not need a null check.</p>
 */
public record IngredientDetailResponse(
        long id,
        long fdcId,
        String name,
        String dataType,
        String category,
        NutrientProfile nutrients,
        String densityClass,
        List<IngredientMeasureResponse> measures
) {}