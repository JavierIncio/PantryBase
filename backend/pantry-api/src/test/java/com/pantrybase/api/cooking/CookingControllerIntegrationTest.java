package com.pantrybase.api.cooking;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.cooking.domain.CookingSession;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CompletionDeductionResponse;
import com.pantrybase.api.cooking.dto.CookingSessionResponse;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
import com.pantrybase.api.pantry.domain.ConsumptionOrigin;
import com.pantrybase.api.pantry.dto.ConsumptionEntryResponse;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.recipe.repository.RecipeRepository;
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
 * Covers the H5 contract: a cooking session's state machine and the deduction it makes
 * on completion.
 *
 * <p>The suite is built around the milestone's definition of done — completing a recipe
 * reduces the pantry by exactly the canonical amounts it charges, and every drop is
 * explained by a {@code COOKING_SESSION} entry. The shortage cases are here because the
 * interesting decision is not what happens when stock is enough, but what the server
 * does when it is not: refuse with the plan, and only deduct to zero if the caller
 * accepts the shortfall.</p>
 */
class CookingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKING = "/api/cooking/sessions";
    private static final String PANTRY = "/api/pantry";
    private static final AtomicLong FDC_SEQ = new AtomicLong(950_000);

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;

    @BeforeEach
    void cleanRecipeData() {
        // Children first: a recipe ingredient references its recipe, which references
        // nothing here, but the next suite needs the catalog empty too.
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    @Test
    void create_requiresAuthentication() {
        assertUnauthorized(COOKING, HttpMethod.POST);
    }

    @Test
    void create_unknownRecipe_returns404() {
        ResponseEntity<ErrorResponse> response = rest.exchange(COOKING, HttpMethod.POST,
                new HttpEntity<>(new CreateCookingSessionRequest(987_654L), authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, COOKING);
    }

    @Test
    void complete_deductsEachIngredientExactly_andTheLedgerAgrees() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Ingredient chicken = ingredient("Chicken", "Poultry");
        Ingredient peas = ingredient("Peas", "Vegetable");
        Recipe recipe = recipe("Chicken Rice Bowl");
        line(recipe, rice, "100", 0);
        line(recipe, chicken, "200", 1);
        line(recipe, peas, "300", 2);
        stock(headers, rice, "500");
        stock(headers, chicken, "500");
        stock(headers, peas, "500");

        long sessionId = createSession(headers, recipe.getId());
        ResponseEntity<CookingSessionResponse> response =
                updateState(headers, sessionId, CookingSessionStatus.COMPLETED, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(session -> {
            assertThat(session.status()).isEqualTo(CookingSessionStatus.COMPLETED);
            assertThat(session.completedAt()).isNotNull();
            assertThat(session.deduction().hasShortage()).isFalse();
            assertThat(session.deduction().lines()).hasSize(3);
        });

        // The definition of done: each line moves by exactly its canonical amount.
        assertThat(quantityOf(headers, rice)).isEqualByComparingTo("400");
        assertThat(quantityOf(headers, chicken)).isEqualByComparingTo("300");
        assertThat(quantityOf(headers, peas)).isEqualByComparingTo("200");

        // And the movement is explained, not just applied: the last entry of each line is
        // the deduction, signed negative and tagged as a session's.
        PantryItemResponse riceLine = pantryLine(headers, rice);
        List<ConsumptionEntryResponse> riceEntries = entries(headers, riceLine.id());
        assertThat(riceEntries).last().satisfies(entry -> {
            assertThat(entry.origin()).isEqualTo(ConsumptionOrigin.COOKING_SESSION);
            assertThat(entry.deltaQuantity()).isEqualByComparingTo("-100");
        });
        assertThat(sumOfDeltas(riceEntries)).isEqualByComparingTo(riceLine.quantity());
    }

    @Test
    void complete_shortfallWithoutAcceptance_returns409WithPlan_andLeavesStockUntouched() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);
        stock(headers, rice, "100");

        long sessionId = createSession(headers, recipe.getId());
        ResponseEntity<CompletionDeductionResponse> response = rest.exchange(
                COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(CookingSessionStatus.COMPLETED, false), headers),
                CompletionDeductionResponse.class);

        // The body is the plan, not the usual error envelope: the client needs to name the
        // lines that fall short and what each would become if the user accepts — here, take
        // the 100 the pantry has, leaving the line at zero, and the missing grams off-book.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull().satisfies(deduction -> {
            assertThat(deduction.hasShortage()).isTrue();
            assertThat(deduction.lines()).singleElement().satisfies(line -> {
                assertThat(line.needed()).isEqualByComparingTo("200");
                assertThat(line.available()).isEqualByComparingTo("100");
                assertThat(line.deducted()).isEqualByComparingTo("100");
                assertThat(line.remaining()).isEqualByComparingTo("0");
            });
        });

        // Nothing moved and the session is still open, so the user can accept or abandon.
        assertThat(quantityOf(headers, rice)).isEqualByComparingTo("100");
        CookingSession session = cookingSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(CookingSessionStatus.IN_PROGRESS);
        assertThat(session.getCompletedAt()).isNull();
    }

    @Test
    void complete_acceptedShortfall_deductsToZero_andRecordsTheActualDelta() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);
        stock(headers, rice, "100");

        long sessionId = createSession(headers, recipe.getId());
        ResponseEntity<CookingSessionResponse> response =
                updateState(headers, sessionId, CookingSessionStatus.COMPLETED, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().deduction()).satisfies(deduction -> {
            assertThat(deduction.hasShortage()).isTrue();
            assertThat(deduction.lines()).singleElement().satisfies(line -> {
                assertThat(line.deducted()).isEqualByComparingTo("100");
                assertThat(line.remaining()).isEqualByComparingTo("0");
            });
        });

        // The line reaches zero and the ledger records only what was really taken: the
        // grams the user added off the books are not invented as stock that existed.
        assertThat(quantityOf(headers, rice)).isEqualByComparingTo("0");
        PantryItemResponse riceLine = pantryLine(headers, rice);
        assertThat(entries(headers, riceLine.id())).last().satisfies(entry -> {
            assertThat(entry.origin()).isEqualTo(ConsumptionOrigin.COOKING_SESSION);
            assertThat(entry.deltaQuantity()).isEqualByComparingTo("-100");
        });
    }

    @Test
    void complete_twice_returns409_andDoesNotDoubleDeduct() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);
        stock(headers, rice, "500");

        long sessionId = createSession(headers, recipe.getId());
        updateState(headers, sessionId, CookingSessionStatus.COMPLETED, false);
        BigDecimal afterFirst = quantityOf(headers, rice);

        ResponseEntity<ErrorResponse> response =
                updateStateError(headers, sessionId, CookingSessionStatus.COMPLETED);

        // A terminal session cannot be completed again, which is exactly what stops the
        // deduction from running a second time.
        assertError(response, HttpStatus.CONFLICT, COOKING + "/" + sessionId + "/state");
        assertThat(quantityOf(headers, rice)).isEqualByComparingTo(afterFirst);
    }

    @Test
    void pauseResume_areAllowed_butARepeatedStateIsRefused() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);

        long sessionId = createSession(headers, recipe.getId());

        assertThat(updateState(headers, sessionId, CookingSessionStatus.PAUSED, false)
                .getBody().status()).isEqualTo(CookingSessionStatus.PAUSED);

        // Pausing an already-paused session is not a move in the machine, not a no-op:
        // silently accepting it would hide a client that has lost track of the state.
        assertError(updateStateError(headers, sessionId, CookingSessionStatus.PAUSED),
                HttpStatus.CONFLICT, COOKING + "/" + sessionId + "/state");

        assertThat(updateState(headers, sessionId, CookingSessionStatus.IN_PROGRESS, false)
                .getBody().status()).isEqualTo(CookingSessionStatus.IN_PROGRESS);
    }

    @Test
    void abandon_isTerminal_andStampsCompletion() {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);

        long sessionId = createSession(headers, recipe.getId());
        updateState(headers, sessionId, CookingSessionStatus.ABANDONED, false);

        CookingSession session = cookingSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(CookingSessionStatus.ABANDONED);
        assertThat(session.getCompletedAt()).isNotNull();

        assertError(updateStateError(headers, sessionId, CookingSessionStatus.COMPLETED),
                HttpStatus.CONFLICT, COOKING + "/" + sessionId + "/state");
    }

    @Test
    void state_ofAnotherUsersSession_returns404() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateSecondUser();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, "200", 0);

        long theirSession = createSession(theirs, recipe.getId());

        ResponseEntity<ErrorResponse> response = rest.exchange(
                COOKING + "/" + theirSession + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(CookingSessionStatus.ABANDONED, false), mine),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, COOKING + "/" + theirSession + "/state");
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

    private long createSession(HttpHeaders headers, long recipeId) {
        ResponseEntity<CookingSessionResponse> response = rest.exchange(COOKING, HttpMethod.POST,
                new HttpEntity<>(new CreateCookingSessionRequest(recipeId), headers),
                CookingSessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return response.getBody().id();
    }

    private ResponseEntity<CookingSessionResponse> updateState(HttpHeaders headers, long sessionId,
                                                               CookingSessionStatus status, boolean acceptShortage) {
        return rest.exchange(COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(status, acceptShortage), headers),
                CookingSessionResponse.class);
    }

    private ResponseEntity<ErrorResponse> updateStateError(HttpHeaders headers, long sessionId,
                                                           CookingSessionStatus status) {
        return rest.exchange(COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                new HttpEntity<>(new UpdateCookingSessionStateRequest(status, false), headers),
                ErrorResponse.class);
    }

    private BigDecimal quantityOf(HttpHeaders headers, Ingredient ingredient) {
        return pantryLine(headers, ingredient).quantity();
    }

    private PantryItemResponse pantryLine(HttpHeaders headers, Ingredient ingredient) {
        ResponseEntity<List<PantryItemResponse>> response = rest.exchange(
                PANTRY, HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {
                });

        return response.getBody().stream()
                .filter(item -> item.fdcId() == ingredient.getFdcId())
                .findFirst()
                .orElseThrow();
    }

    private List<ConsumptionEntryResponse> entries(HttpHeaders headers, long itemId) {
        ResponseEntity<List<ConsumptionEntryResponse>> response = rest.exchange(
                PANTRY + "/" + itemId + "/entries", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        return response.getBody();
    }

    private BigDecimal sumOfDeltas(List<ConsumptionEntryResponse> entries) {
        return entries.stream()
                .map(ConsumptionEntryResponse::deltaQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private void stock(HttpHeaders headers, Ingredient ingredient, String grams) {
        // Through the real endpoint, so the opening balance is written to the ledger:
        // a row inserted straight into the table would leave the deduction as the only
        // entry and silently break the identity the ledger rests on.
        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(ingredient.getFdcId(),
                        new BigDecimal(grams), "GRAM", null, null), headers),
                PantryItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}