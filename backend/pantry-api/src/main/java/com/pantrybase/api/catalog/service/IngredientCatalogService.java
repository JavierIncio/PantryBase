package com.pantrybase.api.catalog.service;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.domain.Portion;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.exception.FdcRateLimitException;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the external live catalog and the local materialization.
 *
 * <p>Searches hit the USDA FDC provider on the fly, through
 * {@link CatalogSearchCache}, because that result is never materialized anywhere
 * else. Detail lookups resolve the provider profile against the local row: inside
 * the freshness window the local copy answers by itself, so a repeated read costs
 * no quota, and the provider's household measures (grams per cup, tablespoon, ...)
 * are captured on the way through so volume-to-weight conversions can use the
 * ingredient's own measure.</p>
 */
@Service
@Transactional(readOnly = true)
public class IngredientCatalogService {

    private static final Logger log = LoggerFactory.getLogger(IngredientCatalogService.class);

    /** USDA FDC caps a single batch lookup at twenty ids. */
    private static final int FDC_BATCH_MAX = 20;

    private final FoodCatalogPort catalogPort;
    private final IngredientRepository ingredientRepo;
    private final IngredientMeasureRepository measureRepo;
    private final CatalogSearchCache searchCache;
    private final FdcCachePolicy cachePolicy;
    private final Counter staleServedCounter;
    private final Counter rateLimitCounter;

    public IngredientCatalogService(FoodCatalogPort catalogPort,
                                    IngredientRepository ingredientRepo,
                                    IngredientMeasureRepository measureRepo,
                                    CatalogSearchCache searchCache,
                                    FdcCachePolicy cachePolicy,
                                    MeterRegistry meterRegistry) {
        this.catalogPort = catalogPort;
        this.ingredientRepo = ingredientRepo;
        this.measureRepo = measureRepo;
        this.searchCache = searchCache;
        this.cachePolicy = cachePolicy;
        this.staleServedCounter = meterRegistry.counter("pantry.catalog.fdc.stale.served");
        this.rateLimitCounter = meterRegistry.counter("pantry.catalog.fdc.quota.exhausted");
    }

    /**
     * Serves search hits from Redis when possible.
     *
     * <p>No local fallback exists for search, unlike the detail path: a search
     * result is never materialized, so a local copy does not exist to serve. A
     * provider failure therefore still surfaces as {@code FdcProviderException}
     * and the caller sees a 502.</p>
     */
    public List<IngredientSearchResponse> search(String query) {
        return searchCache.get(query, 20).orElseGet(() -> {
            List<FoodSearchHit> hits = catalogPort.search(query, 20);
            searchCache.put(query, 20, hits);
            return hits;
        }).stream()
                .map(hit -> new IngredientSearchResponse(hit.fdcId(), hit.description(), hit.dataType()))
                .toList();
    }

    /**
     * Returns the full ingredient detail, materializing it in the local catalog.
     *
     * <p>Resolves in two or three steps. A local row inside the freshness window is
     * returned directly, with no provider call at all. Otherwise the provider is
     * asked, the row is refreshed, and a provider failure falls back to the local
     * copy while it stays inside the stale window. Past that window the failure is
     * reported, because a bounded amount of old data is useful and an unbounded
     * amount is not.</p>
     *
     * <p>Side effects: persists the ingredient if new, refreshes the provider-owned
     * columns otherwise, and appends any provider measure not seen before. Neither
     * the curated density class nor an existing measure is ever overwritten.</p>
     */
    @Transactional
    public IngredientDetailResponse getById(long fdcId) {
        Optional<Ingredient> local = ingredientRepo.findByFdcId(fdcId);
        FdcCachePolicy.Decision decision = local
                .map(i -> cachePolicy.evaluate(i.getSyncedAt()))
                .orElse(FdcCachePolicy.Decision.EXPIRED);

        if (decision == FdcCachePolicy.Decision.FRESH) {
            return toDetailResponse(local.orElseThrow());
        }

        FoodProfile profile;
        try {
            profile = catalogPort.getById(fdcId)
                    .orElseThrow(() -> new IngredientNotFoundException(fdcId));
        } catch (FdcProviderException e) {
            if (e instanceof FdcRateLimitException) {
                rateLimitCounter.increment();
            }
            // An unknown id is a normal domain result, never a reason to answer from
            // the local copy: the row exists, so the id is known after all.
            if (decision == FdcCachePolicy.Decision.STALE && local.isPresent()) {
                staleServedCounter.increment();
                log.warn("Serving stale catalog copy for fdcId={} after provider failure: {}",
                        fdcId, e.getMessage());
                return toDetailResponse(local.get());
            }
            throw e;
        }

        return materializeAndMerge(profile);
    }

