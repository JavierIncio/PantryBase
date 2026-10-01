package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.domain.Portion;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the external live catalog and the local materialization.
 *
 * <p>Searches hit the USDA FDC provider on the fly; detail lookups merge
 * the live profile with the internally persisted ingredient id, and also
 * capture the provider's household measures (grams per cup, tablespoon, ...)
 * so volume-to-weight conversions can use the ingredient's own measure.</p>
 */
@Service
@Transactional(readOnly = true)
public class IngredientCatalogService {

    /** USDA FDC caps a single batch lookup at twenty ids. */
    private static final int FDC_BATCH_MAX = 20;

    private final FoodCatalogPort catalogPort;
    private final IngredientRepository ingredientRepo;
    private final IngredientMeasureRepository measureRepo;

    public IngredientCatalogService(FoodCatalogPort catalogPort,
                                    IngredientRepository ingredientRepo,
                                    IngredientMeasureRepository measureRepo) {
        this.catalogPort = catalogPort;
        this.ingredientRepo = ingredientRepo;
        this.measureRepo = measureRepo;
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
        return materializeAndMerge(profile);
    }

    /**
     * Materializes a batch of ingredients from the provider.
     *
     * <p>Duplicate ids are collapsed, and ids the provider does not know are
     * simply absent from the result (never an error, per the port contract).
     * The provider is called in chunks of {@link #FDC_BATCH_MAX} ids.</p>
     *
     * @throws IllegalArgumentException when the collection is empty
     */
    @Transactional
    public Map<Long, IngredientDetailResponse> getByIds(Collection<Long> fdcIds) {
        if (fdcIds.isEmpty()) {
            throw new IllegalArgumentException("fdcIds must not be empty");
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(fdcIds));
        return fetchAll(ids).values().stream()
                .map(this::materializeAndMerge)
                .collect(Collectors.toMap(IngredientDetailResponse::fdcId, Function.identity()));
    }

    private Map<Long, FoodProfile> fetchAll(List<Long> ids) {
        Map<Long, FoodProfile> profiles = new HashMap<>();
        for (List<Long> chunk : chunks(ids)) {
            profiles.putAll(catalogPort.getByIds(chunk));
        }
        return profiles;
    }

    private List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += FDC_BATCH_MAX) {
            chunks.add(ids.subList(i, Math.min(ids.size(), i + FDC_BATCH_MAX)));
        }
        return chunks;
    }

    private IngredientDetailResponse materializeAndMerge(FoodProfile profile) {
        materialize(profile);
        Ingredient ingredient = ingredientRepo.findByFdcId(profile.fdcId())
                .orElseThrow(() -> new IllegalStateException("Ingredient not found after materialization"));
        persistMeasures(ingredient.getId(), profile);
        return new IngredientDetailResponse(
                ingredient.getId(), profile.fdcId(), profile.description(),
                profile.dataType(), profile.category(), profile.nutrientsPer100g(),
                ingredient.getDensityClass());
    }

    private void persistMeasures(Long ingredientId, FoodProfile profile) {
        if (profile.portions() == null) return;
        for (Portion portion : profile.portions()) {
            measureRepo.insertIfAbsent(ingredientId, portion.unitCode(),
                    BigDecimal.valueOf(portion.gramPerUnit()));
        }
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
