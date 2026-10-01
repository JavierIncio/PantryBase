package com.pantrybase.api.catalog;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.exception.FdcRateLimitException;

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
    private final List<String> searchRequests = new ArrayList<>();
    private final List<Long> detailRequests = new ArrayList<>();
    private boolean providerError;
    private boolean quotaExhausted;

    public void returns(List<FoodSearchHit> hits) {
        this.searchResult = hits;
        this.providerError = false;
        this.quotaExhausted = false;
    }

    public void returns(Optional<FoodProfile> profile) {
        this.detailResult = profile;
        this.providerError = false;
        this.quotaExhausted = false;
    }

    public void returns(Map<Long, FoodProfile> batch) {
        this.batchResult = new HashMap<>(batch);
        this.providerError = false;
        this.quotaExhausted = false;
    }

    public void fails() {
        this.providerError = true;
        this.quotaExhausted = false;
        this.searchResult = List.of();
        this.detailResult = Optional.empty();
        this.batchResult = Map.of();
        this.batchRequests.clear();
    }

    /**
     * Exhausts the quota, the way the shared DEMO_KEY does in normal operation.
     *
     * <p>Kept apart from {@link #fails()} because the two demand different behaviour
     * from the service: a 429 must be answered from a local copy rather than
     * reported, and collapsing both into one flag would let a test pass without
     * proving that.</p>
     */
    public void quotaExhausted() {
        this.quotaExhausted = true;
        this.providerError = false;
    }

    public List<List<Long>> batchRequests() {
        return List.copyOf(batchRequests);
    }

    public List<String> searchRequests() {
        return List.copyOf(searchRequests);
    }

    public List<Long> detailRequests() {
        return List.copyOf(detailRequests);
    }

    public void resetCounters() {
        batchRequests.clear();
        searchRequests.clear();
        detailRequests.clear();
    }

    @Override
    public List<FoodSearchHit> search(String query, int limit) {
        searchRequests.add(query);
        checkFailure();
        return searchResult;
    }

    @Override
    public Optional<FoodProfile> getById(long fdcId) {
        detailRequests.add(fdcId);
        checkFailure();
        return detailResult;
    }

    @Override
    public Map<Long, FoodProfile> getByIds(Collection<Long> fdcIds) {
        checkFailure();
        batchRequests.add(List.copyOf(fdcIds));
        Map<Long, FoodProfile> found = new HashMap<>();
        for (Long id : fdcIds) {
            if (batchResult.containsKey(id)) found.put(id, batchResult.get(id));
        }
        return found;
    }

    private void checkFailure() {
        if (quotaExhausted) throw new FdcRateLimitException("stub quota exhausted");
        if (providerError) throw new FdcProviderException("stub provider failure");
    }
}