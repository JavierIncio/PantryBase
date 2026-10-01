package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.exception.FdcRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FdcFoodCatalogClientTest {

    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private static final String SEARCH_PATH = "/foods/search";
    private static final String DETAIL_PATH = "/food/171265";
    private static final String BATCH_PATH = "/foods";

    private MockRestServiceServer server;
    private FdcFoodCatalogClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(clientBuilder).bufferContent().build();
        adapter = new FdcFoodCatalogClient(clientBuilder.build(), new FdcProperties(
                "TEST_KEY", BASE_URL,
                List.of("Foundation", "SR Legacy"), 20,
                Duration.ofSeconds(2), Duration.ofSeconds(5),
                Duration.ofHours(24), Duration.ofDays(30)));
    }

    @Test
    void search_mapsWireHitsToDomain() {
        server.expect(requestTo(containsString(SEARCH_PATH)))
                .andExpect(queryParam("api_key", "TEST_KEY"))
                .andRespond(withSuccess("""
                        {"foods": [
                            {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                             "dataType": "SR Legacy"},
                            {"fdcId": 169757, "description": "Milk, nonfat", "dataType": "SR Legacy"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var hits = adapter.search("milk", 10);

        assertThat(hits).extracting("fdcId", "description", "dataType")
                .containsExactly(
                        tuple(171265L, "Milk, whole, 3.25% milkfat", "SR Legacy"),
                        tuple(169757L, "Milk, nonfat", "SR Legacy"));
        server.verify();
    }

    @Test
    void getById_mapsWireNutrientsToDomain() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                         "dataType": "SR Legacy",
                         "foodCategory": {"description": "Dairy and Egg Products"},
                         "foodNutrients": [
                            {"nutrientId": 1008, "value": 61.0, "unitName": "KCAL"},
                            {"nutrientId": 1003, "value": 3.15, "unitName": "G"},
                            {"nutrientId": 1004, "value": 3.25, "unitName": "G"},
                            {"nutrientId": 1005, "value": 4.8, "unitName": "G"}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        Optional<FoodProfile> profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().description()).isEqualTo("Milk, whole, 3.25% milkfat");
        assertThat(profile.get().category()).isEqualTo("Dairy and Egg Products");
        assertThat(profile.get().nutrientsPer100g())
                .extracting(n -> n.energyKcal(), n -> n.proteinG(), n -> n.fatG(), n -> n.carbsG())
                .containsExactly(61.0, 3.15, 3.25, 4.8);
        server.verify();
    }

    @Test
    void getById_404_returnsEmpty() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(adapter.getById(171265)).isEmpty();
        server.verify();
    }

    @Test
    void getById_providerError_throws() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.getById(171265))
                .isInstanceOf(FdcProviderException.class);
        server.verify();
    }

    @Test
    void getById_missingNutrient_keepsNull() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole",
                         "foodNutrients": [{"nutrientId": 1003, "value": 3.15, "unitName": "G"}]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().nutrientsPer100g().carbsG()).isNull();   // 1005 ausente
        server.verify();
    }

    @Test
    void getByIds_mapsMultipleWireFoodsToDomain() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andExpect(queryParam("api_key", "TEST_KEY"))
                .andExpect(queryParam("format", "full"))
                .andRespond(withSuccess("""
                        {"foods": [
                            {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                             "dataType": "SR Legacy",
                             "foodCategory": {"description": "Dairy and Egg Products"},
                             "foodNutrients": [
                                {"nutrientId": 1008, "value": 61.0},
                                {"nutrientId": 1003, "value": 3.15},
                                {"nutrientId": 1004, "value": 3.25},
                                {"nutrientId": 1005, "value": 4.8}]},
                            {"fdcId": 169757, "description": "Milk, nonfat",
                             "dataType": "SR Legacy",
                             "foodCategory": {"description": "Dairy and Egg Products"},
                             "foodNutrients": [
                                {"nutrientId": 1008, "value": 34.0},
                                {"nutrientId": 1003, "value": 3.4},
                                {"nutrientId": 1004, "value": 0.2},
                                {"nutrientId": 1005, "value": 5.0}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Map<Long, FoodProfile> profiles = adapter.getByIds(List.of(171265L, 169757L));

        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(171265L).description()).isEqualTo("Milk, whole, 3.25% milkfat");
        assertThat(profiles.get(169757L).dataType()).isEqualTo("SR Legacy");
        server.verify();
    }

    @Test
    void getByIds_unknownId_omittedFromResult() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withSuccess("""
                        {"foods": [
                            {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                             "dataType": "SR Legacy", "foodNutrients": []}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Map<Long, FoodProfile> profiles = adapter.getByIds(List.of(171265L, 999999L));

        assertThat(profiles).hasSize(1);
        assertThat(profiles).containsOnlyKeys(171265L);
        server.verify();
    }

    @Test
    void getByIds_providerError_throws() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.getByIds(List.of(171265L)))
                .isInstanceOf(FdcProviderException.class);
        server.verify();
    }

    @Test
    void search_providerError_messageDoesNotLeakTheApiKey() {
        server.expect(requestTo(containsString(SEARCH_PATH)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.search("milk", 10))
                .isInstanceOf(FdcProviderException.class)
                // The URI travels inside the exception, which the handler logs, so the
                // key must be redacted while the rest of the call stays diagnosable.
                .hasMessageNotContaining("TEST_KEY")
                .hasMessageContaining("api_key=***")
                .hasMessageContaining("query=milk");
        server.verify();
    }

    @Test
    void getByIds_providerError_messageDoesNotLeakTheApiKey() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> adapter.getByIds(List.of(171265L)))
                .isInstanceOf(FdcProviderException.class)
                .hasMessageNotContaining("TEST_KEY")
                .hasMessageContaining("api_key=***");
        server.verify();
    }

    @Test
    void search_quotaExhausted_raisesARateLimitFailure_withoutTheKey() {
        server.expect(requestTo(containsString(SEARCH_PATH)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adapter.search("milk", 10))
                // A distinct type so the caller answers from a local copy instead of
                // reporting an outage, and never retries: the quota cannot be spent.
                .isInstanceOf(FdcRateLimitException.class)
                .hasMessageNotContaining("TEST_KEY")
                .hasMessageContaining("api_key=***");
        server.verify();
    }

    @Test
    void getById_quotaExhausted_raisesARateLimitFailure() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adapter.getById(171265L))
                .isInstanceOf(FdcRateLimitException.class)
                .hasMessageContaining("quota")
                .hasMessageNotContaining("TEST_KEY");
        server.verify();
    }

    @Test
    void getByIds_quotaExhausted_raisesARateLimitFailure_withoutTheKey() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adapter.getByIds(List.of(171265L)))
                .isInstanceOf(FdcRateLimitException.class)
                .hasMessageNotContaining("TEST_KEY")
                .hasMessageContaining("api_key=***");
        server.verify();
    }

    @Test
    void search_otherClientError_reportsTheStatusWithoutBeingARateLimitFailure() {
        server.expect(requestTo(containsString(SEARCH_PATH)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.search("milk", 10))
                .isInstanceOf(FdcProviderException.class)
                // A 401 is a credentials problem, not an exhausted quota: treating it as
                // a rate limit would silently serve old data instead of surfacing the
                // wrong key.
                .isNotInstanceOf(FdcRateLimitException.class)
                .hasMessageContaining("status=401")
                .hasMessageNotContaining("TEST_KEY");
        server.verify();
    }

    @Test
    void getByIds_sendsSerializedBody_withRequestedFdcIds() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andExpect(content().json("{\"fdcIds\":[171265,169757]}"))
                .andRespond(withSuccess("""
                        {"foods": []}
                        """, MediaType.APPLICATION_JSON));

        adapter.getByIds(List.of(171265L, 169757L));

        server.verify();
    }

    @Test
    void getById_mapsHouseholdPortionsToDomain() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                         "foodPortions": [
                            {"amount": 1, "gramWeight": 244.0,
                             "measureUnit": {"name": "cup, nf"}},
                            {"amount": 1, "gramWeight": 15.0,
                             "measureUnit": {"name": "tbsp"}}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 244.0), tuple("TBSP", 15.0));
        server.verify();
    }

    @Test
    void getById_normalizesPartialPortionByAmount() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole",
                         "foodPortions": [
                            {"amount": 0.5, "gramWeight": 122.0,
                             "measureUnit": {"name": "cup"}}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 244.0));
        server.verify();
    }

    @Test
    void getById_skipsUnmappedOrWeightlessPortions() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole",
                         "foodPortions": [
                            {"amount": 1, "gramWeight": 28.35, "measureUnit": {"name": "oz"}},
                            {"amount": 1, "gramWeight": 1.0, "measureUnit": {"name": "g"}},
                            {"amount": 1, "gramWeight": 0.0, "measureUnit": {"name": "cup"}},
                            {"amount": 1, "gramWeight": 30.0, "measureUnit": null}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().portions()).isEmpty();
        server.verify();
    }

    @Test
    void getById_keepsFirstPortionPerUnit() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole",
                         "foodPortions": [
                            {"amount": 1, "gramWeight": 244.0, "measureUnit": {"name": "cup"}},
                            {"amount": 1, "gramWeight": 122.0,
                             "measureUnit": {"name": "cup, chopped"}}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 244.0));
        server.verify();
    }

    @Test
    void getByIds_mapsPortionsInBatch() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withSuccess("""
                        {"foods": [
                            {"fdcId": 171265, "description": "Milk, whole",
                             "foodPortions": [
                                {"amount": 1, "gramWeight": 244.0,
                                 "measureUnit": {"name": "cup"}}
                             ]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var profiles = adapter.getByIds(List.of(171265L));

        assertThat(profiles.get(171265L).portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 244.0));
        server.verify();
    }
}