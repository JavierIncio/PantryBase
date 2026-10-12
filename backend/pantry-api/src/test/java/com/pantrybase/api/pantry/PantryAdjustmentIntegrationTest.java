package com.pantrybase.api.pantry;

import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.pantry.domain.ConsumptionOrigin;
import com.pantrybase.api.pantry.dto.AdjustPantryItemRequest;
import com.pantrybase.api.pantry.dto.ConsumePantryItemRequest;
import com.pantrybase.api.pantry.dto.ConsumptionEntryResponse;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Moving the quantity of a line and the ledger that explains it.
 *
 * <p>The suite is built around one identity: the sum of an item's entries is its
 * quantity. It is the only reason the ledger exists, and it is the thing a later change
 * would break quietly — a deduction that updates the item and forgets the entry, or an
 * entry written outside the transaction that moved the item, both leave every individual
 * assertion green.</p>
 *
 * <p>No test here simulates two concurrent writers. Reproducing an overlapping read and
 * write deterministically needs a latch inside the request, and a timing-based test in
 * this suite would be the first flaky one in the project. The lost-update case is
 * covered by {@code @Version} on the entity and the 409 mapping, both asserted by
 * reading rather than by racing.</p>
 */
class PantryAdjustmentIntegrationTest extends AbstractPantryIntegrationTest {

    private static final String MILK = "Dairy and Egg Products";

    /**
     * The column is DECIMAL(14,6), so the values come back as {@code 100.000000}.
     * Comparing with {@code equals} would make every assertion here a test of the column
     * scale rather than of the ledger, hence a tolerance instead.
     */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    @Test
    void create_writesAnOpeningEntrySoTheQuantityIsAlreadyExplained() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        List<ConsumptionEntryResponse> entries = entries(headers, id);

