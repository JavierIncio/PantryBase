package com.pantrybase.api.pantry;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.IngredientMeasure;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.repository.ConsumptionEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fixtures shared by the pantry endpoint suites.
 *
 * <p>Extracted rather than copied when the adjustment endpoint arrived: the same
 * ingredient, the same density class and the same "add a line" call appear in every
 * pantry test, and two copies of a fixture drift apart silently — the second one gets an
 * extra default that only its own suite knows about.</p>
 *
 * <p>The ingredients are inserted straight into the catalog so no test depends on a
 * provider stub, and so the density class can be chosen per case: with one, a volume
 * really does reach the pantry in grams; without one, it stays in milliliters instead of
 * failing.</p>
 */
abstract class AbstractPantryIntegrationTest extends AbstractIntegrationTest {

    protected static final String PANTRY = "/api/pantry";

    /** Real FDC ids, so a mistake in the payload shows up as a miss and not as a fixture. */
    protected static final long MILK_FDC_ID = 170899;
    protected static final long FLOUR_FDC_ID = 168918;
    protected static final long UNKNOWN_FDC_ID = 999_999L;

    @Autowired
    protected IngredientRepository ingredientRepository;
    @Autowired
    protected IngredientMeasureRepository measureRepository;
    @Autowired
    protected ConsumptionEntryRepository consumptionEntryRepository;

    @BeforeEach
    void cleanCatalog() {
        measureRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    /**
     * Registers the second user and returns its headers.
     *
     * <p>Every use goes through this instead of {@link #authenticate()}, which would
     * collide on the unique username and make an isolation test fail during setup for a
     * reason that has nothing to do with isolation.</p>
     */
    protected HttpHeaders authenticateAsSecondUser() {
        Map<String, String> credentials = Map.of(
                "username", "JaneDoe",
                "email", "jane.doe@example.com",
                "password", "password123");

        ResponseEntity<AuthResponse> registered = registerUser(credentials);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registered.getBody().accessToken());

        return headers;
    }

    protected ResponseEntity<PantryItemResponse> add(HttpHeaders headers, long fdcId,
                                                     BigDecimal amount, String unitCode) {
        return rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(fdcId, amount, unitCode, null, null),
                        headers),
                PantryItemResponse.class);
    }

    /** Adds the seeded milk line, which resolves to grams through the MILK density class. */
    protected ResponseEntity<PantryItemResponse> addMilk(HttpHeaders headers, BigDecimal amount) {
        return add(headers, MILK_FDC_ID, amount, "L");
    }

    protected ResponseEntity<List<PantryItemResponse>> list(HttpHeaders headers) {
        return rest.exchange(PANTRY, HttpMethod.GET, new HttpEntity<>(headers), itemsType());
    }

    protected Ingredient materialize(long fdcId, String name, String category) {
        return materialize(fdcId, name, category, null);
    }

    protected Ingredient materialize(long fdcId, String name, String category, String densityClass) {
        Ingredient ingredient = new Ingredient();
        ingredient.setFdcId(fdcId);
        ingredient.setName(name);
        ingredient.setCategory(category);
        ingredient.setDataType("SR Legacy");
        ingredient.setDensityClass(densityClass);
        ingredient.setSyncedAt(Instant.now());
        ingredient.setEnergyKcal(100.0);
        ingredient.setProteinG(1.0);
        ingredient.setFatG(1.0);
        ingredient.setCarbsG(1.0);

        return ingredientRepository.saveAndFlush(ingredient);
    }

    protected IngredientMeasure measure(Ingredient ingredient, String unitCode, String gramPerUnit) {
        IngredientMeasure measure = new IngredientMeasure();
        measure.setIngredient(ingredient);
        measure.setUnitCode(unitCode);
        measure.setGramPerUnit(new BigDecimal(gramPerUnit));

        return measureRepository.saveAndFlush(measure);
    }

    protected static ParameterizedTypeReference<List<PantryItemResponse>> itemsType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}