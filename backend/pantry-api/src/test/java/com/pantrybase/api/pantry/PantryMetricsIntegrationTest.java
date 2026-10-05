package com.pantrybase.api.pantry;

import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.pantry.dto.AdjustPantryItemRequest;
import com.pantrybase.api.pantry.dto.CategoryShareResponse;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.pantry.dto.PantrySummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the user has, counted.
 *
 * <p>Its own suite because counting is its own question: a wrong count is invisible in the
 * inventory view, which draws the lines themselves, so nothing else would notice it.</p>
 *
 * <p>Every case that builds a pantry does it through the endpoints rather than through the
 * repositories, so the quantities counted are the ones the write path really stored — the
 * unit they land in is decided by the conversion, not by the fixture.</p>
 */
class PantryMetricsIntegrationTest extends AbstractPantryIntegrationTest {

    private static final long EGGS_FDC_ID = 171287;
    private static final long CREAM_FDC_ID = 171264;

    @Test
    void summary_emptyPantryIsAllZero() {
        ResponseEntity<PantrySummaryResponse> response = summary(authenticate());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Zeros rather than nulls or an error: a user with nothing in the pantry is the
        // normal state of a new account, not a failure to report.
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.totalItems()).isZero();
            assertThat(body.zeroItems()).isZero();
            assertThat(body.byCategory()).isEmpty();
            assertThat(body.byUnitType()).isEmpty();
        });
    }

    @Test
    void summary_countsLinesPerCategory() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        addMilk(headers, new BigDecimal("1"));
        add(headers, FLOUR_FDC_ID, new BigDecimal("500"), "GRAM");

        PantrySummaryResponse body = summary(headers).getBody();

        assertThat(body).isNotNull();
        assertThat(body.totalItems()).isEqualTo(2);
        assertThat(body.byCategory()).containsExactly(
                new CategoryShareResponse("Dairy and Egg Products", 1),
                new CategoryShareResponse("Grains", 1));
    }

    @Test
    void summary_countsAZeroedLineInTheTotalAndSaysHowManyAreAtZero() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products", "MILK");
        PantryItemResponse milk = addMilk(headers, new BigDecimal("1")).getBody();
        // Consumed to exactly zero, which the schema allows on purpose: running out is an
        // ordinary event and the line has to be able to say so.
        adjust(headers, milk.id(), milk.quantity().negate(), milk.unitCode());

        PantrySummaryResponse body = summary(headers).getBody();

        assertThat(body).isNotNull().satisfies(summary -> {
            assertThat(summary.totalItems()).isEqualTo(1);
            assertThat(summary.zeroItems()).isEqualTo(1);
        });
    }

    @Test
    void summary_splitsLinesByKindOfStorageUnit() {
        HttpHeaders headers = authenticate();
        // Milk has the MILK density class, so a liter resolves to grams: a weight line.
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products", "MILK");
        // Flour has none, so the same liter would stay in milliliters: a volume line.
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        materialize(EGGS_FDC_ID, "Egg, whole, raw", "Eggs");
        addMilk(headers, new BigDecimal("1"));
        add(headers, FLOUR_FDC_ID, new BigDecimal("500"), "ML");
        add(headers, EGGS_FDC_ID, new BigDecimal("3"), "UNIT");

        PantrySummaryResponse body = summary(headers).getBody();

        assertThat(body).isNotNull();
        // Sorted by the enum's own order (WEIGHT, VOLUME, COUNT), which is the reading
        // order of the distinction the split exists to draw.
        assertThat(body.byUnitType()).extracting("category")
                .containsExactly(UnitCategory.WEIGHT, UnitCategory.VOLUME, UnitCategory.COUNT);
        assertThat(body.byUnitType()).extracting("items").containsExactly(1L, 1L, 1L);
    }

    @Test
    void summary_namesAnIngredientThatArrivedWithoutACategory() {
        HttpHeaders headers = authenticate();
        // The column is nullable and the provider owns it, so a food with no category is a
        // case the schema allows rather than a mistake.
        materialize(EGGS_FDC_ID, "Egg, whole, raw", null);
        add(headers, EGGS_FDC_ID, new BigDecimal("3"), "UNIT");

        PantrySummaryResponse body = summary(headers).getBody();

        assertThat(body).isNotNull().satisfies(summary -> {
            assertThat(summary.totalItems()).isEqualTo(1);
            assertThat(summary.byCategory())
                    .containsExactly(new CategoryShareResponse("Sin categoría", 1));
        });
    }

    @Test
    void summary_ordersCategoriesBySizeFirst() {
        HttpHeaders headers = authenticate();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        materialize(EGGS_FDC_ID, "Egg, whole, raw", "Eggs");
        // A second dairy ingredient, so the largest category is not also the alphabetically
        // first one and the two orderings cannot be mistaken for each other.
        materialize(CREAM_FDC_ID, "Cream, fluid, half and half", "Dairy and Egg Products");
        add(headers, EGGS_FDC_ID, new BigDecimal("3"), "UNIT");
        add(headers, MILK_FDC_ID, new BigDecimal("1"), "ML");
        add(headers, FLOUR_FDC_ID, new BigDecimal("500"), "ML");
        add(headers, CREAM_FDC_ID, new BigDecimal("200"), "ML");

        List<CategoryShareResponse> shares = summary(headers).getBody().byCategory();

        // Whole records rather than two separate extractions, so the assertion fails if the
        // count and the name ever come back paired wrongly.
        assertThat(shares).containsExactly(
                new CategoryShareResponse("Dairy and Egg Products", 2),
                new CategoryShareResponse("Eggs", 1),
                new CategoryShareResponse("Grains", 1));
    }

    @Test
    void summary_ordersEqualCategoriesByName() {
        HttpHeaders headers = authenticate();
        materialize(FLOUR_FDC_ID, "Wheat flour, white, all-purpose", "Grains");
        materialize(EGGS_FDC_ID, "Egg, whole, raw", "Eggs");
        // Added in the opposite order to the answer, so a tie broken by insertion would
        // fail here rather than pass by accident.
        add(headers, FLOUR_FDC_ID, new BigDecimal("500"), "ML");
        add(headers, EGGS_FDC_ID, new BigDecimal("3"), "UNIT");

        List<CategoryShareResponse> shares = summary(headers).getBody().byCategory();

        assertThat(shares).extracting("category").containsExactly("Eggs", "Grains");
    }

    @Test
    void summary_countsOnlyTheOwnLines() {
        HttpHeaders mine = authenticate();
        HttpHeaders theirs = authenticateAsSecondUser();
        materialize(MILK_FDC_ID, "Milk, whole", "Dairy and Egg Products");
        add(mine, MILK_FDC_ID, new BigDecimal("1"), "ML");
        add(theirs, MILK_FDC_ID, new BigDecimal("9"), "ML");

        PantrySummaryResponse body = summary(mine).getBody();

        // Scoped in the query like every other read of a pantry: a summary that aggregated
        // before filtering would leak the shape of another inventory, and a 1 versus 9
        // difference here is the assertion that notices.
        assertThat(body).isNotNull().satisfies(summary -> {
            assertThat(summary.totalItems()).isEqualTo(1);
            assertThat(summary.byCategory())
                    .containsExactly(new CategoryShareResponse("Dairy and Egg Products", 1));
        });
    }

    @Test
    void summary_requiresAuthentication() {
        assertUnauthorized(PANTRY + "/summary", HttpMethod.GET);
    }

    private ResponseEntity<PantrySummaryResponse> summary(HttpHeaders headers) {
        return rest.exchange(PANTRY + "/summary", HttpMethod.GET, new HttpEntity<>(headers),
                PantrySummaryResponse.class);
    }

    private void adjust(HttpHeaders headers, long itemId, BigDecimal amount, String unitCode) {
        rest.exchange(PANTRY + "/" + itemId + "/adjustments", HttpMethod.POST,
                new HttpEntity<>(new AdjustPantryItemRequest(amount, unitCode), headers),
                PantryItemResponse.class);
    }
}