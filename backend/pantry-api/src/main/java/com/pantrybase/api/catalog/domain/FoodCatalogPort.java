package com.pantrybase.api.catalog.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the external food catalog provider (USDA FDC).
 *
 * <p>Domain and services depend only on this interface; the HTTP adapter
 * ({@code FdcFoodCatalogClient}) implements it, so swapping providers
 * never touches domain or application logic.</p>
 */
public interface FoodCatalogPort {
    /** @return matching foods ordered by relevance, never null */
    List<FoodSearchHit> search(String query, int limit);

    /** @return empty when the provider has no food with the given fdcId */
    Optional<FoodProfile> getById(long fdcId);
}
