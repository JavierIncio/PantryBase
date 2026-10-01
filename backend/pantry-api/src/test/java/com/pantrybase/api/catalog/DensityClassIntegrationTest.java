package com.pantrybase.api.catalog;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.dto.AssignDensityClassRequest;
import com.pantrybase.api.catalog.dto.ConvertUnitsRequest;
import com.pantrybase.api.catalog.dto.DensityClassResponse;
import com.pantrybase.api.catalog.dto.IngredientDensityClassResponse;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.UnitConversionResponse;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.catalog.service.IngredientCatalogService;
import com.pantrybase.api.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Import(StubFoodCatalogPortConfig.class)
class DensityClassIntegrationTest extends AbstractIntegrationTest {

    private static final String CLASSES = "/api/catalog/density-classes";
    private static final String ASSIGN = CLASSES + "/ingredients/%d";
    private static final String INGREDIENTS = "/api/catalog/ingredients";
    private static final String UNITS_CONVERT = "/api/units/convert";

    /** Sliced cheddar: real FDC id, and the food that has no usable volume portion. */
    private static final long CHEESE_FDC_ID = 170899;
    private static final long UNKNOWN_INGREDIENT_ID = 999_999L;

    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    IngredientMeasureRepository measureRepository;
    @Autowired
    IngredientCatalogService ingredientService;
    @Autowired
    StubFoodCatalogPort stubPort;

    @BeforeEach
    void cleanIngredients() {
        measureRepository.deleteAll();
        ingredientRepository.deleteAll();
        stubPort.fails();
    }

    @Test
    void listClasses_includesTheSeededOnesWithTheirProvenance() {
        ResponseEntity<List<DensityClassResponse>> response = rest.exchange(
                CLASSES, HttpMethod.GET, new HttpEntity<>(authenticate()), classesType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(classes -> {
            assertThat(classes).extracting(DensityClassResponse::code).contains("FLOUR", "MILK");
            // Ordered by code, so a consumer can render the list without sorting it.
            assertThat(classes).extracting(DensityClassResponse::code).isSorted();
        });

        DensityClassResponse milk = response.getBody().stream()
                .filter(c -> c.code().equals("MILK")).findFirst().orElseThrow();
        assertThat(milk.densityGPerMl()).isEqualByComparingTo("1.031288");
        // A class has to say how its density was established, or it is a bare number.
        assertThat(milk.source()).isNotBlank();
    }

    @Test
    void listClasses_requiresAuthentication() {
        assertUnauthorized(CLASSES, HttpMethod.GET);
    }

    @Test
    void assign_makesAFoodWithoutPortionsConvertible() {
        long ingredientId = materializeCheese();
        HttpHeaders admin = authenticateAsAdmin();

        // No measure and no class yet, so there is nothing honest to convert with.
        ResponseEntity<ErrorResponse> unconvertible = rest.exchange(UNITS_CONVERT, HttpMethod.POST,
                new HttpEntity<>(new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM",
                        ingredientId, null), admin), ErrorResponse.class);
        assertError(unconvertible, HttpStatus.BAD_REQUEST, UNITS_CONVERT);

        ResponseEntity<IngredientDensityClassResponse> assigned =
                assign(ingredientId, "MILK", admin, IngredientDensityClassResponse.class);

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assigned.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.id()).isEqualTo(ingredientId);
            assertThat(body.fdcId()).isEqualTo(CHEESE_FDC_ID);
            assertThat(body.densityClass()).isEqualTo("MILK");
        });

        ResponseEntity<UnitConversionResponse> converted = convertCups(2, ingredientId, admin);
        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 2 cups = 473.176 ml, which at 1.031288 g/ml weighs 487.980730688 g. The result
        // keeps full precision because GRAM is canonical, so nothing rounds it.
        assertThat(converted.getBody()).isNotNull().satisfies(body ->
                assertThat(body.amount()).isCloseTo(new BigDecimal("487.980731"),
                        within(new BigDecimal("0.000001"))));
    }

    @Test
    void assign_isRejectedForANonAdmin() {
        long ingredientId = materializeCheese();

        ResponseEntity<ErrorResponse> response =
                assign(ingredientId, "MILK", authenticate(), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ingredientRepository.findById(ingredientId).orElseThrow().getDensityClass())
                .isNull();
    }

    @Test
    void assign_isRejectedForAnUnknownClass() {
        long ingredientId = materializeCheese();

        ResponseEntity<ErrorResponse> response =
                assign(ingredientId, "CHEESE", authenticateAsAdmin(), ErrorResponse.class);

        // The endpoint must not double as a way to introduce an unverified density.
        assertError(response, HttpStatus.BAD_REQUEST, String.format(ASSIGN, ingredientId));
        assertThat(ingredientRepository.findById(ingredientId).orElseThrow().getDensityClass())
                .isNull();
    }

    @Test
    void assign_unknownIngredient_returns404() {
        ResponseEntity<ErrorResponse> response =
                assign(UNKNOWN_INGREDIENT_ID, "MILK", authenticateAsAdmin(), ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, String.format(ASSIGN, UNKNOWN_INGREDIENT_ID));
    }

    @Test
    void assignedClass_isReportedInTheDetailAndSurvivesANewLookup() {
        long ingredientId = materializeCheese();
        HttpHeaders admin = authenticateAsAdmin();
        assign(ingredientId, "MILK", admin, IngredientDensityClassResponse.class);

        // A later provider lookup must report the curated class and never clear it.
        ResponseEntity<IngredientDetailResponse> detail = rest.exchange(
                INGREDIENTS + "/" + CHEESE_FDC_ID, HttpMethod.GET, new HttpEntity<>(admin),
                IngredientDetailResponse.class);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).isNotNull().satisfies(body ->
                assertThat(body.densityClass()).isEqualTo("MILK"));
        assertThat(ingredientRepository.findById(ingredientId).orElseThrow().getDensityClass())
                .isEqualTo("MILK");
    }

    private long materializeCheese() {
        stubPort.returns(Optional.of(new FoodProfile(CHEESE_FDC_ID, "Cheese, cheddar, sharp, sliced",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(403.0, 24.94, 33.1, 1.3), List.of())));

        return ingredientService.getById(CHEESE_FDC_ID).id();
    }

    private <T> ResponseEntity<T> assign(long ingredientId, String densityClass,
                                        HttpHeaders headers, Class<T> bodyType) {
        return rest.exchange(String.format(ASSIGN, ingredientId), HttpMethod.PATCH,
                new HttpEntity<>(new AssignDensityClassRequest(densityClass), headers), bodyType);
    }

    private ResponseEntity<UnitConversionResponse> convertCups(int amount, long ingredientId,
                                                              HttpHeaders headers) {
        return rest.exchange(UNITS_CONVERT, HttpMethod.POST,
                new HttpEntity<>(new ConvertUnitsRequest(new BigDecimal(String.valueOf(amount)),
                        "CUP", "GRAM", ingredientId, null), headers),
                UnitConversionResponse.class);
    }

    private static ParameterizedTypeReference<List<DensityClassResponse>> classesType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
