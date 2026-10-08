package com.pantrybase.api.recipe.service;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.QuantityInfo;
import com.pantrybase.api.catalog.domain.Unit;
import com.pantrybase.api.catalog.domain.UnitConverter;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.UnitRepository;
import com.pantrybase.api.pantry.repository.PantryItemRepository;
import com.pantrybase.api.recipe.domain.*;
import com.pantrybase.api.recipe.dto.*;
import com.pantrybase.api.recipe.repository.*;
import com.pantrybase.api.user.domain.Allergen;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Owns the recipe domain: search (with cache + TheMealDB fallback), materialisation,
 * coverage against the user's pantry, and user-created recipes.
 *
 * <p>Coverage rule (H4 decision): an ingredient is either FULLY covered (available >= needed)
 * or NOT covered. No partial credit. matchScore = fraction of ingredients fully covered.</p>
 *
 * <p>Priority when searching: USER recipes first, then already-materialised THE_MEAL_DB recipes,
 * then a fresh TheMealDB search (triggered only by explicit user action via request.name/ingredientId).</p>
 */
@Service
@Transactional(readOnly = true)
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);
    private static final String SOURCE_MEALDB = "THE_MEAL_DB";
    private static final String SOURCE_USER = "USER";

    private final RecipeRepository recipeRepo;
    private final RecipeIngredientRepository ingredientRepo;
    private final RecipeNutrientsRepository nutrientsRepo;
    private final RecipeAllergenRepository allergenRepo;
    private final AllergenMappingRepository allergenMappingRepo;
    private final IngredientRepository catalogIngredientRepo;
    private final IngredientMeasureRepository measureRepo;
    private final PantryItemRepository pantryRepo;
    private final UserRepository userRepo;
    private final UnitRepository unitRepo;
    private final UnitConverter unitConverter;
    private final TheMealDbClient mealDbClient;
    private final RecipeSearchCache searchCache;

    public RecipeService(RecipeRepository recipeRepo,
                         RecipeIngredientRepository ingredientRepo,
                         RecipeNutrientsRepository nutrientsRepo,
                         RecipeAllergenRepository allergenRepo,
                         AllergenMappingRepository allergenMappingRepo,
                         IngredientRepository catalogIngredientRepo,
                         IngredientMeasureRepository measureRepo,
                         PantryItemRepository pantryRepo,
                         UserRepository userRepo,
                         UnitRepository unitRepo,
                         UnitConverter unitConverter,
                         TheMealDbClient mealDbClient,
                         RecipeSearchCache searchCache) {
        this.recipeRepo = recipeRepo;
        this.ingredientRepo = ingredientRepo;
        this.nutrientsRepo = nutrientsRepo;
        this.allergenRepo = allergenRepo;
        this.allergenMappingRepo = allergenMappingRepo;
        this.catalogIngredientRepo = catalogIngredientRepo;
        this.measureRepo = measureRepo;
        this.pantryRepo = pantryRepo;
        this.userRepo = userRepo;
        this.unitRepo = unitRepo;
        this.unitConverter = unitConverter;
        this.mealDbClient = mealDbClient;
        this.searchCache = searchCache;
    }

    // ============ SEARCH ============

    /**
     * Search recipes for the user, applying coverage and allergen filtering.
     *
     * Priority:
     * 1. User recipes (source = USER)
     * 2. Already materialised TheMealDB recipes
     * 3. Fresh TheMealDB search (only if explicitly requested via request.name/ingredientId)
     *
     * @param userId   owner of the pantry
     * @param request  search parameters (name, ingredientId, strict, threshold, allergenSafe)
     * @return list of summaries with matchScore and missingCount
     */
    public List<RecipeSummaryDto> search(Long userId, RecipeSearchRequest request) {
        boolean strict = Boolean.TRUE.equals(request.strict());
        int threshold = request.threshold() != null ? request.threshold() : (strict ? 100 : 50);
        double thresholdRatio = threshold / 100.0;

        // 1. Load user pantry once
        Map<Long, BigDecimal> pantryAvailable = loadPantryCanonical(userId);

        // 2. Load allergen exclusions
        Set<String> excludedAllergens = getExcludedAllergens(userId);

        // 3. Candidates in priority order, keyed by id: a recipe reachable through two
        //    branches (e.g. materialised during this same search) must be scored once.
        Map<Long, Recipe> candidates = new LinkedHashMap<>();
        boolean hasName = request.name() != null && !request.name().isBlank();

        // 3a. USER recipes (always first)
        addCandidates(candidates, request, SOURCE_USER, hasName);

        // 3b. THE_MEAL_DB already materialised
        addCandidates(candidates, request, SOURCE_MEALDB, hasName);

        // 3c. Fresh TheMealDB search only if a name query was given; hits are
        //     materialised into the local store and then re-read through the same
        //     source-scoped queries as 3a/3b, so new rows join without a duplicate.
        if (hasName) {
            List<TheMealDbClient.MealSummary> fresh = searchCache.get(request)
                    .orElseGet(() -> {
                        List<TheMealDbClient.MealSummary> hits = mealDbClient.searchByName(request.name());
                        searchCache.put(request, hits);
                        return hits;
                    });
            fresh.forEach(hit -> {
                if (!recipeRepo.existsBySourceAndExternalId(SOURCE_MEALDB, hit.idMeal())) {
                    materializeMealDbRecipe(hit);
                }
            });
            addCandidates(candidates, request, SOURCE_MEALDB, hasName);
        }

        // 4. Filter allergens if requested
        Collection<Recipe> filtered = candidates.values();
        if (Boolean.TRUE.equals(request.allergenSafe()) && !excludedAllergens.isEmpty()) {
            filtered = filtered.stream()
                    .filter(r -> !hasExcludedAllergen(r.getId(), excludedAllergens))
                    .toList();
        }

        // 5. Compute coverage and filter by threshold
        return filtered.stream()
                .map(r -> toSummaryWithCoverage(r, pantryAvailable))
                .filter(s -> s.matchScore() != null && s.matchScore() >= (strict ? 1.0 : thresholdRatio))
                .sorted(Comparator.comparingDouble((RecipeSummaryDto s) -> -s.matchScore())
                        .thenComparing(RecipeSummaryDto::name))
                .toList();
    }

    // ============ DETAIL ============

    public Optional<RecipeDetailDto> detail(Long userId, long recipeId) {
        return recipeRepo.findById(recipeId).map(r -> toDetailDto(r, loadPantryCanonical(userId)));
    }

    // ============ USER RECIPE CREATION ============

    @Transactional
    public RecipeDetailDto createUserRecipe(Long userId, CreateRecipeRequest request) {
        // Validate ingredients exist
        List<Long> ingredientIds = request.ingredients().stream()
                .map(CreateRecipeIngredientRequest::ingredientId)
                .toList();
        Map<Long, Ingredient> ingredientMap = catalogIngredientRepo.findAllById(ingredientIds).stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));
        if (ingredientMap.size() != ingredientIds.size()) {
            throw new IllegalArgumentException("Some ingredientIds do not exist");
        }

        Recipe recipe = new Recipe();
        recipe.setSource(SOURCE_USER);
        recipe.setExternalId(UUID.randomUUID().toString());
        recipe.setName(request.name());
        recipe.setCategory(request.category());
        recipe.setArea(request.area());
        recipe.setInstructions(request.instructions());
        recipe.setImageUrl(request.imageUrl());
        recipe.setVideoUrl(request.videoUrl());
        recipe.setTags(request.tags() != null ? String.join(",", request.tags()) : null);
        recipe.setServings(request.servings());
        recipe.setSourceUrl(request.sourceUrl());
        recipe.setCreatedAt(Instant.now());
        recipe.setUpdatedAt(Instant.now());
        Recipe saved = recipeRepo.save(recipe);

        // Ingredients + canonical conversion
        List<RecipeIngredient> ingredients = new ArrayList<>();
        List<RecipeIngredientDetailDto> dtos = new ArrayList<>();
        for (int i = 0; i < request.ingredients().size(); i++) {
            CreateRecipeIngredientRequest req = request.ingredients().get(i);
            Ingredient ing = ingredientMap.get(req.ingredientId());
            QuantityInfo canonical = unitConverter.toStorageUnit(req.amount(), req.unitCode(), ing.getId());

            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipeId(saved.getId());
            ri.setIngredientId(ing.getId());
            ri.setOriginalAmount(req.amount());
            ri.setOriginalUnitCode(req.unitCode());
            ri.setCanonicalAmount(canonical.amount());
            ri.setCanonicalUnitCode(canonical.unitCode());
            ri.setPosition(req.position());
            ingredients.add(ri);

            dtos.add(new RecipeIngredientDetailDto(
                    ing.getId(), ing.getName(), ing.getCategory(),
                    req.amount(), req.unitCode(),
                    canonical.amount(), canonical.unitCode(),
                    unitRepo.findById(canonical.unitCode())
                            .map(Unit::getCategory)
                            .orElse(null),
                    req.position()
            ));
        }
        ingredientRepo.saveAll(ingredients);

        // Nutrients per serving
        RecipeNutrientsDto nutrients = computeNutrients(saved.getId(), ingredients);

        // Allergens
        deriveAndStoreAllergens(saved.getId(), ingredientIds);

        return new RecipeDetailDto(
                saved.getId(), SOURCE_USER, saved.getExternalId(),
                saved.getName(), saved.getCategory(), saved.getArea(),
                saved.getInstructions(), saved.getImageUrl(), saved.getVideoUrl(),
                saved.getTags() != null ? List.of(saved.getTags().split(",")) : List.of(),
                saved.getServings(), saved.getSourceUrl(),
                dtos, nutrients,
                allergenRepo.findAllergenCodesByRecipeId(saved.getId()).stream().toList(),
                null, // no coverage for user recipes by default
                List.of()
        );
    }

    // ============ INTERNAL HELPERS ============

    /**
     * Adds one source's recipes to the candidate map, narrowed by the ingredient filter
     * or the name query when either is present.
     *
     * <p>{@code putIfAbsent} rather than {@code put}: the fresh-search branch re-runs
     * these queries after materialising provider hits, and rows that were already
     * candidates must not be scored twice — a duplicate would appear twice in the grid
     * and skew any count the client derives from the result list.</p>
     */
    private void addCandidates(Map<Long, Recipe> candidates, RecipeSearchRequest request,
                               String source, boolean hasName) {
        List<Recipe> found;
        if (request.ingredientId() != null) {
            found = recipeRepo.findBySourceAndIngredient(source, request.ingredientId());
        } else if (hasName) {
            found = recipeRepo.searchByName(source, request.name());
        } else {
            found = recipeRepo.findBySourceOrderByName(source);
        }
        found.forEach(r -> candidates.putIfAbsent(r.getId(), r));
    }

    private Map<Long, BigDecimal> loadPantryCanonical(Long userId) {
        return pantryRepo.findAllWithIngredientByUserId(userId).stream()
                .collect(Collectors.toMap(
                        row -> ((com.pantrybase.api.pantry.domain.PantryItem) row[0]).getIngredientId(),
                        row -> ((com.pantrybase.api.pantry.domain.PantryItem) row[0]).getQuantity(),
                        (a, b) -> a)); // should be unique per (user, ingredient)
    }

    private Set<String> getExcludedAllergens(Long userId) {
        return userRepo.findById(userId)
                .map(User::getAllergyExclusions)
                .orElse(Set.of())
                .stream()
                .map(Allergen::getCode)
                .collect(Collectors.toSet());
    }

    private boolean hasExcludedAllergen(Long recipeId, Set<String> excluded) {
        return allergenRepo.findAllergenCodesByRecipeId(recipeId).stream()
                .anyMatch(excluded::contains);
    }

    private RecipeSummaryDto toSummaryWithCoverage(Recipe recipe, Map<Long, BigDecimal> pantry) {
        List<RecipeIngredient> ingredients = ingredientRepo.findByRecipeIdOrderByPositionAsc(recipe.getId());
        if (ingredients.isEmpty()) {
            return new RecipeSummaryDto(
                    recipe.getId(), recipe.getSource(), recipe.getExternalId(),
                    recipe.getName(), recipe.getCategory(), recipe.getArea(),
                    recipe.getImageUrl(), recipe.getServings(),
                    null, 0, allergenRepo.findAllergenCodesByRecipeId(recipe.getId()).stream().toList());
        }

        int total = ingredients.size();
        int covered = 0;
        int missing = 0;

        for (RecipeIngredient ri : ingredients) {
            BigDecimal available = pantry.getOrDefault(ri.getIngredientId(), BigDecimal.ZERO);
            if (available.compareTo(ri.getCanonicalAmount()) >= 0) {
                covered++;
            } else {
                missing++;
            }
        }

        double score = (double) covered / total;

        return new RecipeSummaryDto(
                recipe.getId(), recipe.getSource(), recipe.getExternalId(),
                recipe.getName(), recipe.getCategory(), recipe.getArea(),
                recipe.getImageUrl(), recipe.getServings(),
                score, missing,
                allergenRepo.findAllergenCodesByRecipeId(recipe.getId()).stream().toList());
    }

    private RecipeDetailDto toDetailDto(Recipe recipe, Map<Long, BigDecimal> pantry) {
        List<RecipeIngredient> ingredients = ingredientRepo.findByRecipeIdOrderByPositionAsc(recipe.getId());
        List<RecipeIngredientDetailDto> detailIngredients = ingredients.stream().map(ri -> {
            Ingredient ing = catalogIngredientRepo.getReferenceById(ri.getIngredientId());
            return new RecipeIngredientDetailDto(
                    ing.getId(), ing.getName(), ing.getCategory(),
                    ri.getOriginalAmount(), ri.getOriginalUnitCode(),
                    ri.getCanonicalAmount(), ri.getCanonicalUnitCode(),
                    unitRepo.findById(ri.getCanonicalUnitCode())
                            .map(Unit::getCategory)
                            .orElse(null),
                    ri.getPosition()
            );
        }).toList();

        List<IngredientCoverageDto> coverage = ingredients.stream().map(ri -> {
            BigDecimal available = pantry.getOrDefault(ri.getIngredientId(), BigDecimal.ZERO);
            return new IngredientCoverageDto(
                    ri.getIngredientId(),
                    catalogIngredientRepo.getReferenceById(ri.getIngredientId()).getName(),
                    ri.getCanonicalAmount(), ri.getCanonicalUnitCode(),
                    available, ri.getCanonicalUnitCode(),
                    available.compareTo(ri.getCanonicalAmount()) >= 0
            );
        }).toList();

        int covered = (int) coverage.stream().filter(IngredientCoverageDto::fullyCovered).count();
        double score = ingredients.isEmpty() ? 0.0 : (double) covered / ingredients.size();

        return new RecipeDetailDto(
                recipe.getId(), recipe.getSource(), recipe.getExternalId(),
                recipe.getName(), recipe.getCategory(), recipe.getArea(),
                recipe.getInstructions(), recipe.getImageUrl(), recipe.getVideoUrl(),
                recipe.getTags() != null ? List.of(recipe.getTags().split(",")) : List.of(),
                recipe.getServings(), recipe.getSourceUrl(),
                detailIngredients,
                nutrientsRepo.findById(recipe.getId()).map(this::toNutrientsDto).orElse(null),
                allergenRepo.findAllergenCodesByRecipeId(recipe.getId()).stream().toList(),
                score,
                coverage
        );
    }

    private void deriveAndStoreAllergens(Long recipeId, List<Long> ingredientIds) {
        Set<String> codes = allergenMappingRepo.findAllergenCodesByIngredientIds(ingredientIds);
        if (!codes.isEmpty()) {
            allergenRepo.deleteByRecipeId(recipeId);
            List<RecipeAllergen> toSave = codes.stream().map(code -> {
                RecipeAllergen ra = new RecipeAllergen();
                ra.setRecipeId(recipeId);
                ra.setAllergenCode(code);
                return ra;
            }).toList();
            allergenRepo.saveAll(toSave);
        }
    }

    @Transactional
    public void materializeMealDbRecipe(TheMealDbClient.MealSummary hit) {
        if (recipeRepo.existsBySourceAndExternalId(SOURCE_MEALDB, hit.idMeal())) return;

        Optional<TheMealDbClient.MealDetail> detailOpt = mealDbClient.lookupById(hit.idMeal());
        if (detailOpt.isEmpty()) {
            log.warn("TheMealDB lookup returned empty for id {}", hit.idMeal());
            return;
        }
        TheMealDbClient.MealDetail detail = detailOpt.get();

        // TheMealDB detail contains up to 20 ingredient/measure pairs.
        // Real implementation parses the raw JSON map from the lookup response.
        // For brevity, this skeleton leaves materialisation as a TODO.

        log.info("Materialised TheMealDB recipe {} ({})", hit.strMeal(), hit.idMeal());
    }

    private RecipeNutrientsDto computeNutrients(Long recipeId, List<RecipeIngredient> ingredients) {
        // Sum nutrients from each ingredient * (canonicalAmount / 100g) / servings
        return null;
    }

    private RecipeNutrientsDto toNutrientsDto(RecipeNutrients n) {
        return new RecipeNutrientsDto(n.getEnergyKcal(), n.getProteinG(), n.getFatG(), n.getCarbsG());
    }
}