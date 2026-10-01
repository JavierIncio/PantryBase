package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.domain.Portion;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                      FoodCategory foodCategory, List<FoodNutrient> foodNutrients,
                      List<FoodPortion> foodPortions) {
        record FoodCategory(String description) {
        }

        record FoodNutrient(int nutrientId, Double value, String unitName) {
        }
    }

    record FoodPortion(Double amount, Double gramWeight, MeasureUnit measureUnit) {
        record MeasureUnit(String name) {
        }
    }

    record BatchRequest(List<Long> fdcIds) {
    }

    record BatchResponse(List<FoodDetail> foods) {
    }

    /** Household volume measures captured from the provider; others are skipped. */
    private static final Map<String, String> PORTION_UNIT_CODES = Map.of(
            "tsp", "TSP", "tbsp", "TBSP", "cup", "CUP", "fl oz", "FLOZ", "pint", "PINT");

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

    /**
     * Fetches profiles in one {@code POST /foods} call using the full format,
     * so the wire food shape (and mapping) matches {@link #getById(long)}.
     * Unknown ids are simply absent from the returned map, never an error.
     */
    @Override
    public Map<Long, FoodProfile> getByIds(Collection<Long> fdcIds) {
        BatchResponse response = client.post().uri(uriBuilder -> uriBuilder
                        .path("/foods")
                        .queryParam("api_key", props.apiKey())
                        .queryParam("format", "full")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BatchRequest(new ArrayList<>(fdcIds)))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), (req, res) -> {
                    throw new FdcProviderException("FDC batch lookup failed: " + req.getURI());
                })
                .body(BatchResponse.class);

        return response.foods().stream()
                .map(this::mapDetail)
                .collect(Collectors.toMap(FoodProfile::fdcId, Function.identity(), (a, b) -> a));
    }

    private FoodProfile mapDetail(FoodDetail d) {
        return new FoodProfile(
                d.fdcId(), d.description(), d.dataType(),
                d.foodCategory() == null ? null : d.foodCategory().description(),
                new NutrientProfile(
                        nutrient(d, 1008), nutrient(d, 1003),
                        nutrient(d, 1004), nutrient(d, 1005)),
                mapPortions(d));
    }

    private List<Portion> mapPortions(FoodDetail d) {
        if (d.foodPortions() == null || d.foodPortions().isEmpty()) return List.of();
        Map<String, Portion> byUnit = new LinkedHashMap<>();
        for (FoodPortion p : d.foodPortions()) {
            if (p == null || p.gramWeight() == null || p.gramWeight() <= 0) continue;
            Double amount = (p.amount() == null || p.amount() <= 0) ? 1.0 : p.amount();
            String code = portionUnitCode(p.measureUnit() == null ? null : p.measureUnit().name());
            if (code == null) continue;
            double gpu = p.gramWeight() / amount;
            if (gpu <= 0) continue;
            // A measure is unique per (ingredient, unit); the provider can repeat a unit
            // ("cup" and "cup, chopped"), so the first recognized one wins.
            byUnit.putIfAbsent(code, new Portion(code, gpu));
        }
        return List.copyOf(byUnit.values());
    }

    /**
     * Resolves the measure unit name to one of our volume unit codes, or {@code null}
     * when it is not a household volume measure we support.
     *
     * <p>Only volume units are captured: weight measures (oz, lb) would be redundant
     * with {@code measure_conversions}. The provider qualifies a measure with the
     * preparation mode ("cup, nf", "cup, chopped", "tbsp, level"), and every
     * qualification of a base volume measure describes the same volume, so the base
     * measure before the comma is the one that matters.</p>
     */
    private String portionUnitCode(String name) {
        if (name == null || name.isBlank()) return null;

        String norm = name.toLowerCase(Locale.ROOT).trim();
        String code = PORTION_UNIT_CODES.get(norm);
        if (code != null) return code;

        int qualifier = norm.indexOf(',');
        return qualifier > 0 ? PORTION_UNIT_CODES.get(norm.substring(0, qualifier).trim()) : null;
    }

    private Double nutrient(FoodDetail d, int nutrientId) {
        if (d.foodNutrients() == null) return null;
        return d.foodNutrients().stream()
                .filter(n -> n.nutrientId() == nutrientId)
                .map(FoodDetail.FoodNutrient::value)
                .findFirst().orElse(null);
    }

}
