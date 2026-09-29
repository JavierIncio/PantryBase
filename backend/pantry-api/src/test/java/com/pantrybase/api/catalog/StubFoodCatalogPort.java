package com.pantrybase.api.catalog;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.exception.FdcProviderException;

import java.util.List;
import java.util.Optional;

/** Test fake: deterministic FoodCatalogPort with programmatic behavior. */
public class StubFoodCatalogPort implements FoodCatalogPort {
    private List<FoodSearchHit> searchResult = List.of();
    private Optional<FoodProfile> detailResult = Optional.empty();
    private boolean providerError;

    public void returns(List<FoodSearchHit> hits) {
        this.searchResult = hits;
        this.providerError = false;
    }

    public void returns(Optional<FoodProfile> profile) {
        this.detailResult = profile;
        this.providerError = false;
    }

    public void fails() { this.providerError = true; }

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
}