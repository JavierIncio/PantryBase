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

import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FdcFoodCatalogClientTest {

    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private static final String SEARCH_PATH = "/foods/search";
    private static final String DETAIL_PATH = "/food/171265";
    private static final String BATCH_PATH = "/foods";
    private static final String REAL_FOOD_PATH = "/food/168894";

    /**
     * Verbatim slice of a live {@code GET /food/168894} response, in the shape the
     * provider actually uses.
     *
     * <p>Fixtures written from the record definitions rather than from a real response
     * are what let three wire mismatches through: the nutrients nest the id under
     * {@code nutrient} and call the quantity {@code amount}, the household measure lives
     * in {@code modifier} while {@code measureUnit} says "undetermined", and the batch
     * endpoint is a GET answering a bare array. Capturing the real shape here is the
     * guard, so the next field rename fails a test instead of silently reading nulls.</p>
     */
    private static final String REAL_FOOD_168894 = """
            {
              "fdcId": 168894,
              "description": "Wheat flour, white, all-purpose, enriched, bleached",
              "dataType": "SR Legacy",
              "foodCategory": { "id": 20, "description": "Cereal Grains and Pasta" },
              "foodNutrients": [
                { "type": "FoodNutrient", "nutrient": { "id": 2045, "name": "Proximates" } },
                {
                  "type": "FoodNutrient",
                  "nutrient": { "id": 1008, "name": "Energy", "unitName": "kcal" },
                  "amount": 364.0
                },
                {
                  "type": "FoodNutrient",
                  "foodNutrientDerivation": { "id": 1, "code": "A" },
                  "amount": 12.0
                },
                {
                  "type": "FoodNutrient",
                  "nutrient": { "id": 1003, "name": "Protein", "unitName": "g" },
                  "amount": 10.33
                },
                {
                  "type": "FoodNutrient",
                  "nutrient": { "id": 1004, "name": "Total lipid (fat)", "unitName": "g" },
                  "amount": 0.98
                },
                {
                  "type": "FoodNutrient",
                  "nutrient": { "id": 1005, "name": "Carbohydrate, by difference", "unitName": "g" },
                  "amount": 76.31
                }
              ],
              "foodPortions": [
                {
                  "amount": 1.0,
                  "gramWeight": 125.0,
                  "modifier": "cup",
                  "measureUnit": { "id": 1001, "name": "undetermined" }
                }
              ]
            }
            """;

    /** The batch endpoint answers with a bare array, not an object with a "foods" key. */
    private static final String REAL_BATCH_ARRAY = "[" + REAL_FOOD_168894 + "]";

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
    void getById_mapsRealWireNutrientsToDomain() {
        server.expect(requestTo(containsString(REAL_FOOD_PATH)))
                .andRespond(withSuccess(REAL_FOOD_168894, MediaType.APPLICATION_JSON));

        Optional<FoodProfile> profile = adapter.getById(168894);

        assertThat(profile).isPresent();
        assertThat(profile.get().description())
                .isEqualTo("Wheat flour, white, all-purpose, enriched, bleached");
        assertThat(profile.get().category()).isEqualTo("Cereal Grains and Pasta");
        // Read from the live shape: id under "nutrient", value in "amount". The fixture
        // also carries entries with no "nutrient" at all, which must not break the lookup.
        assertThat(profile.get().nutrientsPer100g())
                .extracting(n -> n.energyKcal(), n -> n.proteinG(), n -> n.fatG(), n -> n.carbsG())
                .containsExactly(364.0, 10.33, 0.98, 76.31);
        server.verify();
    }

    @Test
    void getById_capturesThePortionTheRealProviderReportsInModifier() {
        server.expect(requestTo(containsString(REAL_FOOD_PATH)))
                .andRespond(withSuccess(REAL_FOOD_168894, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(168894);

        // measureUnit is "undetermined" here, so a mapping that only reads measureUnit
        // captures nothing at all: 125 g per cup is the entire basis of the conversion.
        assertThat(profile).isPresent();
        assertThat(profile.get().portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 125.0));
        server.verify();
    }

    @Test
    void getById_fallsBackToMeasureUnitWhenModifierIsAbsent() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole",
                         "foodPortions": [
                            {"amount": 1, "gramWeight": 244.0,
                             "measureUnit": {"name": "cup, nf"}}
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
                         "foodNutrients": [
                            {"nutrient": {"id": 1003, "name": "Protein", "unitName": "g"},
                             "amount": 3.15}
                         ]}
                        """, MediaType.APPLICATION_JSON));

        var profile = adapter.getById(171265);

        assertThat(profile).isPresent();
        assertThat(profile.get().nutrientsPer100g().carbsG()).isNull();   // 1005 ausente
        server.verify();
    }

    @Test
    void getByIds_readsTheBareArrayTheProviderReturns() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andExpect(queryParam("api_key", "TEST_KEY"))
                .andExpect(queryParam("format", "full"))
                .andRespond(withSuccess(REAL_BATCH_ARRAY, MediaType.APPLICATION_JSON));

        Map<Long, FoodProfile> profiles = adapter.getByIds(List.of(168894L));

        // A {"foods": [...]} fixture deserialized into a List would have passed the
        // original tests and failed against the real endpoint, which answers with a
        // bare array and rejects the POST body this adapter used to send.
        assertThat(profiles).containsOnlyKeys(168894L);
        assertThat(profiles.get(168894L).description())
                .isEqualTo("Wheat flour, white, all-purpose, enriched, bleached");
        assertThat(profiles.get(168894L).nutrientsPer100g().energyKcal()).isEqualTo(364.0);
        assertThat(profiles.get(168894L).portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 125.0));
        server.verify();
    }

    @Test
    void getByIds_usesAGetWithTheIdsInTheQueryString() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("fdcIds", "171265,169757"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        adapter.getByIds(List.of(171265L, 169757L));

        // The ids travel as a repeated fdcIds parameter; the live endpoint rejects the
        // POST-with-JSON-body shape with 400 "Invalid request".
        server.verify();
    }

    @Test
    void getByIds_unknownId_omittedFromResult() {
        server.expect(requestTo(containsString(BATCH_PATH)))
                .andRespond(withSuccess("""
                        [
                            {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                             "dataType": "SR Legacy", "foodNutrients": []}
                        ]
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
    void getById_mapsHouseholdPortionsFromModifierToDomain() {
        server.expect(requestTo(containsString(DETAIL_PATH)))
                .andRespond(withSuccess("""
                        {"fdcId": 171265, "description": "Milk, whole, 3.25% milkfat",
                         "foodPortions": [
                            {"amount": 1, "gramWeight": 244.0, "modifier": "cup",
                             "measureUnit": {"name": "undetermined"}},
                            {"amount": 1, "gramWeight": 15.0, "modifier": "tbsp",
                             "measureUnit": {"name": "undetermined"}}
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
                        [
                            {"fdcId": 171265, "description": "Milk, whole",
                             "foodPortions": [
                                {"amount": 1, "gramWeight": 244.0, "modifier": "cup",
                                 "measureUnit": {"name": "undetermined"}}
                             ]}
                        ]
                        """, MediaType.APPLICATION_JSON));

        var profiles = adapter.getByIds(List.of(171265L));

        assertThat(profiles.get(171265L).portions())
                .extracting(p -> p.unitCode(), p -> p.gramPerUnit())
                .containsExactly(tuple("CUP", 244.0));
        server.verify();
    }
}