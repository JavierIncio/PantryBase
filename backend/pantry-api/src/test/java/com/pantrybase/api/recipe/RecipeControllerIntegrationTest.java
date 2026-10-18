package com.pantrybase.api.recipe;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.pantry.domain.PantryItem;
import com.pantrybase.api.recipe.domain.AllergenMapping;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.domain.RecipeAllergen;
import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.dto.CreateRecipeIngredientRequest;
import com.pantrybase.api.recipe.dto.CreateRecipeRequest;
import com.pantrybase.api.recipe.dto.IngredientCoverageDto;
import com.pantrybase.api.recipe.dto.RecipeDetailDto;
import com.pantrybase.api.recipe.dto.RecipeIngredientDetailDto;
import com.pantrybase.api.recipe.dto.RecipeSummaryDto;
import com.pantrybase.api.recipe.repository.AllergenMappingRepository;
import com.pantrybase.api.recipe.repository.RecipeAllergenRepository;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.recipe.repository.RecipeRepository;
import com.pantrybase.api.recipe.service.TheMealDbClient;
import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.dto.AllergyExclusionsResponse;
import com.pantrybase.api.user.dto.ReplaceAllergyExclusionsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the H4 contract: binary coverage (no partial credit), strict vs threshold
 * filtering, allergen exclusion, source priority, and the recipe endpoints.
 */
class RecipeControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String RECIPES = "/api/recipes";
    private static final AtomicLong FDC_SEQ = new AtomicLong(900_000);

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private IngredientMeasureRepository measureRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private RecipeAllergenRepository recipeAllergenRepository;
    @Autowired
    private AllergenMappingRepository allergenMappingRepository;

    /** Provider is replaced so no test reaches the real TheMealDB. */
    @MockitoBean
    private TheMealDbClient mealDbClient;

    @BeforeEach
    void cleanRecipeData() {
        // Parent already wiped pantry items and users; recipes and the catalog they
        // reference must go too, children first or the foreign keys refuse.
        recipeAllergenRepository.deleteAll();
        recipeDietRepository.deleteAll();
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        allergenMappingRepository.deleteAll();
        measureRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    // ============ SEARCH ============

    @Test
    void search_requiresAuthentication() {
        assertUnauthorized(RECIPES, HttpMethod.GET);
    }

    @Test
    void search_scoresCoverageBinary_withoutPartialCredit() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Ingredient chicken = ingredient("Chicken", "Poultry");
        Recipe recipe = recipe("USER", "Chicken Rice Bowl");
        line(recipe, rice, "100", 0);
        line(recipe, chicken, "200", 1);

        // Exact quantity covers, 99 of 100 does not: no fraction is ever credited.
        stock(rice, "99");
        stock(chicken, "200");

        ResponseEntity<List<RecipeSummaryDto>> response = search(headers, "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(summary -> {
            assertThat(summary.matchScore()).isEqualTo(0.5);
            assertThat(summary.missingCount()).isEqualTo(1);
        });
    }

    @Test
    void search_strictRequiresEveryIngredientCovered() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Ingredient chicken = ingredient("Chicken", "Poultry");
        Recipe recipe = recipe("USER", "Chicken Rice Bowl");
        line(recipe, rice, "100", 0);
        line(recipe, chicken, "200", 1);

        stock(rice, "99");
        stock(chicken, "200");
        assertThat(search(headers, "?strict=true").getBody()).isEmpty();

        stock(rice, "100");
        ResponseEntity<List<RecipeSummaryDto>> strict = search(headers, "?strict=true");
        assertThat(strict.getBody()).singleElement()
                .satisfies(summary -> assertThat(summary.matchScore()).isEqualTo(1.0));
    }

    @Test
    void search_thresholdIsTheMinimumAcceptedScore() {
        HttpHeaders headers = authenticate();
        Recipe recipe = recipe("USER", "Four Ingredient Stew");
        for (int i = 0; i < 4; i++) {
            Ingredient ing = ingredient("Stew Ingredient " + i, "Vegetable");
            line(recipe, ing, "50", i);
            if (i < 3) {
                stock(ing, "50");
            }
        }

        assertThat(search(headers, "?threshold=80").getBody()).isEmpty();
        assertThat(search(headers, "?threshold=75").getBody()).hasSize(1);

        ResponseEntity<List<RecipeSummaryDto>> atThreshold = search(headers, "?threshold=70");
        assertThat(atThreshold.getBody()).singleElement()
                .satisfies(summary -> {
                    assertThat(summary.matchScore()).isEqualTo(0.75);
                    assertThat(summary.missingCount()).isEqualTo(1);
                });
    }

    @Test
    void search_allergenSafeExcludesRecipesWithExcludedAllergens() {
        HttpHeaders headers = authenticate();

        Ingredient egg = ingredient("Egg", "Egg");
        Ingredient lettuce = ingredient("Lettuce", "Vegetable");
        Recipe omelette = recipe("USER", "Omelette");
        line(omelette, egg, "100", 0);
        Recipe salad = recipe("USER", "Garden Salad");
        line(salad, lettuce, "100", 0);
        allergenRow(egg, "EGG");
        recipeAllergen(omelette, "EGG");
        stock(egg, "100");
        stock(lettuce, "100");

        // Without the flag both recipes are returned.
        assertThat(search(headers, "").getBody()).hasSize(2);

        excludeFromDiet(headers, "EGG");
        assertThat(search(headers, "?allergenSafe=true").getBody())
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Garden Salad"));

        // The exclusion is opt-in: without allergenSafe the omelette comes back.
        assertThat(search(headers, "").getBody()).hasSize(2);
    }

    @Test
    void search_byNameFiltersLocalRecipesAndSkipsProviderOnEmptyResult() {
        HttpHeaders headers = authenticate();
        Ingredient base = ingredient("Flour", "Grain");
        Recipe cake = recipe("USER", "Chocolate Cake");
        line(cake, base, "200", 0);
        Recipe stew = recipe("USER", "Beef Stew");
        line(stew, base, "100", 0);
        stock(base, "500");

        when(mealDbClient.searchByName("cake")).thenReturn(List.of());

        ResponseEntity<List<RecipeSummaryDto>> response = search(headers, "?name=cake");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Chocolate Cake"));
    }

    @Test
    void search_byIngredientOnlyReturnsRecipesContainingIt() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Ingredient lentils = ingredient("Lentils", "Pulse");
        Recipe riceDish = recipe("USER", "Rice Pilaf");
        line(riceDish, rice, "100", 0);
        Recipe lentilDish = recipe("USER", "Lentil Soup");
        line(lentilDish, lentils, "100", 0);
        stock(rice, "100");
        stock(lentils, "100");

        ResponseEntity<List<RecipeSummaryDto>> response =
                search(headers, "?ingredientId=" + rice.getId());
        assertThat(response.getBody())
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Rice Pilaf"));
    }

    // ============ FRESH SEARCH BY INGREDIENT ============

    @Test
    void search_discoversFreshProviderHitsByIngredient() {
        HttpHeaders headers = authenticate();
        Ingredient cabbage = ingredient("Cabbage", "Vegetable");
        recipe("USER", "Cabbage and Rice");
        stock(cabbage, "100");

        when(mealDbClient.filterByIngredient("cabbage")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52944", "Cabbage Roll", "Side", "British",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52944")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "52944", "Cabbage Roll", "Side", "British", "Roll and fry.",
                        null, null, null,
                        List.of(new TheMealDbClient.IngredientMeasure("Cabbage", "100g")),
                        null, "4")));

        // First call materialises the provider hit; the second is served from the per-word
        // Redis cache, so each provider endpoint answers once and the row stays single.
        search(headers, "?ingredientId=" + cabbage.getId());
        ResponseEntity<List<RecipeSummaryDto>> second =
                search(headers, "?ingredientId=" + cabbage.getId());

        verify(mealDbClient, times(1)).filterByIngredient("cabbage");
        verify(mealDbClient, times(1)).lookupById("52944");
        assertThat(second.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.name()).isEqualTo("Cabbage Roll");
                    assertThat(summary.matchScore()).isEqualTo(1.0);
                });

        Recipe materialised = recipeRepository
                .findBySourceAndExternalId("THE_MEAL_DB", "52944").orElseThrow();
        assertThat(recipeIngredientRepository.findByRecipeIdOrderByPositionAsc(materialised.getId()))
                .singleElement()
                .satisfies(ri -> assertThat(ri.getIngredientId()).isEqualTo(cabbage.getId()));
    }

    @Test
    void search_unknownIngredientReturns404_withoutCallingProvider() {
        HttpHeaders headers = authenticate();

        ResponseEntity<ErrorResponse> response = rest.exchange(
                RECIPES + "?ingredientId=999999999", HttpMethod.GET, new HttpEntity<>(headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, RECIPES);
        verify(mealDbClient, never()).filterByIngredient(anyString());
    }

    @Test
    void search_byIngredient_withoutProviderHitsStillReturnsLocalRows() {
        HttpHeaders headers = authenticate();
        Ingredient quinoa = ingredient("Quinoa", "Grain");
        Recipe quinoaDish = recipe("USER", "Quinoa Pilaf");
        line(quinoaDish, quinoa, "100", 0);
        stock(quinoa, "100");
        when(mealDbClient.filterByIngredient("quinoa")).thenReturn(List.of());

        ResponseEntity<List<RecipeSummaryDto>> response =
                search(headers, "?ingredientId=" + quinoa.getId());

        verify(mealDbClient, times(1)).filterByIngredient("quinoa");
        assertThat(response.getBody())
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Quinoa Pilaf"));
    }

    // ============ FRESHNESS (TTL REFRESH) ============

    @Test
    void detail_refreshesStaleProviderRecipe_inPlace() {
        HttpHeaders headers = authenticate();
        ingredient("Wheat Flour", "Grain");
        when(mealDbClient.searchByName("Pancakes")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52768", "Pancakes", "Breakfast", "American",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52768")).thenReturn(Optional.of(pancakeDetail("Pancakes", "4")));
        search(headers, "?name=Pancakes&threshold=0");

        Recipe stored = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52768").orElseThrow();
        Instant staleMark = Instant.now().minus(Duration.ofDays(2));
        stored.setRefreshedAt(staleMark);
        recipeRepository.saveAndFlush(stored);

        when(mealDbClient.lookupById("52768"))
                .thenReturn(Optional.of(pancakeDetail("Fresh Pancakes", "6")));
        ResponseEntity<RecipeDetailDto> refreshed = detail(headers, stored.getId());

        assertThat(refreshed.getBody().name()).isEqualTo("Fresh Pancakes");
        assertThat(refreshed.getBody().servings()).isEqualTo(6);
        assertThat(recipeRepository.findById(stored.getId()).orElseThrow().getRefreshedAt())
                .isAfter(staleMark);
        verify(mealDbClient, times(2)).lookupById("52768");
    }

    @Test
    void detail_doesNotReRequestRecipeWithinFreshTtl() {
        HttpHeaders headers = authenticate();
        ingredient("Wheat Flour", "Grain");
        when(mealDbClient.searchByName("Pancakes")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52768", "Pancakes", "Breakfast", "American",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52768")).thenReturn(Optional.of(pancakeDetail("Pancakes", "4")));
        search(headers, "?name=Pancakes&threshold=0");

        long id = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52768").orElseThrow().getId();
        detail(headers, id);
        detail(headers, id);

        verify(mealDbClient, times(1)).lookupById("52768");
    }

    @Test
    void search_refreshesStaleProviderRow_onFilteredQuery() {
        HttpHeaders headers = authenticate();
        ingredient("Wheat Flour", "Grain");
        when(mealDbClient.searchByName("Pancakes")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52768", "Pancakes", "Breakfast", "American",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52768")).thenReturn(Optional.of(pancakeDetail("Pancakes", "4")));
        search(headers, "?name=Pancakes&threshold=0");

        Recipe stored = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52768").orElseThrow();
        stored.setRefreshedAt(Instant.now().minus(Duration.ofDays(2)));
        recipeRepository.saveAndFlush(stored);

        when(mealDbClient.lookupById("52768"))
                .thenReturn(Optional.of(pancakeDetail("Fresh Pancakes", "6")));
        ResponseEntity<List<RecipeSummaryDto>> second =
                search(headers, "?name=Pancakes&threshold=0");

        assertThat(second.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Fresh Pancakes"));
        verify(mealDbClient, times(2)).lookupById("52768");
    }

    @Test
    void refresh_keepsLocalCopy_whenProviderNoLongerServesTheRecipe() {
        HttpHeaders headers = authenticate();
        ingredient("Wheat Flour", "Grain");
        when(mealDbClient.searchByName("Pancakes")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52768", "Pancakes", "Breakfast", "American",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52768")).thenReturn(Optional.of(pancakeDetail("Pancakes", "4")));
        search(headers, "?name=Pancakes&threshold=0");

        Recipe stored = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52768").orElseThrow();
        stored.setRefreshedAt(Instant.now().minus(Duration.ofDays(2)));
        recipeRepository.saveAndFlush(stored);

        when(mealDbClient.lookupById("52768")).thenReturn(Optional.empty());
        ResponseEntity<RecipeDetailDto> first = detail(headers, stored.getId());
        ResponseEntity<RecipeDetailDto> again = detail(headers, stored.getId());

        assertThat(first.getBody().name()).isEqualTo("Pancakes");
        assertThat(again.getBody().name()).isEqualTo("Pancakes");
        verify(mealDbClient, times(2)).lookupById("52768");
    }

    private TheMealDbClient.MealDetail pancakeDetail(String name, String servings) {
        return new TheMealDbClient.MealDetail(
                "52768", name, "Breakfast", "American", "Mix and fry.",
                null, null, null,
                List.of(new TheMealDbClient.IngredientMeasure("Wheat Flour", "200g")),
                null, servings);
    }

    @Test
    void search_materialisesProviderHitsOnNameQuery() {
        HttpHeaders headers = authenticate();
        Ingredient flour = ingredient("Wheat Flour", "Grain");
        Ingredient milk = ingredient("Milk", "Dairy");
        Ingredient egg = ingredient("Egg, whole, raw", "Egg");
        allergenRow(egg, "EGG");

        when(mealDbClient.searchByName("Pancakes")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52768", "Pancakes", "Breakfast", "American",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52768")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "52768", "Pancakes", "Breakfast", "American", "Mix and fry.",
                        null, null, null,
                        List.of(
                                new TheMealDbClient.IngredientMeasure("Wheat Flour", "200g"),
                                new TheMealDbClient.IngredientMeasure("Milk", "1 1/2 cups"),
                                new TheMealDbClient.IngredientMeasure("Egg, whole", "2")),
                        null, "4")));

        // First search triggers materialisation; the second shares the Redis cache, so the
        // provider must be called once and the unique (source, external_id) row kept single.
        search(headers, "?name=Pancakes&threshold=0");
        ResponseEntity<List<RecipeSummaryDto>> second = search(headers, "?name=Pancakes&threshold=0");

        verify(mealDbClient, times(1)).searchByName("Pancakes");
        verify(mealDbClient, times(1)).lookupById("52768");
        assertThat(second.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Pancakes"));

        Recipe materialised = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52768").orElseThrow();
        List<RecipeIngredient> lines = recipeIngredientRepository.findByRecipeIdOrderByPositionAsc(materialised.getId());
        assertThat(lines).hasSize(3);

        RecipeIngredient flourLine = lines.get(0);
        assertThat(flourLine.getIngredientId()).isEqualTo(flour.getId());
        assertThat(flourLine.getIngredientName()).isEqualTo("Wheat Flour");
        assertThat(flourLine.getOriginalUnitCode()).isEqualTo("GRAM");
        assertThat(flourLine.getCanonicalUnitCode()).isEqualTo("GRAM");
        assertThat(flourLine.getCanonicalAmount()).isEqualByComparingTo("200");

        RecipeIngredient milkLine = lines.get(1);
        assertThat(milkLine.getIngredientId()).isEqualTo(milk.getId());
        assertThat(milkLine.getIngredientName()).isEqualTo("Milk");
        assertThat(milkLine.getOriginalUnitCode()).isEqualTo("CUP");
        assertThat(milkLine.getCanonicalUnitCode()).isEqualTo("ML");
        assertThat(milkLine.getOriginalAmount()).isEqualByComparingTo("1.5");
        assertThat(milkLine.getCanonicalAmount()).isEqualByComparingTo("354.882");

        RecipeIngredient eggLine = lines.get(2);
        assertThat(eggLine.getIngredientId()).isEqualTo(egg.getId());
        // The stored name is the catalog's, not the provider's wording ("Egg, whole").
        assertThat(eggLine.getIngredientName()).isEqualTo("Egg, whole, raw");
        assertThat(eggLine.getOriginalUnitCode()).isEqualTo("UNIT");
        assertThat(eggLine.getCanonicalUnitCode()).isEqualTo("UNIT");
        assertThat(eggLine.getOriginalAmount()).isEqualByComparingTo("2");
        assertThat(eggLine.getCanonicalAmount()).isEqualByComparingTo("2");

        // Nutrients are weighted from the flour line and divided across the servings.
        ResponseEntity<RecipeDetailDto> detail = detail(headers, materialised.getId());
        assertThat(detail.getBody().nutrients()).satisfies(n -> {
            assertThat(n.energyKcal()).isEqualByComparingTo("50");
            assertThat(n.proteinG()).isEqualByComparingTo("0.5");
            assertThat(n.fatG()).isEqualByComparingTo("0.5");
            assertThat(n.carbsG()).isEqualByComparingTo("0.5");
        });
        assertThat(detail.getBody().allergenCodes()).containsExactlyInAnyOrder("EGG");

        // The derived allergen takes part in the opt-in filter like any other recipe.
        excludeFromDiet(headers, "EGG");
        assertThat(search(headers, "?name=Pancakes&threshold=0&allergenSafe=true").getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .isEmpty();
    }

    @Test
    void search_skipsMaterialisationWhenNoIngredientMatches() {
        HttpHeaders headers = authenticate();
        // Catalog rows sharing no word with the provider hit remain unmatched.
        ingredient("Milk", "Dairy");
        ingredient("Egg, whole, raw", "Egg");

        when(mealDbClient.searchByName("beef")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "52971", "Beef Wellington", "Beef", "British",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("52971")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "52971", "Beef Wellington", "Beef", "British", "Wrap it.",
                        null, null, null,
                        List.of(new TheMealDbClient.IngredientMeasure("Beef Tenderloin", "1 1/2 lb")),
                        null, "4")));

        search(headers, "?name=beef&threshold=0");

        verify(mealDbClient).lookupById("52971");
        assertThat(recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "52971")).isEmpty();
    }

    // ============ RECOMMEND ============

    @Test
    void search_partialMatchPersistsAllLines_soPartialRecipesNeverScoreFull() {
        HttpHeaders headers = authenticate();
        // The catalog understands only flour (as quinoa flour) and egg, like a user whose
        // synced data is thin; apples, sugar, salt and the rest have no row to match.
        Ingredient quinoa = ingredient("Quinoa Flour", "Grain");
        Ingredient egg = ingredient("Egg, whole, raw", "Egg");
        stock(quinoa, "300");
        stock(egg, "100");

        when(mealDbClient.searchByName("apple pie")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "53575", "Apple Pie", "Dessert", null,
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("53575")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "53575", "Apple Pie", "Dessert", null, "Bake it.",
                        null, null, null,
                        List.of(
                                new TheMealDbClient.IngredientMeasure("Apples", "4"),
                                new TheMealDbClient.IngredientMeasure("Eggs", "2"),
                                new TheMealDbClient.IngredientMeasure("Sugar", "125 ml"),
                                new TheMealDbClient.IngredientMeasure("Flour", "200 g"),
                                new TheMealDbClient.IngredientMeasure("Salt", "0.5 tsp"),
                                new TheMealDbClient.IngredientMeasure("Bicarbonate Of Soda", "1 tsp"),
                                new TheMealDbClient.IngredientMeasure("Sour Milk", "250 ml")),
                        null, "8")));

        ResponseEntity<List<RecipeSummaryDto>> response = search(headers, "?name=apple+pie&threshold=0");
        assertThat(response.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> {
                    // The two understood lines are stocked, but seven real ingredients were
                    // measured: 5 unmatched lines still count as missing, never 100%.
                    assertThat(summary.missingCount()).isEqualTo(5);
                    assertThat(summary.matchScore()).isNotEqualTo(1.0);
                });

        Recipe materialised = recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "53575").orElseThrow();
        List<RecipeIngredient> lines = recipeIngredientRepository.findByRecipeIdOrderByPositionAsc(materialised.getId());
        assertThat(lines).hasSize(7);
        long matched = lines.stream().filter(l -> Boolean.TRUE.equals(l.getMatched())).count();
        assertThat(matched).isEqualTo(2);
        lines.stream().filter(l -> !Boolean.TRUE.equals(l.getMatched())).forEach(l -> {
            assertThat(l.getIngredientId()).isNull();
            assertThat(l.getIngredientName()).isNotBlank();
            assertThat(l.getCanonicalUnitCode()).isNull();
        });

        // The detail exposes the same truth: full line list, coverage only for matched.
        ResponseEntity<RecipeDetailDto> detail = detail(headers, materialised.getId());
        assertThat(detail.getBody().ingredients()).hasSize(7);
        assertThat(detail.getBody().ingredients().stream()
                .filter(RecipeIngredientDetailDto::matched).count()).isEqualTo(2);
        assertThat(detail.getBody().coverage()).hasSize(2);
        assertThat(detail.getBody().matchScore()).isEqualTo(2.0 / 7.0);
    }

    // ============ RECOMMEND ============

    @Test
    void recommend_drivesProviderSearchFromPantryAndScoresHits() {
        HttpHeaders headers = authenticate();
        Ingredient chicken = ingredient("Chicken", "Poultry");
        Ingredient lemon = ingredient("Lemon", "Fruit");
        stock(chicken, "300");
        stock(lemon, "100");

        // Lemon matches nothing on the provider, chicken does; the word is the first
        // token of the catalog name ("Lemon" → "lemon", "Chicken" → "chicken").
        when(mealDbClient.filterByIngredient("lemon")).thenReturn(List.of());
        when(mealDbClient.filterByIngredient("chicken")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "54001", "Lemon Chicken", "Chicken", "British",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("54001")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "54001", "Lemon Chicken", "Chicken", "British", "Roast it brown.",
                        null, null, null,
                        List.of(
                                new TheMealDbClient.IngredientMeasure("Lemon", "1"),
                                new TheMealDbClient.IngredientMeasure("Chicken", "300g")),
                        null, "4")));

        ResponseEntity<List<RecipeSummaryDto>> response = recommend(headers, "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.name()).isEqualTo("Lemon Chicken");
                    assertThat(summary.matchScore()).isEqualTo(1.0);
                    assertThat(summary.missingCount()).isEqualTo(0);
                });

        verify(mealDbClient, times(1)).filterByIngredient("chicken");
        verify(mealDbClient, times(1)).filterByIngredient("lemon");
        verify(mealDbClient, times(1)).lookupById("54001");
        assertThat(recipeRepository.findBySourceAndExternalId("THE_MEAL_DB", "54001")).isPresent();
    }

    @Test
    void recommend_emptyPantryReturnsEmptyWithoutTouchingProvider() {
        HttpHeaders headers = authenticate();

        ResponseEntity<List<RecipeSummaryDto>> response = recommend(headers, "");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        verify(mealDbClient, never()).filterByIngredient(anyString());
    }

    @Test
    void recommend_ingredientsWithoutProviderHitsReturnEmpty() {
        HttpHeaders headers = authenticate();
        stock(ingredient("Broccoli", "Vegetable"), "400");
        stock(ingredient("Pasta", "Grain"), "250");

        when(mealDbClient.filterByIngredient("broccoli")).thenReturn(List.of());
        when(mealDbClient.filterByIngredient("pasta")).thenReturn(List.of());

        assertThat(recommend(headers, "").getBody()).isEmpty();
        verify(mealDbClient, times(1)).filterByIngredient("broccoli");
        verify(mealDbClient, times(1)).filterByIngredient("pasta");
    }

    @Test
    void recommend_secondCallIsServedFromTheWordCache() {
        HttpHeaders headers = authenticate();
        Ingredient oats = ingredient("Oats", "Grain");
        Ingredient honey = ingredient("Honey", "Sweet");
        stock(oats, "80");
        stock(honey, "30");

        when(mealDbClient.filterByIngredient("oats")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "54002", "Oat Porridge", "Breakfast", "British",
                        null, null, null, null, null)));
        when(mealDbClient.filterByIngredient("honey")).thenReturn(List.of());
        when(mealDbClient.lookupById("54002")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "54002", "Oat Porridge", "Breakfast", "British", "Simmer and sweeten.",
                        null, null, null,
                        List.of(new TheMealDbClient.IngredientMeasure("Oats", "80g")),
                        null, "2")));

        recommend(headers, "");
        ResponseEntity<List<RecipeSummaryDto>> second = recommend(headers, "");

        assertThat(second.getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.name()).isEqualTo("Oat Porridge"));

        // The word cache answers the second call and materialisation is idempotent, so
        // neither the provider search nor the lookup runs twice.
        verify(mealDbClient, times(1)).filterByIngredient("oats");
        verify(mealDbClient, times(1)).lookupById("54002");
    }

    @Test
    void recommend_givesEveryPantryWordAFairShareOfTheHitSet() {
        HttpHeaders headers = authenticate();
        Ingredient egg = ingredient("Egg", "Egg");
        Ingredient bacon = ingredient("Bacon", "Meat");
        stock(egg, "5000");
        stock(bacon, "300");

        // Alone, "egg" returns more meals than the hit set allows, yet the "bacon" word
        // must still be felt: slots are claimed word by word, not by the biggest
        // provider list first.
        List<TheMealDbClient.MealSummary> eggMeals = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String id = String.valueOf(54010 + i);
            eggMeals.add(new TheMealDbClient.MealSummary(
                    id, "Egg Meal " + i, "Side", "British",
                    null, null, null, null, null));
            when(mealDbClient.lookupById(id)).thenReturn(Optional.of(
                    new TheMealDbClient.MealDetail(
                            id, "Egg Meal " + i, "Side", "British",
                            "Boil it.", null, null, null,
                            List.of(new TheMealDbClient.IngredientMeasure("Egg", "100g")),
                            null, "1")));
        }
        when(mealDbClient.filterByIngredient("egg")).thenReturn(eggMeals);
        when(mealDbClient.filterByIngredient("bacon")).thenReturn(List.of(
                new TheMealDbClient.MealSummary(
                        "54099", "Bacon Meal", "Chicken", "British",
                        null, null, null, null, null)));
        when(mealDbClient.lookupById("54099")).thenReturn(Optional.of(
                new TheMealDbClient.MealDetail(
                        "54099", "Bacon Meal", "Chicken", "British",
                        "Roast it.", null, null, null,
                        List.of(new TheMealDbClient.IngredientMeasure("Bacon", "200g")),
                        null, "4")));

        List<RecipeSummaryDto> body = recommend(headers, "").getBody();
        assertThat(body)
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .hasSize(13);
        assertThat(body)
                .filteredOn(summary -> summary.name().equals("Bacon Meal"))
                .singleElement()
                .satisfies(summary -> assertThat(summary.matchScore()).isEqualTo(1.0));
    }

    @Test
    void recommend_neverReturnsMoreThanTheHitSetCap() {
        HttpHeaders headers = authenticate();
        Ingredient tofu = ingredient("Tofu", "Pulse");
        stock(tofu, "3000");

        // One provider list dwarfs the hit set; the response must still be capped at
        // MAX_RECOMMEND_HITS instead of materialising every match.
        List<TheMealDbClient.MealSummary> tofuMeals = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String id = String.valueOf(54100 + i);
            tofuMeals.add(new TheMealDbClient.MealSummary(
                    id, "Tofu Meal " + i, "Vegetarian", "British",
                    null, null, null, null, null));
            when(mealDbClient.lookupById(id)).thenReturn(Optional.of(
                    new TheMealDbClient.MealDetail(
                            id, "Tofu Meal " + i, "Vegetarian", "British",
                            "Stir-fry it.", null, null, null,
                            List.of(new TheMealDbClient.IngredientMeasure("Tofu", "120g")),
                            null, "1")));
        }
        when(mealDbClient.filterByIngredient("tofu")).thenReturn(tofuMeals);

        assertThat(recommend(headers, "").getBody())
                .filteredOn(summary -> summary.source().equals("THE_MEAL_DB"))
                .hasSize(24);
    }

    // ============ DETAIL ============

    @Test
    void detail_returnsPerIngredientCoverageBreakdown() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Ingredient chicken = ingredient("Chicken", "Poultry");
        Recipe recipe = recipe("USER", "Chicken Rice Bowl");
        line(recipe, rice, "100", 0);
        line(recipe, chicken, "200", 1);
        stock(rice, "150");

        ResponseEntity<RecipeDetailDto> response = detail(headers, recipe.getId());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecipeDetailDto body = response.getBody();
        assertThat(body.matchScore()).isEqualTo(0.5);
        assertThat(body.coverage()).hasSize(2);
        assertThat(coverageOf(body, rice.getId())).satisfies(c -> {
            assertThat(c.fullyCovered()).isTrue();
            assertThat(c.availableAmount()).isEqualByComparingTo("150");
            assertThat(c.neededAmount()).isEqualByComparingTo("100");
        });
        assertThat(coverageOf(body, chicken.getId())).satisfies(c -> {
            assertThat(c.fullyCovered()).isFalse();
            assertThat(c.availableAmount()).isEqualByComparingTo("0");
        });
    }

    @Test
    void detail_unknownRecipeReturns404() {
        HttpHeaders headers = authenticate();
        String path = RECIPES + "/999999";

        ResponseEntity<ErrorResponse> response = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, path);
    }

    // ============ CREATE ============

    @Test
    void create_returns201WithCanonicalAmountsAndDerivedAllergens() {
        HttpHeaders headers = authenticate();
        Ingredient egg = ingredient("Egg", "Egg");
        allergenRow(egg, "EGG");

        CreateRecipeRequest request = new CreateRecipeRequest(
                "Scrambled Eggs", "Breakfast", "British", "Stir until set.",
                null, null, List.of("quick"), 2, null,
                List.of(new CreateRecipeIngredientRequest(egg.getId(),
                        new BigDecimal("100"), "GRAM", 0)));

        ResponseEntity<RecipeDetailDto> response = rest.exchange(
                RECIPES, HttpMethod.POST, new HttpEntity<>(request, headers), RecipeDetailDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RecipeDetailDto body = response.getBody();
        assertThat(body.source()).isEqualTo("USER");
        assertThat(body.externalId()).isNotBlank();
        assertThat(body.ingredients()).singleElement().satisfies(i -> {
            assertThat(i.canonicalAmount()).isEqualByComparingTo("100");
            assertThat(i.canonicalUnitCode()).isEqualTo("GRAM");
        });
        assertThat(body.allergenCodes()).containsExactlyInAnyOrder("EGG");
        assertThat(body.matchScore()).isNull();
        assertThat(body.coverage()).isEmpty();
        // A freshly created recipe belongs to its author and starts private.
        assertThat(body.owned()).isTrue();
        assertThat(body.published()).isFalse();
    }

    @Test
    void create_unknownIngredientReturns400() {
        HttpHeaders headers = authenticate();
        CreateRecipeRequest request = new CreateRecipeRequest(
                "Ghost Recipe", null, null, null, null, null, null, null, null,
                List.of(new CreateRecipeIngredientRequest(999_999L,
                        new BigDecimal("100"), "GRAM", 0)));

        ResponseEntity<ErrorResponse> response = rest.exchange(
                RECIPES, HttpMethod.POST, new HttpEntity<>(request, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, RECIPES);
    }

    @Test
    void create_blankNameReturns400() {
        HttpHeaders headers = authenticate();
        CreateRecipeRequest request = new CreateRecipeRequest(
                "   ", null, null, null, null, null, null, null, null, List.of());

        ResponseEntity<ErrorResponse> response = rest.exchange(
                RECIPES, HttpMethod.POST, new HttpEntity<>(request, headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, RECIPES);
    }

    @Test
    void create_persistsNutrientsPerServing_soDetailAgrees() {
        HttpHeaders headers = authenticate();
        Ingredient egg = ingredient("Egg, whole, raw", "Egg");
        CreateRecipeRequest request = new CreateRecipeRequest(
                "Omelette", "Breakfast", "French", "Whisk and fold.",
                null, null, null, 2, null,
                List.of(new CreateRecipeIngredientRequest(egg.getId(),
                        new BigDecimal("100"), "GRAM", 0)));

        ResponseEntity<RecipeDetailDto> created = rest.exchange(
                RECIPES, HttpMethod.POST, new HttpEntity<>(request, headers), RecipeDetailDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().nutrients()).satisfies(n -> {
            assertThat(n.energyKcal()).isEqualByComparingTo("50");
            assertThat(n.proteinG()).isEqualByComparingTo("0.5");
            assertThat(n.fatG()).isEqualByComparingTo("0.5");
            assertThat(n.carbsG()).isEqualByComparingTo("0.5");
        });

        // The same row is served on a later read instead of recomputed on the fly.
        ResponseEntity<RecipeDetailDto> readBack = detail(headers, created.getBody().id());
        assertThat(readBack.getBody().nutrients().energyKcal()).isEqualByComparingTo("50");
    }

    // ============ DIET FILTERING ============

    @Test
    void create_derivesDietTagsFromPerServingNutrients() {
        HttpHeaders headers = authenticate();
        Ingredient lean = ingredient("Lean Chicken", "Poultry");
        lean.setProteinG(30.0);
        lean.setFatG(3.0);
        lean.setCarbsG(5.0);
        ingredientRepository.saveAndFlush(lean);

        long recipeId = createRecipe(headers, "Lean Plate", lean, "200", 1);

        // 200 g of a 30/3/5-per-100g food, one serving: 60 g protein, 6 g fat, 10 g carbs.
        assertThat(recipeDietRepository.findDietsByRecipeId(recipeId))
                .containsExactlyInAnyOrder(Diet.HIGH_PROTEIN, Diet.LOW_CARB, Diet.LOW_FAT);
    }

    @Test
    void search_filtersByDiet_andBalancedShowsEveryRecipe() {
        HttpHeaders headers = authenticate();
        Ingredient lean = ingredient("Lean Meat", "Meat");
        lean.setProteinG(40.0);
        lean.setFatG(5.0);
        lean.setCarbsG(2.0);
        ingredientRepository.saveAndFlush(lean);

        Ingredient fatty = ingredient("Fatty Meat", "Meat");
        fatty.setProteinG(40.0);
        fatty.setFatG(60.0);
        fatty.setCarbsG(2.0);
        ingredientRepository.saveAndFlush(fatty);

        long leanId = createRecipe(headers, "Lean Dish", lean, "200", 1);
        long fattyId = createRecipe(headers, "Fatty Dish", fatty, "200", 1);

        // Stock both so each recipe clears the default coverage threshold and reaches the filter.
        stock(lean, "200");
        stock(fatty, "200");

        assertThat(recipeIds(search(headers, "?diet=LOW_FAT")))
                .contains(leanId).doesNotContain(fattyId);
        // Both are high protein, so that filter keeps them and BALANCED narrows nothing.
        assertThat(recipeIds(search(headers, "?diet=HIGH_PROTEIN"))).contains(leanId, fattyId);
        assertThat(recipeIds(search(headers, "?diet=BALANCED"))).contains(leanId, fattyId);
    }

    @Test
    void createUserRecipe_storesTheCatalogIngredientNameOnEachLine() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");

        long recipeId = createRecipe(headers, "Rice Bowl", rice, "100", 1);

        // The stored name feeds consumers that read the line without the catalog —
        // cooking deduction labels and the profile's top-ingredient ranking.
        assertThat(recipeIngredientRepository.findByRecipeIdOrderByPositionAsc(recipeId))
                .singleElement()
                .satisfies(line -> assertThat(line.getIngredientName()).isEqualTo("Rice"));
    }

    // ============ PUBLISHING (H8-E) ============

    @Test
    void publish_makesRecipeVisibleToOthers_andIsIdempotent() {
        HttpHeaders owner = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("USER", "Shared Pilaf");
        line(recipe, rice, "100", 0);

        HttpHeaders other = authenticateSecondUser();
        assertThat(publishedList(other)).isEmpty();

        ResponseEntity<RecipeDetailDto> first = publish(owner, recipe.getId());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().published()).isTrue();
        assertThat(first.getBody().owned()).isTrue();

        // Publishing again changes nothing and still reports published.
        assertThat(publish(owner, recipe.getId()).getBody().published()).isTrue();

        assertThat(publishedList(other)).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(recipe.getId());
            assertThat(summary.published()).isTrue();
            assertThat(summary.owned()).isFalse();
        });
    }

    @Test
    void unpublish_hidesRecipeAgain() {
        HttpHeaders owner = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("USER", "Ephemeral Pilaf");
        line(recipe, rice, "100", 0);
        publish(owner, recipe.getId());

        HttpHeaders other = authenticateSecondUser();
        assertThat(publishedList(other)).hasSize(1);

        ResponseEntity<RecipeDetailDto> response = unpublish(owner, recipe.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().published()).isFalse();
        assertThat(publishedList(other)).isEmpty();
        // A second unpublish is a harmless no-op.
        assertThat(unpublish(owner, recipe.getId()).getBody().published()).isFalse();
    }

    @Test
    void search_includesPublishedRecipeFromAnotherAuthor() {
        HttpHeaders author = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("USER", "Author Pilaf");
        line(recipe, rice, "100", 0);
        publish(author, recipe.getId());

        // The reader stocks the ingredient, so the recipe can score above the threshold.
        HttpHeaders reader = authenticateSecondUser();
        stockFor(secondUser(), rice, "100");

        assertThat(search(reader, "").getBody())
                .filteredOn(s -> s.id() == recipe.getId())
                .singleElement()
                .satisfies(s -> assertThat(s.published()).isTrue());
    }

    @Test
    void publish_otherUsersRecipe_returns403() {
        HttpHeaders owner = authenticate();
        Recipe recipe = recipe("USER", "Not Yours");
        HttpHeaders other = authenticateSecondUser();

        ResponseEntity<ErrorResponse> response = publishError(other, recipe.getId());

        assertError(response, HttpStatus.FORBIDDEN,
                RECIPES + "/" + recipe.getId() + "/publish");
    }

    @Test
    void publish_providerRecipe_returns400() {
        HttpHeaders headers = authenticate();
        Recipe provider = recipe("THE_MEAL_DB", "Provider Dish");

        ResponseEntity<ErrorResponse> response = publishError(headers, provider.getId());

        assertError(response, HttpStatus.BAD_REQUEST,
                RECIPES + "/" + provider.getId() + "/publish");
    }

    @Test
    void publish_unknownRecipe_returns404() {
        HttpHeaders headers = authenticate();

        ResponseEntity<ErrorResponse> response = publishError(headers, 999_999L);

        assertError(response, HttpStatus.NOT_FOUND, RECIPES + "/999999/publish");
    }

    @Test
    void publish_requiresAuthentication() {
        assertUnauthorized(RECIPES + "/1/publish", HttpMethod.POST);
    }

    // ============ MY RECIPES (COOKBOOK "CREATED") ============

    @Test
    void mine_listsOwnPrivateAndPublishedRecipes_newestFirst() {
        HttpHeaders owner = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe privateDraft = recipe("USER", "Private Draft");
        line(privateDraft, rice, "100", 0);
        Recipe publicDish = recipe("USER", "Public Dish");
        line(publicDish, rice, "100", 0);
        publish(owner, publicDish.getId());

        // A provider recipe is not authored by anyone and never belongs here.
        Recipe provider = recipe("THE_MEAL_DB", "Provider Dish");

        assertThat(mineList(owner))
                .extracting(RecipeSummaryDto::name)
                .containsExactlyInAnyOrder("Private Draft", "Public Dish")
                .doesNotContain("Provider Dish");

        // Coverage is not a filter: the draft scores 0/1 against an empty pantry yet
        // still appears (the whole point of the listing).
        assertThat(mineList(owner))
                .filteredOn(s -> s.id() == privateDraft.getId())
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.owned()).isTrue();
                    assertThat(s.published()).isFalse();
                });
    }

    @Test
    void mine_doesNotIncludeAnotherUsersPrivateRecipe() {
        HttpHeaders owner = authenticate();
        Recipe recipe = recipe("USER", "Own Secret");
        HttpHeaders other = authenticateSecondUser();

        assertThat(mineList(other)).isEmpty();
        assertThat(mineList(owner)).extracting(RecipeSummaryDto::name).containsExactly("Own Secret");
        assertThat(recipe.getId()).isNotNull();
    }

    @Test
    void mine_requiresAuthentication() {
        assertUnauthorized(RECIPES + "/mine", HttpMethod.GET);
    }

    // ============ HELPERS ============

    private ResponseEntity<List<RecipeSummaryDto>> search(HttpHeaders headers, String query) {
        return rest.exchange(RECIPES + query, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private List<Long> recipeIds(ResponseEntity<List<RecipeSummaryDto>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().stream().map(RecipeSummaryDto::id).toList();
    }

    private long createRecipe(HttpHeaders headers, String name, Ingredient ingredient,
                              String grams, int servings) {
        CreateRecipeRequest request = new CreateRecipeRequest(
                name, null, null, null, null, null, null, servings, null,
                List.of(new CreateRecipeIngredientRequest(ingredient.getId(),
                        new BigDecimal(grams), "GRAM", 0)));

        ResponseEntity<RecipeDetailDto> response = rest.exchange(
                RECIPES, HttpMethod.POST, new HttpEntity<>(request, headers), RecipeDetailDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private ResponseEntity<List<RecipeSummaryDto>> recommend(HttpHeaders headers, String query) {
        return rest.exchange(RECIPES + "/recommend" + query, HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<RecipeDetailDto> detail(HttpHeaders headers, long recipeId) {
        return rest.exchange(RECIPES + "/" + recipeId, HttpMethod.GET,
                new HttpEntity<>(headers), RecipeDetailDto.class);
    }

    private IngredientCoverageDto coverageOf(RecipeDetailDto body, long ingredientId) {
        return body.coverage().stream()
                .filter(c -> c.ingredientId() == ingredientId)
                .findFirst()
                .orElseThrow();
    }

    private Long currentUser() {
        return userRepository
                .findByUsernameOrEmail(DEFAULT_USER.get("username"), DEFAULT_USER.get("email"))
                .orElseThrow()
                .getId();
    }

    private Ingredient ingredient(String name, String category) {
        Ingredient ingredient = new Ingredient();
        ingredient.setFdcId(FDC_SEQ.incrementAndGet());
        ingredient.setName(name);
        ingredient.setCategory(category);
        ingredient.setDataType("SR Legacy");
        ingredient.setSyncedAt(Instant.now());
        ingredient.setEnergyKcal(100.0);
        ingredient.setProteinG(1.0);
        ingredient.setFatG(1.0);
        ingredient.setCarbsG(1.0);

        return ingredientRepository.saveAndFlush(ingredient);
    }

    private Recipe recipe(String source, String name) {
        Recipe recipe = new Recipe();
        recipe.setSource(source);
        recipe.setExternalId(UUID.randomUUID().toString());
        recipe.setName(name);
        recipe.setCreatedAt(Instant.now());
        recipe.setUpdatedAt(Instant.now());
        // USER rows carry their author: search scopes private recipes to the owner, and
        // these tests create them as the already-authenticated default user.
        if ("USER".equals(source)) {
            recipe.setOwnerId(currentUser());
        }

        return recipeRepository.saveAndFlush(recipe);
    }

    private void line(Recipe recipe, Ingredient ingredient, String grams, int position) {
        RecipeIngredient line = new RecipeIngredient();
        line.setRecipeId(recipe.getId());
        line.setIngredientId(ingredient.getId());
        line.setMatched(true);
        line.setOriginalAmount(new BigDecimal(grams));
        line.setOriginalUnitCode("GRAM");
        line.setCanonicalAmount(new BigDecimal(grams));
        line.setCanonicalUnitCode("GRAM");
        line.setPosition(position);
        recipeIngredientRepository.saveAndFlush(line);
    }

    private void stock(Ingredient ingredient, String grams) {
        stockFor(currentUser(), ingredient, grams);
    }

    private void stockFor(Long userId, Ingredient ingredient, String grams) {
        // Upsert: the pantry holds one line per (user, ingredient), so a second insert
        // would be refused by the unique constraint instead of updating the quantity.
        PantryItem item = pantryItemRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(userId)
                        && i.getIngredientId().equals(ingredient.getId()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            item = new PantryItem();
            item.setUserId(userId);
            item.setIngredientId(ingredient.getId());
            item.setUnitCode("GRAM");
        }
        item.setQuantity(new BigDecimal(grams));
        pantryItemRepository.saveAndFlush(item);
    }

    private HttpHeaders authenticateSecondUser() {
        ResponseEntity<com.pantrybase.api.auth.dto.AuthResponse> registered = registerUser(
                java.util.Map.of("username", "JaneDoe", "email", "jane.doe@example.com",
                        "password", "password123"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registered.getBody().accessToken());
        return headers;
    }

    private Long secondUser() {
        return userRepository
                .findByUsernameOrEmail("JaneDoe", "jane.doe@example.com")
                .orElseThrow()
                .getId();
    }

    private List<RecipeSummaryDto> publishedList(HttpHeaders headers) {
        ResponseEntity<List<RecipeSummaryDto>> response = rest.exchange(
                RECIPES + "/published", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<RecipeSummaryDto> mineList(HttpHeaders headers) {
        ResponseEntity<List<RecipeSummaryDto>> response = rest.exchange(
                RECIPES + "/mine", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<RecipeDetailDto> publish(HttpHeaders headers, long recipeId) {
        return rest.exchange(RECIPES + "/" + recipeId + "/publish", HttpMethod.POST,
                new HttpEntity<>(headers), RecipeDetailDto.class);
    }

    private ResponseEntity<RecipeDetailDto> unpublish(HttpHeaders headers, long recipeId) {
        return rest.exchange(RECIPES + "/" + recipeId + "/publish", HttpMethod.DELETE,
                new HttpEntity<>(headers), RecipeDetailDto.class);
    }

    private ResponseEntity<ErrorResponse> publishError(HttpHeaders headers, long recipeId) {
        return rest.exchange(RECIPES + "/" + recipeId + "/publish", HttpMethod.POST,
                new HttpEntity<>(headers), ErrorResponse.class);
    }

    private void allergenRow(Ingredient ingredient, String code) {
        AllergenMapping mapping = new AllergenMapping();
        mapping.setIngredientId(ingredient.getId());
        mapping.setAllergenCode(code);
        allergenMappingRepository.saveAndFlush(mapping);
    }

    private void recipeAllergen(Recipe recipe, String code) {
        RecipeAllergen allergen = new RecipeAllergen();
        allergen.setRecipeId(recipe.getId());
        allergen.setAllergenCode(code);
        recipeAllergenRepository.saveAndFlush(allergen);
    }

    // Through the real endpoint rather than the entity: allergyExclusions is lazy, the
    // user was loaded outside a session, and adding to the collection blew up with a
    // LazyInitializationException. It also matches how the frontend will set the diet.
    private void excludeFromDiet(HttpHeaders headers, String code) {
        ResponseEntity<AllergyExclusionsResponse> response = rest.exchange(
                "/api/users/allergy-exclusions", HttpMethod.PUT,
                new HttpEntity<>(new ReplaceAllergyExclusionsRequest(List.of(code)), headers),
                AllergyExclusionsResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
