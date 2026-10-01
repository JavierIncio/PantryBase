package com.pantrybase.api.catalog;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.dto.IngredientDetailResponse;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.exception.FdcProviderException;
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
    private static final long FDC_ID = 171265;

    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    IngredientCatalogService ingredientService;
    @Autowired
    StubFoodCatalogPort stubPort;

    @BeforeEach
    void cleanIngredients() {
        ingredientRepository.deleteAll();
        stubPort.fails();
    }

    private static FoodProfile profile() {
        return profile(FDC_ID);
    }

    private static FoodProfile profile(long fdcId) {
        return new FoodProfile(fdcId, "Milk, whole, 3.25% milkfat", "SR Legacy",
                "Dairy and Egg Products", new NutrientProfile(61.0, 3.15, 3.25, 4.8));
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
    void batch_twentyFiveIds_splitsIntoCallsOfTwenty() {
        List<Long> ids = IntStream.rangeClosed(1, 25).asLongStream().boxed().toList();
        stubPort.returns(ids.stream()
                .collect(Collectors.toMap(Function.identity(), IngredientCatalogIntegrationTest::profile)));

        Map<Long, IngredientDetailResponse> result = ingredientService.getByIds(ids);

        assertThat(result).hasSize(25);
        assertThat(stubPort.batchRequests()).containsExactly(
                IntStream.rangeClosed(1, 20).asLongStream().boxed().toList(),
                IntStream.rangeClosed(21, 25).asLongStream().boxed().toList());
        assertThat(ingredientRepository.count()).isEqualTo(25);
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
}