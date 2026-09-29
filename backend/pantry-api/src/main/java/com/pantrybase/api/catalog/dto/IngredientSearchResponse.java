package com.pantrybase.api.catalog.dto;

/** Summary of a food match shown in catalog search results. */
public record IngredientSearchResponse(long fdcId, String name, String dataType) {}
