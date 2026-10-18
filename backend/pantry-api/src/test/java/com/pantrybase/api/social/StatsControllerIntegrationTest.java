package com.pantrybase.api.social;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.social.dto.UserStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code GET /api/users/me/stats}: the numbers must agree with the completed
 * cooking history and the ranking must weigh repeated cooks.
 */
class StatsControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String STATS = "/api/users/me/stats";
    private static final String COOKING = "/api/cooking/sessions";
    private static final String PANTRY = "/api/pantry";
    private static final AtomicLong FDC_SEQ = new AtomicLong(970_000);

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;

    @BeforeEach
    void cleanCatalog() {
        ingredientRepository.deleteAll();
    }

    @Test
    void stats_withNoHistory_areAllZero() {
        HttpHeaders headers = authenticate();

        UserStatsResponse stats = stats(headers);

        assertThat(stats.cookedSessions()).isZero();
        assertThat(stats.distinctRecipes()).isZero();
        assertThat(stats.topIngredients()).isEmpty();
    }

    @Test
    void stats_countRepeatedCooks_andRankIngredientsByUsage() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice");
        Ingredient chicken = ingredient("Chicken");

        Recipe bowl = recipe("Rice Bowl");
        line(bowl, rice, "100", 0);
        Recipe roast = recipe("Chicken Roast");
        line(roast, rice, "50", 0);
        line(roast, chicken, "200", 1);

        stock(headers, rice, "1000");
        stock(headers, chicken, "1000");

        complete(headers, createSession(headers, bowl.getId()));
        complete(headers, createSession(headers, bowl.getId()));
        complete(headers, createSession(headers, roast.getId()));

        UserStatsResponse stats = stats(headers);

        assertThat(stats.cookedSessions()).isEqualTo(3);
        assertThat(stats.distinctRecipes()).isEqualTo(2);
        assertThat(stats.topIngredients()).satisfiesExactly(
                first -> {
                    assertThat(first.ingredientId()).isEqualTo(rice.getId());
                    assertThat(first.name()).isEqualTo("Rice");
                    assertThat(first.count()).isEqualTo(3);
                },
                second -> {
                    assertThat(second.ingredientId()).isEqualTo(chicken.getId());
                    assertThat(second.name()).isEqualTo("Chicken");
                    assertThat(second.count()).isEqualTo(1);
                });
    }

    @Test
    void stats_areScopedToTheCaller() {
        HttpHeaders mine = authenticate();
        // The recipe has no ingredients, so completing it deducts nothing.
        Recipe recipe = recipe("Private Bake");
        complete(mine, createSession(mine, recipe.getId()));

        HttpHeaders theirs = authenticateSecondUser();

        assertThat(stats(theirs).cookedSessions()).isZero();
        assertThat(stats(mine).cookedSessions()).isEqualTo(1);
    }

    @Test
    void stats_requireAuthentication() {
        assertUnauthorized(STATS, HttpMethod.GET);
    }

    // ============ HELPERS ============

    private HttpHeaders authenticateSecondUser() {
        ResponseEntity<com.pantrybase.api.auth.dto.AuthResponse> registered = registerUser(
                java.util.Map.of("username", "JaneDoe", "email", "jane.doe@example.com",
                        "password", "password123"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registered.getBody().accessToken());
        return headers;
    }

    private UserStatsResponse stats(HttpHeaders headers) {
        ResponseEntity<UserStatsResponse> response = rest.exchange(
                STATS, HttpMethod.GET, new HttpEntity<>(headers), UserStatsResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private long createSession(HttpHeaders headers, long recipeId) {
        ResponseEntity<CookingSessionResponse> response = rest.exchange(COOKING, HttpMethod.POST,
                new HttpEntity<>(new CreateCookingSessionRequest(recipeId), headers),
                CookingSessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private void complete(HttpHeaders headers, long sessionId) {
        ResponseEntity<String> response = rest.exchange(COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(CookingSessionStatus.COMPLETED, false),
                        headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void stock(HttpHeaders headers, Ingredient ingredient, String grams) {
        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(ingredient.getFdcId(),
                        new BigDecimal(grams), "GRAM", null, null), headers),
                PantryItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Ingredient ingredient(String name) {
        Ingredient ingredient = new Ingredient();
        ingredient.setFdcId(FDC_SEQ.incrementAndGet());
        ingredient.setName(name);
        ingredient.setCategory("Test");
        ingredient.setDataType("SR Legacy");
        ingredient.setSyncedAt(Instant.now());
        ingredient.setEnergyKcal(100.0);
        ingredient.setProteinG(1.0);
        ingredient.setFatG(1.0);
        ingredient.setCarbsG(1.0);
        return ingredientRepository.saveAndFlush(ingredient);
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setSource(Recipe.Source.USER.name());
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
        line.setIngredientName(ingredient.getName());
        line.setMatched(true);
        line.setOriginalAmount(new BigDecimal(grams));
        line.setOriginalUnitCode("GRAM");
        line.setCanonicalAmount(new BigDecimal(grams));
        line.setCanonicalUnitCode("GRAM");
        line.setPosition(position);
        recipeIngredientRepository.saveAndFlush(line);
    }
}