        // Without this the identity would be false for every line nobody has adjusted
        // yet, and an unrecorded opening balance is indistinguishable from a line that
        // was never touched.
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.deltaQuantity()).isCloseTo(new BigDecimal("1031.288"),
                    within(new BigDecimal("0.000001")));
            assertThat(entry.origin()).isEqualTo(ConsumptionOrigin.MANUAL);
            assertThat(entry.createdAt()).isNotNull();
        });
    }

    @Test
    void adjust_addsStockAndRecordsTheMovement() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> response = adjust(headers, id, "100", "GRAM");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(item -> {
            assertThat(item.quantity()).isCloseTo(new BigDecimal("1131.288"),
                    within(new BigDecimal("0.000001")));
            assertThat(item.unitCode()).isEqualTo("GRAM");
        });
        assertThat(entries(headers, id)).hasSize(2);
        // Oldest first, so the history reads the same way the movements happened.
        assertDeltas(headers, id, "1031.288", "100");
    }

    @Test
    void adjust_subtractsStockAndRecordsANegativeMovement() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> response = adjust(headers, id, "-500", "GRAM");

        assertThat(response.getBody()).isNotNull().satisfies(item ->
                assertThat(item.quantity()).isCloseTo(new BigDecimal("531.288"),
                        within(new BigDecimal("0.000001"))));
        // Signed, not a positive number with the direction stored elsewhere: a movement
        // that says how much and in which direction is one fact, not two.
        assertDeltas(headers, id, "1031.288", "-500");
    }

    @Test
    void adjust_convertsIntoTheUnitTheLineAlreadyStores() {
        HttpHeaders headers = authenticate();
        Ingredient flour = materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        measureRepository.save(measure(flour, "CUP", "122"));
        long id = add(headers, FLOUR_FDC_ID, new BigDecimal("2"), "CUP").getBody().id();

        ResponseEntity<PantryItemResponse> response = adjust(headers, id, "1", "CUP");

        // The line is 244 GRAM and stays in grams; the caller does not have to know which
        // unit it ended up in, because reinterpreting the line under a new unit would
        // change the meaning of the entries already written against it.
        assertThat(response.getBody()).isNotNull().satisfies(item -> {
            assertThat(item.unitCode()).isEqualTo("GRAM");
            assertThat(item.quantity()).isCloseTo(new BigDecimal("366"),
                    within(new BigDecimal("0.000001")));
        });
        assertDeltas(headers, id, "244", "122");
    }

    @Test
    void adjust_aCountLineCountsInUnits() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Eggs, whole", MILK);
        long id = add(headers, MILK_FDC_ID, new BigDecimal("12"), "UNIT").getBody().id();

        ResponseEntity<PantryItemResponse> response = adjust(headers, id, "-2", "UNIT");

        assertThat(response.getBody()).isNotNull().satisfies(item -> {
            assertThat(item.quantity()).isEqualByComparingTo(new BigDecimal("10"));
            assertThat(item.unitCode()).isEqualTo("UNIT");
        });
    }

    @Test
    void adjust_leavingExactlyZeroIsAllowed() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> response = adjust(headers, id, "-1031.288", "GRAM");

        // Running out of an ingredient is a normal thing that happens, not a mistake.
        // What the user must do with the empty line afterwards is their call.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().satisfies(item ->
                assertThat(item.quantity()).isCloseTo(BigDecimal.ZERO, within(new BigDecimal("0.000001"))));
    }

    @Test
    void adjust_belowZero_returns409AndChangesNothing() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + id + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal("-5000"), "GRAM"), headers),
                ErrorResponse.class);

        // 409 and not 400: the request is well formed, the state it lands on does not
        // admit it. And refusing outright is the point: a negative line would let the
        // user start a recipe on milk that nobody has.
        assertError(response, HttpStatus.CONFLICT, PANTRY + "/" + id + "/adjustments");
        // The message carries all three numbers, because "not enough" without them makes
        // the caller recompute the shortfall against a quantity that has since moved.
        // 1031.288 GRAM on the shelf, minus 5000, would leave -3968.712.
        assertThat(response.getBody().message())
                .contains("-5000", "-3968.712", "1031.288");
        assertThat(list(headers).getBody()).singleElement().satisfies(item ->
                assertThat(item.quantity()).isCloseTo(new BigDecimal("1031.288"),
                        within(new BigDecimal("0.000001"))));
        // And no ledger entry, or the history would claim a movement that never happened.
        assertThat(entries(headers, id)).hasSize(1);
    }

    @Test
    void adjust_zero_returns400() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + id + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(BigDecimal.ZERO, "GRAM"), headers),
                ErrorResponse.class);

        // 400 and not 409: a zero is a malformed movement, not a conflict with any state.
        assertError(response, HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/adjustments");
        assertThat(entries(headers, id)).hasSize(1);
    }

    @Test
    void adjust_unknownUnit_returns400() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + id + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal("1"), "SPOON"), headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/adjustments");
    }

    @Test
    void adjust_withoutAnAmount_returns400() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + id + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("unitCode", "GRAM"), headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/adjustments");
    }

    @Test
    void adjust_aVolumeAgainstALineWithNoDensity_returns400() {
        HttpHeaders headers = authenticate();
        // Stored in milliliters precisely because no weight is known for it, so a caller
        // asking to move grams is asking for a conversion nobody can make honestly.
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        long id = add(headers, FLOUR_FDC_ID, new BigDecimal("1"), "L").getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + id + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal("100"), "GRAM"), headers),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/adjustments");
    }

    @Test
    void adjust_anUnknownLine_returns404() {
        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/987654/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal("1"), "GRAM"),
                        authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, PANTRY + "/987654/adjustments");
    }

    @Test
    void adjust_anotherUsersLine_returns404AndLeavesItAlone() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateAsSecondUser();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long theirId = addMilk(theirs, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + theirId + "/adjustments",
                HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal("-1000"), "GRAM"), mine),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, PANTRY + "/" + theirId + "/adjustments");
        assertThat(entries(theirs, theirId)).hasSize(1);
    }

    @Test
    void entries_ofAnotherUsersLine_returns404() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateAsSecondUser();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long theirId = addMilk(theirs, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = rest.exchange(PANTRY + "/" + theirId + "/entries",
                HttpMethod.GET, new HttpEntity<>(mine), ErrorResponse.class);

        // Ownership first, then the entries: the ledger is only reachable through the
        // line, so a caller who cannot read the line cannot read its history either.
        assertError(response, HttpStatus.NOT_FOUND, PANTRY + "/" + theirId + "/entries");
    }

    @Test
    void theSumOfTheEntriesIsAlwaysTheStoredQuantity() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        adjust(headers, id, "250", "GRAM");
        adjust(headers, id, "-100", "GRAM");
        adjust(headers, id, "1", "CUP");

        BigDecimal summed = entries(headers, id).stream()
                .map(ConsumptionEntryResponse::deltaQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // The identity the whole ledger rests on, asserted after a sequence rather than
        // after one movement: a bug that skipped the second entry would still leave any
        // single assertion satisfied.
        assertThat(summed).isEqualByComparingTo(list(headers).getBody().get(0).quantity());
        assertThat(entries(headers, id)).hasSize(4);
    }

    @Test
    void consume_reducesTheLineAndRecordsAManualMovement() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<PantryItemResponse> response = consume(headers, id, "500", "GRAM");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().quantity()).isCloseTo(new BigDecimal("531.288"),
                within(TOLERANCE));
        assertDeltas(headers, id, "1031.288", "-500");
        // Manual, not a session's: the user typed it, and the ledger says so.
        assertThat(entries(headers, id).get(1).origin()).isEqualTo(ConsumptionOrigin.MANUAL);
    }

    @Test
    void consume_moreThanTheLineHolds_returns409AndLeavesStockUntouched() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = consumeError(headers, id, "2000", "GRAM");

        // Same conflict as a negative adjustment: the request is fine, the stock is not.
        assertError(response, HttpStatus.CONFLICT, PANTRY + "/" + id + "/consume");
        assertThat(list(headers).getBody().get(0).quantity())
                .isCloseTo(new BigDecimal("1031.288"), within(TOLERANCE));
        assertThat(entries(headers, id)).hasSize(1);
    }

    @Test
    void consume_zeroOrNegative_returns400() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long id = addMilk(headers, new BigDecimal("1")).getBody().id();

        // Positive-only by bean validation, so consume can never quietly become an add.
        assertError(consumeError(headers, id, "0", "GRAM"),
                HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/consume");
        assertError(consumeError(headers, id, "-250", "GRAM"),
                HttpStatus.BAD_REQUEST, PANTRY + "/" + id + "/consume");
        assertThat(entries(headers, id)).hasSize(1);
    }

    @Test
    void consume_anotherUsersLine_returns404AndLeavesItAlone() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateAsSecondUser();
        materialize(MILK_FDC_ID, "Milk, whole", MILK, "MILK");
        long theirId = addMilk(theirs, new BigDecimal("1")).getBody().id();

        ResponseEntity<ErrorResponse> response = consumeError(mine, theirId, "100", "GRAM");

        assertError(response, HttpStatus.NOT_FOUND, PANTRY + "/" + theirId + "/consume");
        assertThat(entries(theirs, theirId)).hasSize(1);
    }

    private ResponseEntity<PantryItemResponse> adjust(HttpHeaders headers, long id,
                                                       String amount, String unitCode) {
        return rest.exchange(PANTRY + "/" + id + "/adjustments", HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(new BigDecimal(amount), unitCode),
                        headers),
                PantryItemResponse.class);
    }

    private ResponseEntity<PantryItemResponse> consume(HttpHeaders headers, long id,
                                                       String amount, String unitCode) {
        return rest.exchange(PANTRY + "/" + id + "/consume", HttpMethod.POST,
                new HttpEntity<>(new ConsumePantryItemRequest(new BigDecimal(amount), unitCode),
                        headers),
                PantryItemResponse.class);
    }

    private ResponseEntity<ErrorResponse> consumeError(HttpHeaders headers, long id,
                                                       String amount, String unitCode) {
        return rest.exchange(PANTRY + "/" + id + "/consume", HttpMethod.POST,
                new HttpEntity<>(new ConsumePantryItemRequest(new BigDecimal(amount), unitCode),
                        headers),
                ErrorResponse.class);
    }

    private List<ConsumptionEntryResponse> entries(HttpHeaders headers, long id) {
        ResponseEntity<List<ConsumptionEntryResponse>> response = rest.exchange(
                PANTRY + "/" + id + "/entries", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        return response.getBody();
    }

    /**
     * Asserts the whole ledger of a line, in order, comparing amounts with a tolerance.
     *
     * <p>Takes the expected amounts as strings and re-parses them per element rather than
     * passing a list of numbers, because AssertJ compares {@code BigDecimal} with
     * {@code equals} and the stored scale would otherwise decide whether the test passes.
     * Passing one amount per entry also pins the order, which is the part of a history
     * that is easy to get wrong by sorting on the wrong column.</p>
     */
    private void assertDeltas(HttpHeaders headers, long id, String... expected) {
        List<BigDecimal> actual = entries(headers, id).stream()
                .map(ConsumptionEntryResponse::deltaQuantity)
                .toList();

        assertThat(actual).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(actual.get(i))
                    .as("entry %s", i)
                    .isCloseTo(new BigDecimal(expected[i]), within(TOLERANCE));
        }
    }
}