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
import com.pantrybase.api.recipe.dto.RecipeSummaryDto;
import com.pantrybase.api.recipe.repository.AllergenMappingRepository;
import com.pantrybase.api.recipe.repository.RecipeAllergenRepository;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.recipe.repository.RecipeRepository;
import com.pantrybase.api.recipe.service.TheMealDbClient;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
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

    // ============ HELPERS ============

    private ResponseEntity<List<RecipeSummaryDto>> search(HttpHeaders headers, String query) {
        return rest.exchange(RECIPES + query, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
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

        return recipeRepository.saveAndFlush(recipe);
    }

    private void line(Recipe recipe, Ingredient ingredient, String grams, int position) {
        RecipeIngredient line = new RecipeIngredient();
        line.setRecipeId(recipe.getId());
        line.setIngredientId(ingredient.getId());
        line.setOriginalAmount(new BigDecimal(grams));
        line.setOriginalUnitCode("GRAM");
        line.setCanonicalAmount(new BigDecimal(grams));
        line.setCanonicalUnitCode("GRAM");
        line.setPosition(position);
        recipeIngredientRepository.saveAndFlush(line);
    }

    private void stock(Ingredient ingredient, String grams) {
        Long userId = currentUser();
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
