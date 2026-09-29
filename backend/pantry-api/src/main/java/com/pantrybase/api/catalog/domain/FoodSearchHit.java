package com.pantrybase.api.catalog.domain;

/** Represents the essential information about a food item returned from a search query */
public record FoodSearchHit(long fdcId, String description, String dataType) {}
