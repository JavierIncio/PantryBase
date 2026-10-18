package com.pantrybase.api.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One ingredient in the profile ranking, with how often it was cooked across the
 * user's history.
 */
@Schema(name = "IngredientUsageResponse", description = "An ingredient and how often it was cooked")
public record IngredientUsageResponse(
        @Schema(description = "Catalog ingredient id", example = "42")
        Long ingredientId,
        @Schema(description = "Ingredient name as it appears on the recipe line", example = "Rice")
        String name,
        @Schema(description = "Number of completed cooks that used this ingredient", example = "5")
        long count
) {}
