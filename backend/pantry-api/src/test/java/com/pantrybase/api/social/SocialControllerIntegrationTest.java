package com.pantrybase.api.social;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.social.dto.HistoryItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the H8 social contract: favorite/saved toggles are idempotent and owner-scoped,
 * and the cooking history records completed sessions exactly once.
 */
class SocialControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String HISTORY = "/api/history";
    private static final String FAVORITES = "/api/favorites";
    private static final String SAVED = "/api/saved";
    private static final String COOKING = "/api/cooking/sessions";
    private static final String PANTRY = "/api/pantry";
    private static final AtomicLong FDC_SEQ = new AtomicLong(960_000);

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;

    @BeforeEach
    void cleanCatalog() {
        ingredientRepository.deleteAll();
    }

    @Test
    void favoriteToggle_isIdempotent() {
        HttpHeaders headers = authenticate();
        Recipe recipe = recipe("Favorite Soup");

        assertThat(toggle(HttpMethod.PUT, recipe.getId(), "favorite", headers).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(toggle(HttpMethod.PUT, recipe.getId(), "favorite", headers).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(favorites(headers)).containsExactly(recipe.getId());

        assertThat(toggle(HttpMethod.DELETE, recipe.getId(), "favorite", headers).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(toggle(HttpMethod.DELETE, recipe.getId(), "favorite", headers).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(favorites(headers)).isEmpty();
    }

    @Test
    void savedToggle_isIndependentFromFavorite() {
        HttpHeaders headers = authenticate();
        Recipe recipe = recipe("Saved Stew");

        assertThat(toggle(HttpMethod.PUT, recipe.getId(), "saved", headers).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(saved(headers)).containsExactly(recipe.getId());
        assertThat(favorites(headers)).isEmpty();
    }

    @Test
    void favorite_unknownRecipe_returns404() {
        HttpHeaders headers = authenticate();

        ResponseEntity<ErrorResponse> response = rest.exchange(
                "/api/recipes/999999/favorite", HttpMethod.PUT, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, "/api/recipes/999999/favorite");
    }

    @Test
    void favorite_requiresAuthentication() {
        Recipe recipe = recipe("Anonymous Soup");

        assertUnauthorized("/api/recipes/" + recipe.getId() + "/favorite", HttpMethod.PUT);
    }

    @Test
    void history_recordsCompletedSessionExactlyOnce() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice");
        Recipe recipe = recipe("History Bowl");
        line(recipe, rice, "100", 0);
        stock(headers, rice, "500");

        long sessionId = createSession(headers, recipe.getId());
        assertThat(complete(headers, sessionId, false).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(history(headers))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.recipeId()).isEqualTo(recipe.getId());
                    assertThat(entry.cookingSessionId()).isEqualTo(sessionId);
                });

        // The state machine rejects the double completion and history stays a single row.
        ResponseEntity<String> conflict = rest.exchange(COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(CookingSessionStatus.COMPLETED, false),
                        headers), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(history(headers)).hasSize(1);
    }

    @Test
    void history_doesNotRecordAbandonedSession() {
        HttpHeaders headers = authenticate();
        Recipe recipe = recipe("Abandoned Bake");
        long sessionId = createSession(headers, recipe.getId());

        ResponseEntity<CookingSessionResponse> abandoned = updateState(
                headers, sessionId, CookingSessionStatus.ABANDONED);
        assertThat(abandoned.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(history(headers)).isEmpty();
    }

    // ============ HELPERS ============

    private ResponseEntity<Void> toggle(HttpMethod method, long recipeId, String kind, HttpHeaders headers) {
        return rest.exchange("/api/recipes/" + recipeId + "/" + kind, method,
                new HttpEntity<>(headers), Void.class);
    }

    private List<Long> favorites(HttpHeaders headers) {
        ResponseEntity<List<Long>> response = rest.exchange(
                FAVORITES, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Long> saved(HttpHeaders headers) {
        ResponseEntity<List<Long>> response = rest.exchange(
                SAVED, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<HistoryItemResponse> history(HttpHeaders headers) {
        ResponseEntity<List<HistoryItemResponse>> response = rest.exchange(
                HISTORY, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                });
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

    private ResponseEntity<CookingSessionResponse> complete(HttpHeaders headers, long sessionId,
                                                            boolean acceptShortage) {
        return updateStateInternal(headers, sessionId, CookingSessionStatus.COMPLETED, acceptShortage);
    }

    private ResponseEntity<CookingSessionResponse> updateState(HttpHeaders headers, long sessionId,
                                                               CookingSessionStatus status) {
        return updateStateInternal(headers, sessionId, status, false);
    }

    private ResponseEntity<CookingSessionResponse> updateStateInternal(HttpHeaders headers, long sessionId,
                                                                       CookingSessionStatus status,
                                                                       boolean acceptShortage) {
        return rest.exchange(COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(status, acceptShortage), headers),
                CookingSessionResponse.class);
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
