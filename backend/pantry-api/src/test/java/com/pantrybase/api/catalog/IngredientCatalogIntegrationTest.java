package com.pantrybase.api.catalog;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.Ingredient;
import com.pantrybase.api.catalog.domain.IngredientMeasure;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.domain.Portion;
import com.pantrybase.api.catalog.dto.ConvertUnitsRequest;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.dto.UnitConversionResponse;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.repository.IngredientMeasureRepository;
import com.pantrybase.api.catalog.repository.IngredientRepository;
import com.pantrybase.api.catalog.service.IngredientCatalogService;
import com.pantrybase.api.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(StubFoodCatalogPortConfig.class)
public class IngredientCatalogIntegrationTest extends AbstractIntegrationTest {

    public static final String INGREDIENTS = "/api/catalog/ingredients";
    private static final String UNITS_CONVERT = "/api/units/convert";
    private static final long FDC_ID = 171265;

    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    IngredientMeasureRepository measureRepository;
    @Autowired
    IngredientCatalogService ingredientService;
    @Autowired
    StubFoodCatalogPort stubPort;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIngredients() {
        measureRepository.deleteAll();
        ingredientRepository.deleteAll();
        // The stub is a singleton shared by every test in the class, so its call counters
        // would otherwise accumulate and a "called once" assertion would be measuring the
        // whole class rather than the test.
        stubPort.resetCounters();
        stubPort.fails();
    }

    /**
     * Backdates the freshness stamp so a test can reach the provider path on demand.
     *
     * <p>Done in SQL rather than by waiting: the cache window is 24 h, and a test that
     * slept to reach a branch would be slow and still time-dependent. It also makes
     * explicit that {@code synced_at} is the single input to the freshness decision.</p>
     */
    private void ageSyncedAt(long fdcId, Duration age) {
        // Seconds as a number rather than a Duration: the PostgreSQL driver cannot infer
        // a parameter type from java.time.Duration, and interval arithmetic needs the unit.
        jdbcTemplate.update(
                "UPDATE ingredients SET synced_at = now() - (? * INTERVAL '1 second') WHERE fdc_id = ?",
                age.toSeconds(), fdcId);
    }

    private static FoodProfile profile() {
        return profile(FDC_ID);
    }

    private static FoodProfile profile(long fdcId) {
        return new FoodProfile(fdcId, "Milk, whole, 3.25% milkfat", "SR Legacy",
                "Dairy and Egg Products", new NutrientProfile(61.0, 3.15, 3.25, 4.8),
                List.of(new Portion("CUP", 244.0)));
    }

    private static FoodProfile profileWithoutPortions(long fdcId) {
        return new FoodProfile(fdcId, "Milk, whole, 3.25% milkfat", "SR Legacy",
                "Dairy and Egg Products", new NutrientProfile(61.0, 3.15, 3.25, 4.8),
                List.of());
    }

    @Test
    void search_blankQuery_shouldReturn400() {
        HttpHeaders headers = authenticate();

        ResponseEntity<ErrorResponse> response =
                rest.exchange(INGREDIENTS, HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, INGREDIENTS);
    }

