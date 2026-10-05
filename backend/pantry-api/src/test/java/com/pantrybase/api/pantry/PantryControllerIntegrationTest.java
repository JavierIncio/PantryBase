package com.pantrybase.api.pantry;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.pantry.dto.CreatePantryItemRequest;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.dto.UpdatePantryItemRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The creation, read, edit and delete of pantry lines.
 *
 * <p>Covers what the lines are and how they are stored. Moving a quantity is
 * {@link PantryAdjustmentIntegrationTest}'s subject, and the gap between the two is
 * deliberate: one suite per question, so a failure names the question that broke.</p>
 */
class PantryControllerIntegrationTest extends AbstractPantryIntegrationTest {

    private static final String INGREDIENT_DETAIL = "/api/catalog/ingredients/";

    @Test
    void create_storesTheQuantityInGramsWhateverTheUnit() {
        HttpHeaders headers = authenticate();
        Ingredient flour = materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        // The provider weighed one cup at 122 g, which is how a volume becomes a weight.
        measureRepository.save(measure(flour, "CUP", "122"));

        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(FLOUR_FDC_ID, new BigDecimal("2"),
                        "CUP", "Alacena", "Para pan"), headers),
                PantryItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.name()).isEqualTo("Wheat flour, white, all-purpose");
            assertThat(body.category()).isEqualTo("Grains");
            assertThat(body.fdcId()).isEqualTo(FLOUR_FDC_ID);
            assertThat(body.unitCode()).isEqualTo("GRAM");
            assertThat(body.quantity()).isCloseTo(new BigDecimal("244"), within(new BigDecimal("0.000001")));
            assertThat(body.location()).isEqualTo("Alacena");
            assertThat(body.note()).isEqualTo("Para pan");
            assertThat(body.updatedAt()).isNotNull();
        });
    }

    @Test
    void create_aCountKeepsItsUnit() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products", "MILK");

        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(MILK_FDC_ID, new BigDecimal("6"),
                        "UNIT", "Nevera", null), headers),
                PantryItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Six eggs are six eggs; nobody means 300 grams of them.
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.unitCode()).isEqualTo("UNIT");
            assertThat(body.quantity()).isEqualByComparingTo(new BigDecimal("6"));
            assertThat(body.note()).isNull();
        });
    }

    @Test
    void create_aVolumeWithoutADensityIsKeptInMilliliters() {
        HttpHeaders headers = authenticate();
        // No measure and no density class, so there is no honest weight for a liter.
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");

        ResponseEntity<PantryItemResponse> response = add(headers, FLOUR_FDC_ID,
                new BigDecimal("1"), "L");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Our data is the thing missing, not the request, and the user does own a liter.
        // Guessing a density would store a confidently wrong weight.
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.unitCode()).isEqualTo("ML");
            assertThat(body.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
        });
    }

    @Test
    void create_aVolumeWithADensityClassIsStoredInGrams() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products", "MILK");

        ResponseEntity<PantryItemResponse> response = addMilk(headers, new BigDecimal("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // 1 L = 1000 ml x 1.031288 g/ml of the seeded MILK class.
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.unitCode()).isEqualTo("GRAM");
            assertThat(body.quantity()).isCloseTo(new BigDecimal("1031.288"),
                    within(new BigDecimal("0.000001")));
        });
    }

    @Test
    void create_unknownIngredient_returns404AndNamesTheCallToMake() {
        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(UNKNOWN_FDC_ID, new BigDecimal("1"),
                        "GRAM", null, null), authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, PANTRY);
        assertThat(response.getBody().message())
                .contains(INGREDIENT_DETAIL + UNKNOWN_FDC_ID);
    }

    @Test
    void create_unknownUnit_returns400() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(MILK_FDC_ID, new BigDecimal("1"),
                        "SPOON", null, null), headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, PANTRY);
    }

    @Test
    void create_rejectsAnAmountThatIsNotPositive() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(MILK_FDC_ID, BigDecimal.ZERO,
                        "GRAM", null, null), headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, PANTRY);
    }

    @Test
    void create_aSecondLineForTheSameIngredient_returns409() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        addMilk(headers, new BigDecimal("1"));

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY, HttpMethod.POST,
                new HttpEntity<>(new CreatePantryItemRequest(MILK_FDC_ID, new BigDecimal("2"),
                        "L", null, null), headers),
                ErrorResponse.class);

        // One line per ingredient, and the message says where to go instead of merging
        // the two bottles behind the user's back.
        assertError(response, HttpStatus.CONFLICT, PANTRY);
        assertThat(response.getBody().message()).contains("adjustment");
        assertThat(list(headers).getBody()).hasSize(1);
    }

    @Test
    void list_returnsOnlyTheOwnLinesGroupedByCategory() {
        HttpHeaders mine = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        addMilk(mine, new BigDecimal("1"));
        add(mine, FLOUR_FDC_ID, new BigDecimal("500"), "GRAM");

        HttpHeaders other = authenticateAsSecondUser();
        add(other, MILK_FDC_ID, new BigDecimal("9"), "L");

        ResponseEntity<List<PantryItemResponse>> response = list(mine);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(items -> {
            // Ordered by category then name, which is how the view sections them, so the
            // client never has to sort to draw.
            assertThat(items).extracting(PantryItemResponse::category)
                    .containsExactly("Dairy and Egg Products", "Grains");
            assertThat(items).extracting(PantryItemResponse::fdcId)
                    .containsExactly(MILK_FDC_ID, FLOUR_FDC_ID);
        });
    }

    @Test
    void list_emptyPantryIsAnEmptyArray() {
        ResponseEntity<List<PantryItemResponse>> response = list(authenticate());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void update_replacesLocationAndNote() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> response = rest.exchange(PANTRY + "/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(new UpdatePantryItemRequest("Nevera", "Caduca el viernes"), headers),
                PantryItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.location()).isEqualTo("Nevera");
            assertThat(body.note()).isEqualTo("Caduca el viernes");
        });
    }

    @Test
    void update_cannotChangeTheQuantity() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products", "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        // No quantity in the contract at all: a client that sends one is talking to a
        // server that ignores it, and the audit stays complete because the only way in is
        // the adjustment endpoint.
        rest.exchange(PANTRY + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("quantity", "9999", "location", "Nevera"), headers),
                PantryItemResponse.class);

        assertThat(list(headers).getBody())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.quantity()).isCloseTo(new BigDecimal("1031.288"),
                            within(new BigDecimal("0.000001")));
                    assertThat(item.unitCode()).isEqualTo("GRAM");
                    assertThat(item.location()).isEqualTo("Nevera");
                });
    }

    @Test
    void update_bumpsTheVersionSoAConcurrentWriteIsLosing() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> first = rest.exchange(PANTRY + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(new UpdatePantryItemRequest("Nevera", null), headers),
                PantryItemResponse.class);
        ResponseEntity<PantryItemResponse> second = rest.exchange(PANTRY + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(new UpdatePantryItemRequest("Alacena", null), headers),
                PantryItemResponse.class);

        assertThat(first.getBody().version()).isLessThan(second.getBody().version());
    }

    @Test
    void delete_removesTheLineAndItsLedger() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<Void> response = rest.exchange(PANTRY + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(headers).getBody()).isEmpty();
        // The entries go with it: an entry with no line to belong to is an orphan, not a
        // record, and keeping it would make the deletion impossible without a second call.
        assertThat(consumptionEntryRepository.findAll()).isEmpty();
    }

    @Test
    void anotherUsersLine_isNotFoundAndNotEditable() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateAsSecondUser();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        long theirId = add(theirs, MILK_FDC_ID, new BigDecimal("9"), "L").getBody().id();

        // 404 rather than 403: a 403 would confirm the id exists, which is enough to walk
        // somebody else's inventory by watching which ids are refused instead of absent.
        ResponseEntity<ErrorResponse> update = rest.exchange(PANTRY + "/" + theirId, HttpMethod.PUT,
                new HttpEntity<>(new UpdatePantryItemRequest("Nevera", null), mine),
                ErrorResponse.class);
        ResponseEntity<ErrorResponse> delete = rest.exchange(PANTRY + "/" + theirId,
                HttpMethod.DELETE, new HttpEntity<>(mine), ErrorResponse.class);

        assertError(update, HttpStatus.NOT_FOUND, PANTRY + "/" + theirId);
        assertError(delete, HttpStatus.NOT_FOUND, PANTRY + "/" + theirId);
        assertThat(list(theirs).getBody()).hasSize(1);
        assertThat(list(mine).getBody()).isEmpty();
    }

    @Test
    void everyEndpoint_requiresAuthentication() {
        assertUnauthorized(PANTRY, HttpMethod.GET);
        assertUnauthorized(PANTRY, HttpMethod.POST);
        assertUnauthorized(PANTRY + "/1", HttpMethod.PUT);
        assertUnauthorized(PANTRY + "/1", HttpMethod.DELETE);
        assertUnauthorized(PANTRY + "/1/adjustments", HttpMethod.POST);
        assertUnauthorized(PANTRY + "/1/entries", HttpMethod.GET);
    }
}