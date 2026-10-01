package com.pantrybase.api.catalog.domain;

import com.pantrybase.api.catalog.exception.FdcProviderException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Fetches full profiles for the given fdcIds in a single provider call.
     *
     * <p>Batch lookups return only the foods the provider knows: unknown ids
     * are omitted from the result instead of failing, so absence is normal
     * data for callers to interpret, while transport or provider failures
     * (non-2xx) still throw {@link FdcProviderException}.</p>
     *
     * @param fdcIds ids to look up; may include unknowns, must not contain duplicates
     * @return profiles keyed by fdcId, only for ids present in the provider
     * @throws FdcProviderException when the provider cannot answer
     */
    Map<Long, FoodProfile> getByIds(Collection<Long> fdcIds);
}
