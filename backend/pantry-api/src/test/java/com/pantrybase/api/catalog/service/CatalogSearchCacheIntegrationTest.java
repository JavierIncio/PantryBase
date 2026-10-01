package com.pantrybase.api.catalog.service;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.catalog.StubFoodCatalogPort;
import com.pantrybase.api.catalog.StubFoodCatalogPortConfig;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.dto.IngredientSearchResponse;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the Redis-backed search cache, including what it stores and how it fails.
 *
 * <p>Separate from {@code IngredientCatalogIntegrationTest} because the contract here
 * is about key construction and Redis fault tolerance rather than about HTTP status
 * codes, and a failure in either should point at a different place.</p>
 */
@Import(StubFoodCatalogPortConfig.class)
class CatalogSearchCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "catalog:fdc:search:";

    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    StubFoodCatalogPort stubPort;
    @Autowired
    IngredientCatalogService service;
    @Autowired
    CatalogSearchCache cache;

    @BeforeEach
    void setUp() {
        stubPort.resetCounters();
        stubPort.returns(List.of(new FoodSearchHit(171265L, "Milk, whole", "SR Legacy")));
    }

    private Set<String> cacheKeys() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys == null ? Set.of() : keys;
    }

    @Test
    void firstSearch_writesOneKeyToRedis() {
        service.search("milk");

        assertThat(cacheKeys()).hasSize(1);
    }

    @Test
    void cachedHits_surviveAFreshCacheRead() {
        cache.put("milk", 20, List.of(new FoodSearchHit(1L, "One", "Foundation"),
                new FoodSearchHit(2L, "Two", "SR Legacy")));

        assertThat(cache.get("milk", 20)).contains(List.of(
                new FoodSearchHit(1L, "One", "Foundation"),
                new FoodSearchHit(2L, "Two", "SR Legacy")));
    }

    @Test
    void emptyResult_isCachedRatherThanTreatedAsAMiss() {
        // Caching an empty list is what stops a query with no matches from hitting the
        // provider on every keystroke; a miss on empty would defeat the point.
        cache.put("zzz", 20, List.of());

        Optional<List<FoodSearchHit>> cached = cache.get("zzz", 20);
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEmpty();
    }

    @Test
    void key_doesNotContainTheSearchTerms() {
        // The query is user input and, in a multi-user deployment, personal data: it is
        // hashed so neither can end up as a readable Redis key.
        cache.put("cheddar", 20, List.of(new FoodSearchHit(1L, "One", "Foundation")));

        assertThat(cacheKeys()).allSatisfy(key ->
                assertThat(key).doesNotContain("cheddar").startsWith(KEY_PREFIX));
    }

    @Test
    void differentQueries_andDifferentLimits_doNotCollide() {
        cache.put("milk", 20, List.of(new FoodSearchHit(1L, "One", "Foundation")));
        cache.put("yogurt", 20, List.of(new FoodSearchHit(2L, "Two", "Foundation")));
        cache.put("milk", 5, List.of(new FoodSearchHit(3L, "Three", "Foundation")));

        assertThat(cacheKeys()).hasSize(3);
        assertThat(cache.get("milk", 20)).contains(List.of(
                new FoodSearchHit(1L, "One", "Foundation")));
        assertThat(cache.get("milk", 5)).contains(List.of(
                new FoodSearchHit(3L, "Three", "Foundation")));
    }

    @Test
    void query_isNormalizedSoCasingAndPaddingHitTheSameEntry() {
        cache.put("  Milk  ", 20, List.of(new FoodSearchHit(1L, "One", "Foundation")));

        assertThat(cache.get("milk", 20)).isPresent();
        assertThat(cache.get("MILK", 20)).isPresent();
    }

    @Test
    void unreadablePayload_degradesToAMissInsteadOfFailing() {
        cache.put("milk", 20, List.of(new FoodSearchHit(1L, "One", "Foundation")));
        redisTemplate.opsForValue().set(cacheKeys().iterator().next(), "not-json");

        // A corrupt entry must not become a 500: the provider can still answer, which is
        // exactly what happened before this cache existed.
        assertThat(cache.get("milk", 20)).isEmpty();
    }

    @Test
    void providerCallAfterACorruptEntry_stillAnswers() {
        cache.put("milk", 20, List.of(new FoodSearchHit(1L, "One", "Foundation")));
        redisTemplate.opsForValue().set(cacheKeys().iterator().next(), "not-json");

        List<IngredientSearchResponse> response = service.search("milk");

        assertThat(response).hasSize(1);
        assertThat(stubPort.searchRequests()).containsExactly("milk");
    }

    @Test
    void service_reusesTheCacheAcrossRepeatedQueries() {
        service.search("milk");
        service.search("milk");
        service.search("milk");

        assertThat(stubPort.searchRequests()).containsExactly("milk");
    }

    @Test
    void service_providerFailureWithNoCachedCopy_stillThrows() {
        stubPort.fails();

        assertThatThrownBy(() -> service.search("milk"))
                .isInstanceOf(FdcProviderException.class);
    }

    @Test
    void service_cachedQuerySurvivesAProviderOutage() {
        service.search("milk");
        stubPort.fails();

        List<IngredientSearchResponse> response = service.search("milk");

        assertThat(response).hasSize(1);
    }
}
