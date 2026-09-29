package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FdcFoodCatalogClientTest {

    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private static final String SEARCH_PATH = "/foods/search";
    private static final String DETAIL_PATH = "/food/171265";

    private MockRestServiceServer server;
    private FdcFoodCatalogClient adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(clientBuilder).build();
        adapter = new FdcFoodCatalogClient(clientBuilder.build(), new FdcProperties(
                "TEST_KEY", BASE_URL,
                List.of("Foundation", "SR Legacy"), 20,
                Duration.ofSeconds(2), Duration.ofSeconds(5)));
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
}