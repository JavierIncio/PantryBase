package com.pantrybase.api.cooking;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.dto.CreateCookingSessionRequest;
import com.pantrybase.api.cooking.dto.UpdateCookingSessionStateRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H9-4: the completion path under real concurrency — several sessions of the same user
 * finishing at once over the same pantry line.
 *
 * <p>The assertions are invariants, not statistics: whichever way the transactions
 * interleave, the pantry must move by exactly what the successful completions charged,
 * the ledger must explain the final quantity, and a lost optimistic-lock race must leave
 * the loser's session open (its whole transaction rolled back) and be recoverable by a
 * retry. The test deliberately does <em>not</em> require a conflict to happen: if the
 * scheduler serialises the requests, every invariant still has to hold, so a lucky run
 * fails for the same reasons an unlucky one would.</p>
 *
 * <p>Stock is sized for every worker (8 × 200 g), so a 409 can only come from a lost
 * race — never from a shortage, which would be a different code path with a different
 * body.</p>
 */
class CookingCompletionConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKING = "/api/cooking/sessions";
    private static final String PANTRY = "/api/pantry";
    private static final AtomicLong FDC_SEQ = new AtomicLong(960_000);

    private static final int WORKERS = 8;
    private static final String GRAMS_PER_SESSION = "200";

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;

    @BeforeEach
    void cleanRecipeData() {
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    @Test
    void concurrentCompletions_neverLoseAWrite_andTheLedgerAlwaysExplainsTheQuantity() throws Exception {
        HttpHeaders headers = authenticate();
        Ingredient rice = ingredient("Rice", "Grain");
        Recipe recipe = recipe("Risotto");
        line(recipe, rice, GRAMS_PER_SESSION, 0);
        stock(headers, rice, new BigDecimal(GRAMS_PER_SESSION)
                .multiply(BigDecimal.valueOf(WORKERS)));

        List<Long> sessionIds = new ArrayList<>();
        for (int worker = 0; worker < WORKERS; worker++) {
            sessionIds.add(createSession(headers, recipe.getId()));
        }

        List<HttpStatus> statuses = completeConcurrently(headers, sessionIds);

        // Every response is one of the two documented outcomes; a 500 would mean the
        // optimistic-lock race escapes the error handling instead of becoming a 409.
        assertThat(statuses).allSatisfy(status ->
                assertThat(status).isIn(HttpStatus.OK, HttpStatus.CONFLICT));

        List<Long> losers = new ArrayList<>();
        long succeeded = 0;
        for (int i = 0; i < statuses.size(); i++) {
            if (statuses.get(i) == HttpStatus.OK) {
                succeeded++;
            } else {
                losers.add(sessionIds.get(i));
            }
        }

        // Invariant 1: the pantry moved by exactly what the winners charged.
        BigDecimal charged = new BigDecimal(GRAMS_PER_SESSION).multiply(BigDecimal.valueOf(succeeded));
        BigDecimal expected = new BigDecimal(GRAMS_PER_SESSION)
                .multiply(BigDecimal.valueOf(WORKERS))
                .subtract(charged);
        assertThat(quantityOf(headers, rice)).isEqualByComparingTo(expected);

        // Invariant 2: every movement is on the books — the ledger sum is the quantity.
        PantryItemResponse line = pantryLine(headers, rice);
        assertThat(sumOfDeltas(entries(headers, line.id()))).isEqualByComparingTo(line.quantity());

        // Invariant 3: a lost race leaves nothing behind. The session did not flip to
        // COMPLETED, because the status write shares the transaction with the deduction
        // that lost the race and rolled the whole thing back.
        for (long loser : losers) {
            assertThat(cookingSessionRepository.findById(loser).orElseThrow().getStatus())
                    .isEqualTo(CookingSessionStatus.IN_PROGRESS);
        }

        // Invariant 4: the 409 is a retryable conflict, not a dead end — and the retry
        // moves the stock exactly once more.
        if (!losers.isEmpty()) {
            ResponseEntity<String> retry = rest.exchange(
                    COOKING + "/" + losers.get(0) + "/state", HttpMethod.PUT,
                    new HttpEntity<>(new UpdateCookingSessionStateRequest(
                            CookingSessionStatus.COMPLETED, false), headers),
                    String.class);

            assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(quantityOf(headers, rice))
                    .isEqualByComparingTo(expected.subtract(new BigDecimal(GRAMS_PER_SESSION)));
            PantryItemResponse after = pantryLine(headers, rice);
            assertThat(sumOfDeltas(entries(headers, after.id())))
                    .isEqualByComparingTo(after.quantity());
            assertThat(cookingSessionRepository.findById(losers.get(0)).orElseThrow().getStatus())
                    .isEqualTo(CookingSessionStatus.COMPLETED);
        }
    }

    /**
     * Fires one completion per session from its own thread, released together by a
     * barrier so the requests overlap instead of queueing by accident.
     *
     * @param headers   the caller's auth headers
     * @param sessionIds the sessions to complete, in submission order
     * @return the HTTP status of each completion, aligned with {@code sessionIds}
     */
    private List<HttpStatus> completeConcurrently(HttpHeaders headers, List<Long> sessionIds)
            throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(sessionIds.size());
        ExecutorService pool = Executors.newFixedThreadPool(sessionIds.size());
        List<Future<HttpStatus>> futures = new ArrayList<>();

        for (long sessionId : sessionIds) {
            futures.add(pool.submit(() -> {
                startLine.await();
                return (HttpStatus) rest.exchange(
                        COOKING + "/" + sessionId + "/state", HttpMethod.PUT,
                        new HttpEntity<>(new UpdateCookingSessionStateRequest(
                                CookingSessionStatus.COMPLETED, false), headers),
                        String.class).getStatusCode();
            }));
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> future : futures) {
            statuses.add(future.get());
        }
        return statuses;
    }

    // ============ HELPERS ============
    private long createSession(HttpHeaders headers, long recipeId) {
        ResponseEntity<com.pantrybase.api.cooking.dto.CookingSessionResponse> response =
                rest.exchange(COOKING, HttpMethod.POST,
                        new HttpEntity<>(new CreateCookingSessionRequest(recipeId), headers),
                        com.pantrybase.api.cooking.dto.CookingSessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return response.getBody().id();
    }

    private BigDecimal quantityOf(HttpHeaders headers, Ingredient ingredient) {
        return pantryLine(headers, ingredient).quantity();
    }

    private PantryItemResponse pantryLine(HttpHeaders headers, Ingredient ingredient) {
        ResponseEntity<List<PantryItemResponse>> response = rest.exchange(
                PANTRY, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
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

    private void stock(HttpHeaders headers, Ingredient ingredient, BigDecimal grams) {
        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(ingredient.getFdcId(),
                        grams, "GRAM", null, null), headers),
                PantryItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
