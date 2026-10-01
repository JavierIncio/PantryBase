package com.pantrybase.api.catalog.domain;

import java.util.List;

/** Represents the complete payload for a food item. */
public record FoodProfile(
        long fdcId,
        String description,
        String dataType,
        String category,
        NutrientProfile nutrientsPer100g,
        List<Portion> portions
) {}
