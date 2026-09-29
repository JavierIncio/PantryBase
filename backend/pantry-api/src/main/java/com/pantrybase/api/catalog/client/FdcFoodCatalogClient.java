package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * USDA FoodData Central adapter for the {@link FoodCatalogPort}.
 *
 * <p>Issues HTTP calls against the FDC v1 API and translates its wire
 * format into the domain model, so the rest of the catalog stays
 * provider-agnostic. Provider failures surface as
 * {@link FdcProviderException}; an unknown fdcId maps to
 * {@code Optional.empty()}.</p>
 */
public class FdcFoodCatalogClient implements FoodCatalogPort {

    record SearchResponse(List<FoodItem> foods) {
        record FoodItem(long fdcId, String description, String dataType) {
        }
    }

    record FoodDetail(long fdcId, String description, String dataType,
                      FoodCategory foodCategory, List<FoodNutrient> foodNutrients) {
        record FoodCategory(String description) {
        }

        record FoodNutrient(int nutrientId, Double value, String unitName) {
        }
    }

    private final RestClient client;
    private final FdcProperties props;

    public FdcFoodCatalogClient(RestClient client, FdcProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public List<FoodSearchHit> search(String query, int limit) {
        SearchResponse response = client.get().uri(uriBuilder -> uriBuilder
                        .path("/foods/search")
                        .queryParam("api_key", props.apiKey())
                        .queryParam("query", query)
                        .queryParam("dataType", String.join(",", props.dataTypes()))
                        .queryParam("pageSize", Math.min(limit, props.pageSize()))
                        .build())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), ((req, res) -> {
                    throw new FdcProviderException("FDC search failed: " + req.getURI());
                }))
                .body(SearchResponse.class);

        return response.foods().stream()
                .map(f -> new FoodSearchHit(f.fdcId(), f.description(), f.dataType()))
                .toList();
    }

    @Override
    public Optional<FoodProfile> getById(long fdcId) {
        FoodDetail detail = client.get().uri(uriBuilder -> uriBuilder
                        .path("/food/{fdcId}")
                        .queryParam("api_key", props.apiKey())
                        .build(fdcId))
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    /* no food: Optional.empty below */
                })
                .onStatus(s -> s.value() != 200, (req, res) -> {
                    throw new FdcProviderException("FDC lookup failed (fdcId=%d)".formatted(fdcId));
                })
                .body(FoodDetail.class);
        if (detail == null) return Optional.empty();
        return Optional.of(mapDetail(detail));
    }

    private FoodProfile mapDetail(FoodDetail d) {
        return new FoodProfile(
                d.fdcId(), d.description(), d.dataType(),
                d.foodCategory() == null ? null : d.foodCategory().description(),
                new NutrientProfile(
                        nutrient(d, 1008), nutrient(d, 1003),
                        nutrient(d, 1004), nutrient(d, 1005)));
    }

    private Double nutrient(FoodDetail d, int nutrientId) {
        return d.foodNutrients().stream()
                .filter(n -> n.nutrientId() == nutrientId)
                .map(FoodDetail.FoodNutrient::value)
                .findFirst().orElse(null);
    }

}