    /**
     * Materializes a batch of ingredients from the provider.
     *
     * <p>Duplicate ids are collapsed, and ids the provider does not know are
     * simply absent from the result (never an error, per the port contract).
     * The provider is called in chunks of {@link #FDC_BATCH_MAX} ids.</p>
     *
     * <p>Batch lookups always reach the provider: they exist to hydrate many ids
     * at once, and the local rows cannot answer for ids that were never
     * materialized, so there is no local-first shortcut to take here.</p>
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
        try {
            Map<Long, FoodProfile> profiles = new HashMap<>();
            for (List<Long> chunk : chunks(ids)) {
                profiles.putAll(catalogPort.getByIds(chunk));
            }
            return profiles;
        } catch (FdcRateLimitException e) {
            rateLimitCounter.increment();
            throw e;
        }
    }

    private List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += FDC_BATCH_MAX) {
            chunks.add(ids.subList(i, Math.min(ids.size(), i + FDC_BATCH_MAX)));
        }
        return chunks;
    }

    /**
     * Writes the provider's view of the ingredient and answers from the local row.
     *
     * <p>Insert for a new id, refresh for a known one. The refresh is what lets a
     * corrected nutrient value at the provider reach the catalog at all, and it is
     * kept to the columns the provider owns: the density class is our own curated
     * claim and must survive every refresh.</p>
     */
    private IngredientDetailResponse materializeAndMerge(FoodProfile profile) {
        Ingredient ingredient = upsert(profile);
        persistMeasures(ingredient.getId(), profile);
        return toDetailResponse(ingredient);
    }

    private Ingredient upsert(FoodProfile profile) {
        NutrientProfile n = profile.nutrientsPer100g();
        Double energy = n == null ? null : n.energyKcal();
        Double protein = n == null ? null : n.proteinG();
        Double fat = n == null ? null : n.fatG();
        Double carbs = n == null ? null : n.carbsG();

        ingredientRepo.insertIfAbsent(
                profile.fdcId(), profile.description(), profile.category(), profile.dataType(),
                energy, protein, fat, carbs);

        int updated = ingredientRepo.refreshProviderFields(
                profile.fdcId(), profile.description(), profile.category(), profile.dataType(),
                energy, protein, fat, carbs);
        if (updated == 0) {
            // The insert lost a race against a concurrent request, or the row was
            // deleted in between. Either way there is no row to read, and reporting
            // that is better than returning a detail with no internal id.
            throw new IllegalStateException("Ingredient not found after materialization");
        }

        return ingredientRepo.findByFdcId(profile.fdcId())
                .orElseThrow(() -> new IllegalStateException("Ingredient not found after materialization"));
    }

    /**
     * Appends provider measures that are not stored yet, never replacing one.
     *
     * <p>Insert-only on purpose: a measure is grams per unit of a specific
     * ingredient, so a second reading is the same fact restated, and overwriting
     * would discard a value a conversion may already have been computed from.</p>
     */
    private void persistMeasures(Long ingredientId, FoodProfile profile) {
        if (profile.portions() == null) return;
        for (Portion portion : profile.portions()) {
            measureRepo.insertIfAbsent(ingredientId, portion.unitCode(),
                    BigDecimal.valueOf(portion.gramPerUnit()));
        }
    }

    private IngredientDetailResponse toDetailResponse(Ingredient ingredient) {
        return new IngredientDetailResponse(
                ingredient.getId(), ingredient.getFdcId(), ingredient.getName(),
                ingredient.getDataType(), ingredient.getCategory(),
                new NutrientProfile(ingredient.getEnergyKcal(), ingredient.getProteinG(),
                        ingredient.getFatG(), ingredient.getCarbsG()),
                ingredient.getDensityClass());
    }
}
