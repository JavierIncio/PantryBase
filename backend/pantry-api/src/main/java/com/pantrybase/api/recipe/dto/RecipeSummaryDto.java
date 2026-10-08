package com.pantrybase.api.recipe.dto;

import java.util.List;

/**
 * Minimal recipe summary used in search results and grids.
 *
 * <p>The {@code matchScore} is the fraction of ingredients that are FULLY covered
 * by the pantry (available >= needed). It is {@code null} when the caller did not
 * request coverage (e.g. admin listing).</p>
 */
public record RecipeSummaryDto(
        long id,
        String source,
        String externalId,
        String name,
        String category,
        String area,
        String imageUrl,
        Integer servings,
        Double matchScore,
        Integer missingCount,
        List<String> allergenCodes
) {}