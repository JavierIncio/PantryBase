package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the external live catalog and the local materialization.
 *
 * <p>Searches hit the USDA FDC provider on the fly; detail lookups merge
 * the live profile with the internally persisted ingredient id.</p>
 */
@Service
@Transactional(readOnly = true)
public class IngredientCatalogService {

    private final FoodCatalogPort catalogPort;
    private final IngredientRepository ingredientRepo;

    public IngredientCatalogService(FoodCatalogPort catalogPort,
                                    IngredientRepository ingredientRepo) {
        this.catalogPort = catalogPort;
        this.ingredientRepo = ingredientRepo;
    }

    public List<IngredientSearchResponse> search(String query) {
        return catalogPort.search(query, 20).stream()
                .map(hit -> new IngredientSearchResponse(hit.fdcId(), hit.description(), hit.dataType()))
                .toList();
    }

    /**
     * Returns the full ingredient detail, materializing it in the local catalog.
     *
     * <p>Side effect: persists the ingredient (no-op if already stored) so the
     * returned internal id can be referenced by pantry and recipes.</p>
     */
    @Transactional
    public IngredientDetailResponse getById(long fdcId) {
        FoodProfile profile = catalogPort.getById(fdcId)
                .orElseThrow(() -> new IngredientNotFoundException(fdcId));
        materialize(profile);
        Ingredient ingredient = ingredientRepo.findByFdcId(fdcId)
                .orElseThrow(() -> new IllegalStateException("Ingredient not found after materialization"));
        return new IngredientDetailResponse(
                ingredient.getId(), profile.fdcId(), profile.description(),
                profile.dataType(), profile.category(), profile.nutrientsPer100g());
    }

    private void materialize(FoodProfile profile) {
        NutrientProfile n = profile.nutrientsPer100g();
        ingredientRepo.insertIfAbsent(
                profile.fdcId(),
                profile.description(),
                profile.category(),
                n == null ? null : n.energyKcal(),
                n == null ? null : n.proteinG(),
                n == null ? null : n.fatG(),
                n == null ? null : n.carbsG());
    }
}
