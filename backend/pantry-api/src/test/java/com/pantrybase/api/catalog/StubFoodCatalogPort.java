package com.pantrybase.api.catalog;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.exception.FdcProviderException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test fake: deterministic FoodCatalogPort with programmatic behavior. */
public class StubFoodCatalogPort implements FoodCatalogPort {
    private List<FoodSearchHit> searchResult = List.of();
    private Optional<FoodProfile> detailResult = Optional.empty();
    private Map<Long, FoodProfile> batchResult = Map.of();
    private final List<List<Long>> batchRequests = new ArrayList<>();
    private boolean providerError;

    public void returns(List<FoodSearchHit> hits) {
        this.searchResult = hits;
        this.providerError = false;
    }

    public void returns(Optional<FoodProfile> profile) {
        this.detailResult = profile;
        this.providerError = false;
    }

    public void returns(Map<Long, FoodProfile> batch) {
        this.batchResult = new HashMap<>(batch);
        this.providerError = false;
    }

    public void fails() {
        this.providerError = true;
        this.searchResult = List.of();
        this.detailResult = Optional.empty();
        this.batchResult = Map.of();
        this.batchRequests.clear();
    }

    public List<List<Long>> batchRequests() {
        return List.copyOf(batchRequests);
    }

    @Override
    public List<FoodSearchHit> search(String query, int limit) {
        if (providerError) throw new FdcProviderException("stub provider failure");
        return searchResult;
    }

    @Override
    public Optional<FoodProfile> getById(long fdcId) {
        if (providerError) throw new FdcProviderException("stub provider failure");
        return detailResult;
    }

    @Override
    public Map<Long, FoodProfile> getByIds(Collection<Long> fdcIds) {
        if (providerError) throw new FdcProviderException("stub provider failure");
        batchRequests.add(List.copyOf(fdcIds));
        Map<Long, FoodProfile> found = new HashMap<>();
        for (Long id : fdcIds) {
            if (batchResult.containsKey(id)) found.put(id, batchResult.get(id));
        }
        return found;
    }
}