    @Test
    void search_returnsMappedHits() {
        stubPort.returns(List.of(new FoodSearchHit(FDC_ID, "Milk, whole, 3.25% milkfat", "SR Legacy")));

        ResponseEntity<IngredientSearchResponse[]> response = rest.exchange(
                INGREDIENTS + "?query=milk", HttpMethod.GET, new HttpEntity<>(authenticate()),
                IngredientSearchResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0])
                .satisfies(h -> {
                    assertThat(h.fdcId()).isEqualTo(FDC_ID);
                    assertThat(h.name()).isEqualTo("Milk, whole, 3.25% milkfat");
                    assertThat(h.dataType()).isEqualTo("SR Legacy");
                });
    }

    @Test
    void detail_materializesAndReturnsInternalId() {
        stubPort.returns(Optional.of(profile()));

        ResponseEntity<IngredientDetailResponse> response = rest.exchange(
                INGREDIENTS + "/" + FDC_ID, HttpMethod.GET, new HttpEntity<>(authenticate()),
                IngredientDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).satisfies(d -> {
            assertThat(d.id()).isPositive();
            assertThat(d.fdcId()).isEqualTo(FDC_ID);
            assertThat(d.name()).isEqualTo("Milk, whole, 3.25% milkfat");
            assertThat(d.nutrients().energyKcal()).isEqualTo(61.0);
        });
        assertThat(ingredientRepository.findByFdcId(FDC_ID)).isPresent();
        assertThat(ingredientRepository.count()).isEqualTo(1);
    }

    @Test
    void detail_unknownFdcId_returns404WithoutRow() {
        stubPort.returns(Optional.empty());

        ResponseEntity<ErrorResponse> response = rest.exchange(
                INGREDIENTS + "/" + FDC_ID, HttpMethod.GET, new HttpEntity<>(authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, INGREDIENTS + "/" + FDC_ID);
        assertThat(ingredientRepository.count()).isZero();
    }

    @Test
    void detail_providerFailure_returns502WithoutRow() {
        stubPort.fails();

        ResponseEntity<ErrorResponse> response = rest.exchange(
                INGREDIENTS + "/" + FDC_ID, HttpMethod.GET, new HttpEntity<>(authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_GATEWAY, INGREDIENTS + "/" + FDC_ID);
        assertThat(ingredientRepository.count()).isZero();
    }

    @Test
    void detail_concurrentCalls_materializesSingleRow() throws Exception {
        stubPort.returns(Optional.of(profile()));

        int n = 2;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<IngredientDetailResponse>> futures = IntStream.range(0, n)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return ingredientService.getById(FDC_ID);
                    })).toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<IngredientDetailResponse> results = new ArrayList<>();
            for (Future<IngredientDetailResponse> f : futures) {
                results.add(f.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).extracting(IngredientDetailResponse::id)
                    .containsOnly(results.get(0).id());
            assertThat(ingredientRepository.findByFdcId(FDC_ID)).isPresent();
            assertThat(ingredientRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void detail_repeatedLookups_insideTheFreshnessWindow_callTheProviderOnce() {
        stubPort.returns(Optional.of(profile()));

        ingredientService.getById(FDC_ID);
        ingredientService.getById(FDC_ID);
        ingredientService.getById(FDC_ID);

        // The whole point of the freshness stamp: three reads, one unit of quota.
        assertThat(stubPort.detailRequests()).containsExactly(FDC_ID);
    }

    @Test
    void detail_freshLocalCopy_isServedWithoutReachingTheProvider() {
        stubPort.returns(Optional.of(profile()));
        IngredientDetailResponse first = ingredientService.getById(FDC_ID);

        // The provider now knows nothing; a hit here would mean the local copy was
        // bypassed, which is what would make a repeated read cost quota again.
        stubPort.fails();
        IngredientDetailResponse second = ingredientService.getById(FDC_ID);

        assertThat(second).isEqualTo(first);
        assertThat(stubPort.detailRequests()).containsExactly(FDC_ID);
    }

    @Test
    void detail_staleLocalCopy_isRefreshedFromTheProvider() {
        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);
        ageSyncedAt(FDC_ID, Duration.ofHours(25));

        stubPort.returns(Optional.of(new FoodProfile(FDC_ID, "Milk, whole, 3.25% milkfat",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(62.5, 3.15, 3.25, 4.8),
                List.of())));
        IngredientDetailResponse refreshed = ingredientService.getById(FDC_ID);

        assertThat(refreshed.nutrients().energyKcal()).isEqualTo(62.5);
        assertThat(stubPort.detailRequests()).containsExactly(FDC_ID, FDC_ID);
    }

    @Test
    void detail_providerFailure_insideTheStaleWindow_servesTheLocalCopy() {
        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);
        ageSyncedAt(FDC_ID, Duration.ofHours(25));

        stubPort.fails();
        IngredientDetailResponse stale = ingredientService.getById(FDC_ID);

        assertThat(stale.fdcId()).isEqualTo(FDC_ID);
        assertThat(stale.nutrients().energyKcal()).isEqualTo(61.0);
    }

    @Test
    void detail_providerFailure_pastTheStaleWindow_returns502() {
        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);
        ageSyncedAt(FDC_ID, Duration.ofDays(31));

        stubPort.fails();
        ResponseEntity<ErrorResponse> response = rest.exchange(
                INGREDIENTS + "/" + FDC_ID, HttpMethod.GET, new HttpEntity<>(authenticate()),
                ErrorResponse.class);

        // A bounded amount of old data is useful; an unbounded amount is not, so
        // beyond max-stale the failure is reported instead of hidden.
        assertError(response, HttpStatus.BAD_GATEWAY, INGREDIENTS + "/" + FDC_ID);
    }

    @Test
    void detail_quotaExhausted_insideTheStaleWindow_servesTheLocalCopyWithoutRetrying() {
        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);
        ageSyncedAt(FDC_ID, Duration.ofHours(25));

        stubPort.quotaExhausted();
        IngredientDetailResponse stale = ingredientService.getById(FDC_ID);

        assertThat(stale.nutrients().energyKcal()).isEqualTo(61.0);
        // A 429 must not be retried: the same call cannot succeed until the quota
        // window resets, so a second attempt would only spend quota that is not there.
        assertThat(stubPort.detailRequests()).containsExactly(FDC_ID, FDC_ID);
    }

    @Test
    void detail_quotaExhausted_pastTheStaleWindow_returns502() {
        stubPort.quotaExhausted();

        ResponseEntity<ErrorResponse> response = rest.exchange(
                INGREDIENTS + "/" + FDC_ID, HttpMethod.GET, new HttpEntity<>(authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_GATEWAY, INGREDIENTS + "/" + FDC_ID);
    }

    @Test
    void detail_refresh_keepsTheCuratedDensityClassAndExistingMeasure() {
        stubPort.returns(Optional.of(profile()));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        assignDensityClass(ingredientId, "FLOUR");
        ageSyncedAt(FDC_ID, Duration.ofHours(25));

        stubPort.returns(Optional.of(new FoodProfile(FDC_ID, "Milk, whole, 3.25% milkfat",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(62.5, 3.15, 3.25, 4.8),
                List.of(new Portion("CUP", 200.0)))));
        ingredientService.getById(FDC_ID);

        Ingredient refreshed = ingredientRepository.findByFdcId(FDC_ID).orElseThrow();
        // The density class is a curated claim of ours and the measure is a stored fact:
        // a provider refresh overwrites neither, or a classification nobody recorded
        // the source of would vanish on the next read.
        assertThat(refreshed.getDensityClass()).isEqualTo("FLOUR");
        assertThat(measureRepository.findByIngredientIdAndUnitCode(refreshed.getId(), "CUP")
                .orElseThrow().getGramPerUnit()).isEqualByComparingTo(new BigDecimal("244"));
        // Provider-owned columns do move, which is what the insert-only version could
        // never do: a corrected nutrient value now reaches the local catalog.
        assertThat(refreshed.getEnergyKcal()).isEqualTo(62.5);
        assertThat(refreshed.getDataType()).isEqualTo("SR Legacy");
    }

    @Test
    void detail_refresh_advancesTheFreshnessStamp() {
        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);
        ageSyncedAt(FDC_ID, Duration.ofHours(25));
        Instant before = ingredientRepository.findByFdcId(FDC_ID).orElseThrow().getSyncedAt();

        stubPort.returns(Optional.of(profile()));
        ingredientService.getById(FDC_ID);

        Instant after = ingredientRepository.findByFdcId(FDC_ID).orElseThrow().getSyncedAt();
        assertThat(after).isAfter(before);
    }

    @Test
    void search_repeatedQuery_isServedFromTheCache() {
        stubPort.returns(List.of(new FoodSearchHit(FDC_ID, "Milk, whole, 3.25% milkfat", "SR Legacy")));

        List<IngredientSearchResponse> first = ingredientService.search("milk");
        List<IngredientSearchResponse> second = ingredientService.search("milk");

        assertThat(second).isEqualTo(first);
        // Search results are never materialized, so Redis is the only possible copy:
        // without it every keystroke in the search box would spend quota.
        assertThat(stubPort.searchRequests()).containsExactly("milk");
    }

    @Test
    void search_differentQueries_hitTheProviderSeparately() {
        stubPort.returns(List.of(new FoodSearchHit(FDC_ID, "Milk, whole, 3.25% milkfat", "SR Legacy")));

        ingredientService.search("milk");
        ingredientService.search("yogurt");

        assertThat(stubPort.searchRequests()).containsExactly("milk", "yogurt");
    }

    @Test
    void search_providerFailure_stillReturns502() {
        stubPort.fails();

        assertThatThrownBy(() -> ingredientService.search("milk"))
                .isInstanceOf(FdcProviderException.class);
    }

    @Test
    void unauthorized_returns401ForBothEndpoints() {
        assertUnauthorized(INGREDIENTS, HttpMethod.GET);
        assertUnauthorized(INGREDIENTS + "/" + FDC_ID, HttpMethod.GET);
    }

    @Test
    void batch_materializesAllKnownIds() {
        stubPort.returns(Map.of(
                171265L, profile(171265L),
                169757L, profile(169757L)));

        Map<Long, IngredientDetailResponse> result =
                ingredientService.getByIds(List.of(171265L, 169757L));

        assertThat(result).hasSize(2).containsOnlyKeys(171265L, 169757L);
        assertThat(result.get(171265L).id()).isPositive();
        assertThat(result.get(169757L).name()).isEqualTo("Milk, whole, 3.25% milkfat");
        assertThat(ingredientRepository.count()).isEqualTo(2);
    }

    @Test
    void batch_unknownId_omittedWithoutRow() {
        stubPort.returns(Map.of(171265L, profile(171265L)));

        Map<Long, IngredientDetailResponse> result =
                ingredientService.getByIds(List.of(171265L, 999999L));

        assertThat(result).hasSize(1).containsOnlyKeys(171265L);
        assertThat(ingredientRepository.count()).isEqualTo(1);
    }

    @Test
    void batch_oneHundredAndOneIds_splitsAtTheBatchLimit() {
        // The batch call is a GET whose ids travel in the query string, so the limit is a
        // URI-length guard rather than a documented provider cap (200 ids were accepted
        // against the live API). The test asserts the split exists and lands on the limit,
        // not the number 20 the old, unverified assumption had baked in.
        List<Long> ids = IntStream.rangeClosed(1, 101).asLongStream().boxed().toList();
        stubPort.returns(ids.stream()
                .collect(Collectors.toMap(Function.identity(), IngredientCatalogIntegrationTest::profile)));

        Map<Long, IngredientDetailResponse> result = ingredientService.getByIds(ids);

        assertThat(result).hasSize(101);
        assertThat(stubPort.batchRequests()).containsExactly(
                IntStream.rangeClosed(1, 100).asLongStream().boxed().toList(),
                List.of(101L));
        assertThat(ingredientRepository.count()).isEqualTo(101);
    }

    @Test
    void batch_emptyIds_throwsIllegalArgumentWithoutRow() {
        assertThatThrownBy(() -> ingredientService.getByIds(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ingredientRepository.count()).isZero();
    }

    @Test
    void batch_providerFailure_throwsWithoutRows() {
        stubPort.fails();

        assertThatThrownBy(() -> ingredientService.getByIds(List.of(171265L)))
                .isInstanceOf(FdcProviderException.class);
        assertThat(ingredientRepository.count()).isZero();
    }

    @Test
    void detail_persistsPortionMeasures() {
        stubPort.returns(Optional.of(profile()));

        ingredientService.getById(FDC_ID);

        Long ingredientId = ingredientRepository.findByFdcId(FDC_ID).orElseThrow().getId();
        IngredientMeasure measure = measureRepository
                .findByIngredientIdAndUnitCode(ingredientId, "CUP").orElseThrow();
        assertThat(measure.getGramPerUnit()).isEqualByComparingTo(new BigDecimal("244"));
    }

    @Test
    void detail_repeatedLookups_keepTheFirstMeasure() {
        stubPort.returns(Optional.of(profile()));

        ingredientService.getById(FDC_ID);
        // Past the freshness window, so the second read really does reach the provider:
        // otherwise the local short-circuit would answer it and the test would pass
        // without ever exercising the insert-only behaviour it is about.
        ageSyncedAt(FDC_ID, Duration.ofHours(25));
        stubPort.returns(Optional.of(new FoodProfile(FDC_ID, "Milk, whole, 3.25% milkfat",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(61.0, 3.15, 3.25, 4.8),
                List.of(new Portion("CUP", 200.0)))));
        ingredientService.getById(FDC_ID);

        Long ingredientId = ingredientRepository.findByFdcId(FDC_ID).orElseThrow().getId();
        assertThat(measureRepository.findByIngredientId(ingredientId)).hasSize(1);
        IngredientMeasure measure = measureRepository
                .findByIngredientIdAndUnitCode(ingredientId, "CUP").orElseThrow();
        assertThat(measure.getGramPerUnit()).isEqualByComparingTo(new BigDecimal("244"));
    }

    /** Curated density classes are seeded by migration, so assigning one only needs the ingredient. */
    private void assignDensityClass(Long ingredientId, String densityClass) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId).orElseThrow();
        ingredient.setDensityClass(densityClass);
        ingredientRepository.saveAndFlush(ingredient);
    }

    @Test
    void convert_withIngredientId_usesTheCapturedMeasure() {
        stubPort.returns(Optional.of(profile()));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, "FLOUR");

        ResponseEntity<UnitConversionResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).satisfies(r -> {
            assertThat(r.unitCode()).isEqualTo("GRAM");
            assertThat(r.amount()).isEqualByComparingTo(new BigDecimal("488"));
        });
    }

    @Test
    void convert_ingredientWithoutMeasure_fallsBackToGivenClass() {
        stubPort.returns(Optional.of(profileWithoutPortions(FDC_ID)));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, "FLOUR");

        ResponseEntity<UnitConversionResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().amount())
                .isEqualByComparingTo(new BigDecimal("250.78328"));
    }

    @Test
    void convert_ingredientWithACuratedClass_needsNoHint() {
        stubPort.returns(Optional.of(profileWithoutPortions(FDC_ID)));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        assignDensityClass(ingredientId, "FLOUR");
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, null);

        ResponseEntity<UnitConversionResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().amount())
                .isEqualByComparingTo(new BigDecimal("250.78328"));
    }

    @Test
    void curatedClass_survivesAProviderLookup() {
        stubPort.returns(Optional.of(profileWithoutPortions(FDC_ID)));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        assignDensityClass(ingredientId, "FLOUR");

        // Materialization must not touch curated data: it only ever inserts. The first
        // lookup is inside the freshness window and is answered locally; the second is
        // backdated so it reaches the provider, which is where the guarantee applies.
        ageSyncedAt(FDC_ID, Duration.ofHours(25));
        stubPort.returns(Optional.of(new FoodProfile(FDC_ID, "Milk, whole, 3.25% milkfat",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(61.0, 3.15, 3.25, 4.8),
                List.of(new Portion("CUP", 240.0)))));
        ingredientService.getById(FDC_ID);

        assertThat(ingredientRepository.findById(ingredientId).orElseThrow().getDensityClass())
                .isEqualTo("FLOUR");
        // The newly captured measure is now the preferred path, and it agrees closely
        // with the curated class: 240 g per cup against 0.53 g/ml.
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, null);
        ResponseEntity<UnitConversionResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                UnitConversionResponse.class);

        assertThat(response.getBody().amount()).isEqualByComparingTo(new BigDecimal("480"));
    }

    @Test
    void convert_ingredientWithoutMeasureNorClass_returns400() {
        stubPort.returns(Optional.of(profileWithoutPortions(FDC_ID)));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, null);

        ResponseEntity<ErrorResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_providerCategoryAloneIsNotADensity() {
        // "Dairy and Egg Products" is a real FDC category, and the ingredient carries it,
        // yet the conversion must still fail: the category mixes milk at ~1.03 g/ml with
        // cheddar at ~0.40 g/ml, so no density can be derived from it.
        stubPort.returns(Optional.of(profileWithoutPortions(FDC_ID)));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        assertThat(ingredientRepository.findById(ingredientId).orElseThrow().getCategory())
                .isEqualTo("Dairy and Egg Products");

        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, null);

        ResponseEntity<ErrorResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.BAD_REQUEST, UNITS_CONVERT);
    }

    @Test
    void convert_ingredientWithOnlyATablespoonMeasure_stillConvertsCups() {
        stubPort.returns(Optional.of(new FoodProfile(FDC_ID, "Milk, whole, 3.25% milkfat",
                "SR Legacy", "Dairy and Egg Products",
                new NutrientProfile(61.0, 3.15, 3.25, 4.8),
                List.of(new Portion("TBSP", 15.2)))));
        Long ingredientId = ingredientService.getById(FDC_ID).id();
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", ingredientId, null);

        ResponseEntity<UnitConversionResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                UnitConversionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // A tablespoon of 15.2 g makes two cups weigh about 486.4 g.
        assertThat(response.getBody().amount()).isBetween(new BigDecimal("486.3"),
                new BigDecimal("486.5"));
    }

    @Test
    void convert_unknownIngredientId_returns404() {
        ConvertUnitsRequest body =
                new ConvertUnitsRequest(new BigDecimal("2"), "CUP", "GRAM", 999_999L, "FLOUR");

        ResponseEntity<ErrorResponse> response = rest.exchange(
                UNITS_CONVERT, HttpMethod.POST, new HttpEntity<>(body, authenticate()),
                ErrorResponse.class);

        assertError(response, HttpStatus.NOT_FOUND, UNITS_CONVERT);
    }
}