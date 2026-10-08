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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal KG_FACTOR = new BigDecimal("1000");
    private static final BigDecimal OZ_FACTOR = new BigDecimal("28.3495");
    private static final BigDecimal LB_FACTOR = new BigDecimal("453.592");

    private static final Pattern MEASURE_MIXED = Pattern.compile("^(\\d+)\\s+(\\d+)\\/(\\d+)(.*)$");
    private static final Pattern MEASURE_FRACTION = Pattern.compile("^(\\d+)\\/(\\d+)(.*)$");
    private static final Pattern MEASURE_DECIMAL = Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> TOKEN_STOPWORDS =
            Set.of("a", "and", "for", "in", "of", "or", "the", "to", "with");

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
    private final TransactionTemplate writeTx;

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
                         RecipeSearchCache searchCache,
                         PlatformTransactionManager txManager) {
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
        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
        RecipeNutrientsDto nutrients = computeNutrients(saved.getId(), ingredients, saved.getServings());

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

    /**
     * Materialises a fresh TheMealDB hit into the local store so later searches read it
     * from Postgres instead of the provider.
     *
     * <p>The write runs in a {@code REQUIRES_NEW} transaction managed by a
     * {@link TransactionTemplate} rather than a method-level annotation, because the
     * entry point is {@link #search} — a self-invocation that no transactional proxy
     * intercepts, and one that is itself inside the search's read-only transaction. A
     * write there would flush against a read-only connection and fail, so the template
     * suspends that transaction and commits its own.</p>
     *
     * <p>Provider ingredients are matched to the catalog by token containment; pairs with
     * an unparsable measure are skipped, and a hit none of whose ingredients match is not
     * stored at all — coverage computed over an empty line set would claim a recipe is
     * fully feasible when nothing about it was checked.</p>
     *
     * <p>Idempotency is best-effort: the read check plus the unique
     * {@code (source, external_id)} constraint, the constraint catching the concurrent
     * caller that both checks lose sight of. That race rolls the write back and is
     * swallowed, never surfaced to the search that lost it.</p>
     */
    public void materializeMealDbRecipe(TheMealDbClient.MealSummary hit) {
        if (recipeRepo.existsBySourceAndExternalId(SOURCE_MEALDB, hit.idMeal())) return;

        Optional<TheMealDbClient.MealDetail> detailOpt = mealDbClient.lookupById(hit.idMeal());
        if (detailOpt.isEmpty()) {
            log.warn("TheMealDB lookup returned empty for id {}", hit.idMeal());
            return;
        }
        TheMealDbClient.MealDetail detail = detailOpt.get();

        Map<Long, RecipeIngredient> matched = matchAndConvert(detail.ingredients());
        if (matched.isEmpty()) {
            log.info("Skipping TheMealDB recipe {} ({}) — no ingredient matched the catalog",
                    hit.strMeal(), hit.idMeal());
            return;
        }

        try {
            writeTx.executeWithoutResult(status -> persistMealDbRecipe(detail, matched));
            log.info("Materialised TheMealDB recipe {} ({}) — {}/{} ingredients matched",
                    hit.strMeal(), hit.idMeal(), matched.size(), detail.ingredients().size());
        } catch (DataIntegrityViolationException e) {
            log.info("TheMealDB recipe {} ({}) lost a materialisation race; a concurrent "
                    + "search already stored it", hit.strMeal(), hit.idMeal());
        }
    }

    private void persistMealDbRecipe(TheMealDbClient.MealDetail detail, Map<Long, RecipeIngredient> matched) {
        if (recipeRepo.existsBySourceAndExternalId(SOURCE_MEALDB, detail.idMeal())) return;

        Recipe recipe = new Recipe();
        recipe.setSource(SOURCE_MEALDB);
        recipe.setExternalId(detail.idMeal());
        recipe.setName(detail.strMeal());
        recipe.setCategory(detail.strCategory());
        recipe.setArea(detail.strArea());
        recipe.setInstructions(detail.strInstructions());
        recipe.setImageUrl(detail.strMealThumb());
        recipe.setVideoUrl(detail.strYoutube());
        recipe.setTags(detail.strTags());
        recipe.setServings(parseServings(detail.strServings()));
        recipe.setSourceUrl(detail.strSource());
        Instant now = Instant.now();
        recipe.setCreatedAt(now);
        recipe.setUpdatedAt(now);
        Recipe saved = recipeRepo.save(recipe);

        List<RecipeIngredient> lines = List.copyOf(matched.values());
        lines.forEach(line -> line.setRecipeId(saved.getId()));
        ingredientRepo.saveAll(lines);

        deriveAndStoreAllergens(saved.getId(),
                lines.stream().map(RecipeIngredient::getIngredientId).toList());

        computeNutrients(saved.getId(), lines, saved.getServings());
    }

    /**
     * Maps provider ingredient/measure pairs to canonical recipe lines.
     *
     * <p>Result is keyed by catalog ingredient id: the schema refuses two lines for the
     * same ingredient and a meal sometimes repeats one, so a repeated pair adds its
     * canonical amount instead — only while the canonical unit matches, because merging
     * grams with millilitres would fabricate a quantity.</p>
     *
     * <p>A pair is dropped when no catalog name contains all of its words, or when the
     * measure carries no writable quantity (decorative tokens such as "to taste").</p>
     */
    private Map<Long, RecipeIngredient> matchAndConvert(List<TheMealDbClient.IngredientMeasure> pairs) {
        Map<Long, RecipeIngredient> lines = new LinkedHashMap<>();
        for (int position = 0; position < pairs.size(); position++) {
            TheMealDbClient.IngredientMeasure pair = pairs.get(position);
            Ingredient catalog = matchIngredient(pair.ingredient());
            if (catalog == null) {
                log.debug("No catalog ingredient matched '{}' from meal", pair.ingredient());
                continue;
            }
            ParsedMeasure measure = parseMeasure(pair.measure());
            if (measure == null) {
                log.debug("Measure '{}' for '{}' carries no writable quantity", pair.measure(), pair.ingredient());
                continue;
            }
            QuantityInfo canonical = unitConverter.toStorageUnit(measure.amount(), measure.unitCode(), catalog.getId());

            RecipeIngredient line = lines.get(catalog.getId());
            if (line == null) {
                line = new RecipeIngredient();
                line.setIngredientId(catalog.getId());
                line.setOriginalAmount(measure.amount());
                line.setOriginalUnitCode(measure.unitCode());
                line.setCanonicalAmount(canonical.amount());
                line.setCanonicalUnitCode(canonical.unitCode());
                line.setPosition(position);
                lines.put(catalog.getId(), line);
            } else if (line.getCanonicalUnitCode().equals(canonical.unitCode())) {
                line.setCanonicalAmount(line.getCanonicalAmount().add(canonical.amount()));
            } else {
                log.debug("Skipping repeated '{}' whose canonical unit differs", pair.ingredient());
            }
        }
        return lines;
    }

    /**
     * Best catalog match for one provider ingredient name.
     *
     * <p>Every non-stop word of the provider name must appear as a whole word in the
     * catalog name ({"Whole Milk"} matches "Milk, whole"). The whole-word boundary is
     * deliberate: "Egg" must not match "Eggplant". Among the containers the shortest
     * name wins, then alphabetical order, so two seeds for the same food resolve
     * deterministically instead of following whichever row the database read first.</p>
     */
    private Ingredient matchIngredient(String providerName) {
        List<String> required = tokens(providerName);
        if (required.isEmpty()) return null;

        return catalogIngredientRepo.findIngredientNameContaining(required.getFirst()).stream()
                .filter(candidate -> tokens(candidate.getName()).containsAll(required))
                .min(Comparator.comparingInt((Ingredient candidate) -> candidate.getName().length())
                        .thenComparing(candidate -> candidate.getName().toLowerCase(Locale.ROOT)))
                .orElse(null);
    }

    /** Lower-cased alphanumeric words, stop words dropped and plural "s" trimmed. */
    private static List<String> tokens(String text) {
        return TOKEN.matcher(text.toLowerCase(Locale.ROOT)).results()
                .map(MatchResult::group)
                .filter(token -> !TOKEN_STOPWORDS.contains(token))
                .map(RecipeService::singularize)
                .toList();
    }

    private static String singularize(String token) {
        return token.endsWith("s") && token.length() > 3
                ? token.substring(0, token.length() - 1)
                : token;
    }

    /** A provider measure decomposed into a writable amount and a local unit code. */
    private record ParsedMeasure(BigDecimal amount, String unitCode) {}

    /**
     * Parses a TheMealDB measure ("1 1/2 cups", "200g", "to taste") into an amount and
     * a local unit code, or returns {@code null} when it carries no writable quantity.
     *
     * <p>Amounts may be integers, decimals, fractions ("1/2") or mixed fractions
     * ("1 1/2"). The unit word is resolved against the local unit table; a measure with
     * an amount but no recognised unit stays a count ({"2", "4 cloves"} → UNIT), while
     * one without any amount ("to taste", "garnish") is dropped.</p>
     */
    private static ParsedMeasure parseMeasure(String raw) {
        if (raw == null) return null;
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return null;

        BigDecimal amount;
        String rest;

        Matcher mixed = MEASURE_MIXED.matcher(text);
        if (mixed.matches()) {
            amount = BigDecimal.valueOf(Long.parseLong(mixed.group(1)))
                    .add(fraction(mixed.group(2), mixed.group(3)));
            rest = mixed.group(4);
        } else {
            Matcher fraction = MEASURE_FRACTION.matcher(text);
            if (fraction.matches()) {
                amount = fraction(fraction.group(1), fraction.group(2));
                rest = fraction.group(3);
            } else {
                Matcher decimal = MEASURE_DECIMAL.matcher(text);
                if (!decimal.matches()) return null;
                amount = new BigDecimal(decimal.group(1));
                rest = decimal.group(2);
            }
        }

        String unit = unitToken(rest);
        return unit == null ? null : new ParsedMeasure(amount, unit);
    }

    private static BigDecimal fraction(String numerator, String denominator) {
        return BigDecimal.valueOf(Long.parseLong(numerator))
                .divide(BigDecimal.valueOf(Long.parseLong(denominator)), 6, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the unit word(s) of a measure into a local unit code. The first known
     * alias wins ({"1 1/2 cups"} → CUP); when none fits the amount stays a pure count.
     */
    private static String unitToken(String rest) {
        for (String word : tokens(rest)) {
            String code = switch (word) {
                case "g", "gram" -> "GRAM";
                case "kg", "kilo", "kilogram" -> "KG";
                case "ml", "milliliter", "millilitre" -> "ML";
                case "l", "liter", "litre" -> "L";
                case "oz", "ounce" -> "OZ";
                case "lb", "pound" -> "LB";
                case "cup" -> "CUP";
                case "tbsp", "tbs", "tablespoon", "spoon" -> "TBSP";
                case "tsp", "teaspoon" -> "TSP";
                case "pint" -> "PINT";
                default -> null;
            };
            if (code != null) return code;
        }
        return "UNIT";
    }

    private static Integer parseServings(String servings) {
        if (servings == null || servings.isBlank()) return null;
        try {
            return Integer.valueOf(servings.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Computes the per-serving nutrient profile from the ingredient canonical amounts and
     * persists it, so a {@code detail} served later reads the same row instead of
     * diverging from what {@code create} replied.
     *
     * <p>Only lines whose canonical unit carries a weight contribute: a volume kept in
     * {@code ML} reached that unit precisely because no mass relationship existed, and a
     * count is unweighted too. An ingredient without nutrient data contributes zero for
     * the missing field rather than failing the whole recipe. The total is divided by the
     * serving count; a missing one is treated as a single serving — a total — since there
     * is nothing to divide by.</p>
     *
     * @return the computed profile, or {@code null} when no line could be weighted, so
     *         nothing is persisted and an all-zero row never passes ignorance off as fact
     */
    private RecipeNutrientsDto computeNutrients(Long recipeId, List<RecipeIngredient> ingredients, Integer servings) {
        Map<Long, Ingredient> nutrientsById = catalogIngredientRepo.findAllById(
                        ingredients.stream().map(RecipeIngredient::getIngredientId).toList())
                .stream()
                .collect(Collectors.toMap(Ingredient::getId, i -> i));

        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        boolean anyWeight = false;

        for (RecipeIngredient line : ingredients) {
            BigDecimal grams = gramsOf(line);
            if (grams == null) continue;
            anyWeight = true;
            Ingredient ingredient = nutrientsById.get(line.getIngredientId());
            if (ingredient == null) continue;

            BigDecimal perHundred = grams.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
            energy = energy.add(scaled(ingredient.getEnergyKcal(), perHundred));
            protein = protein.add(scaled(ingredient.getProteinG(), perHundred));
            fat = fat.add(scaled(ingredient.getFatG(), perHundred));
            carbs = carbs.add(scaled(ingredient.getCarbsG(), perHundred));
        }

        if (!anyWeight) return null;

        BigDecimal servingsBd = servings == null || servings <= 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(servings);

        RecipeNutrients nutrients = new RecipeNutrients();
        nutrients.setRecipeId(recipeId);
        nutrients.setEnergyKcal(perServing(energy, servingsBd));
        nutrients.setProteinG(perServing(protein, servingsBd));
        nutrients.setFatG(perServing(fat, servingsBd));
        nutrients.setCarbsG(perServing(carbs, servingsBd));
        nutrientsRepo.save(nutrients);

        return new RecipeNutrientsDto(
                nutrients.getEnergyKcal(), nutrients.getProteinG(),
                nutrients.getFatG(), nutrients.getCarbsG());
    }

    /**
     * Weight equivalent of one recipe line. {@code ML} and {@code UNIT} yield
     * {@code null}: a volume landed on millilitres only because no mass exists for it,
     * and a count has no mass by definition.
     */
    private static BigDecimal gramsOf(RecipeIngredient line) {
        return switch (line.getCanonicalUnitCode()) {
            case "GRAM" -> line.getCanonicalAmount();
            case "KG" -> line.getCanonicalAmount().multiply(KG_FACTOR);
            case "OZ" -> line.getCanonicalAmount().multiply(OZ_FACTOR);
            case "LB" -> line.getCanonicalAmount().multiply(LB_FACTOR);
            default -> null;
        };
    }

    private static BigDecimal scaled(Double perHundredGrams, BigDecimal factor) {
        return perHundredGrams == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(perHundredGrams).multiply(factor);
    }

    private static BigDecimal perServing(BigDecimal value, BigDecimal servings) {
        return value.divide(servings, 3, RoundingMode.HALF_UP);
    }

    private RecipeNutrientsDto toNutrientsDto(RecipeNutrients n) {
        return new RecipeNutrientsDto(n.getEnergyKcal(), n.getProteinG(), n.getFatG(), n.getCarbsG());
    }
}