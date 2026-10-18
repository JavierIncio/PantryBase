package com.pantrybase.api.recipe.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full recipe detail, including coverage breakdown per ingredient.
 */
public record RecipeDetailDto(
        long id,
        String source,
        String externalId,
        String name,
        String category,
        String area,
        String instructions,
        String imageUrl,
        String videoUrl,
        List<String> tags,
        Integer servings,
        String sourceUrl,
        List<RecipeIngredientDetailDto> ingredients,
        RecipeNutrientsDto nutrients,
        List<String> allergenCodes,
        Double matchScore,
        List<IngredientCoverageDto> coverage,
        Boolean favorite,
        Boolean saved,
        Boolean owned,
        Boolean published
) {}