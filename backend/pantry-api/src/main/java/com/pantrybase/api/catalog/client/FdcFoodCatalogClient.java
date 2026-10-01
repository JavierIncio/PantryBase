package com.pantrybase.api.catalog.client;

import com.pantrybase.api.catalog.domain.FoodCatalogPort;
import com.pantrybase.api.catalog.domain.FoodProfile;
import com.pantrybase.api.catalog.domain.FoodSearchHit;
import com.pantrybase.api.catalog.domain.NutrientProfile;
import com.pantrybase.api.catalog.domain.Portion;
import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.exception.FdcRateLimitException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.net.URI;
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

        /**
         * The provider nests the nutrient definition and names the quantity {@code amount},
         * not {@code value}. Both shapes were checked against the live API for fdcId
         * 168894, where reading {@code nutrientId}/{@code value} yields four nulls instead
         * of 10.33 g of protein, 0.98 g of fat, 76.31 g of carbs and 364 kcal.
         */
        record FoodNutrient(NutrientDefinition nutrient, Double amount) {
        }

        record NutrientDefinition(int id, String unitName) {
        }
    }

    /**
     * The unit name is in {@code modifier}, not in {@code measureUnit}.
     *
     * <p>For every food checked against the live API the provider reports
     * {@code measureUnit.name = "undetermined"} and puts the real unit in
     * {@code modifier} ("cup", "tablespoon", "tsp"). Reading only
     * {@code measureUnit.name} therefore discarded every real household measure, which
     * is why the captured measure that H2-B-3 relies on never actually appeared.</p>
     */
    record FoodPortion(Double amount, Double gramWeight, String modifier, MeasureUnit measureUnit) {
        record MeasureUnit(String name) {
        }
    }

    /**
     * The batch endpoint is {@code GET /foods?fdcIds=} and answers with a bare JSON array.
     *
     * <p>It is not a {@code POST} with a {@code {"fdcIds": [...]}} body: that request is
     * rejected by the provider with 400 "Invalid request", and the tests missed it because
     * the stubbed server answered whatever shape the adapter expected. The response is an
     * array of foods, not an object with a {@code foods} property, so it is deserialized
     * as a {@code List}.</p>
     */
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
                .onStatus(HttpStatusCode::is4xxClientError, ((req, res) -> {
                    throw statusAware("FDC search failed: ", req.getURI(), res.getStatusCode().value());
                }))
                .onStatus(s -> !s.is2xxSuccessful(), ((req, res) -> {
                    throw new FdcProviderException("FDC search failed: " + safeUri(req.getURI()));
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
                .onStatus(s -> s.value() == 429, (req, res) -> {
                    throw new FdcRateLimitException("FDC quota exhausted (fdcId=%d)".formatted(fdcId));
                })
                .onStatus(s -> s.value() != 200, (req, res) -> {
                    throw new FdcProviderException("FDC lookup failed (fdcId=%d)".formatted(fdcId));
                })
                .body(FoodDetail.class);
        if (detail == null) return Optional.empty();
        return Optional.of(mapDetail(detail));
    }

    /**
     * Fetches profiles in one {@code GET /foods?fdcIds=} call using the full format,
     * so the wire food shape (and mapping) matches {@link #getById(long)}.
     * Unknown ids are simply absent from the returned map, never an error.
     */
    @Override
    public Map<Long, FoodProfile> getByIds(Collection<Long> fdcIds) {
        List<FoodDetail> response = client.get().uri(uriBuilder -> uriBuilder
                        .path("/foods")
                        .queryParam("api_key", props.apiKey())
                        .queryParam("format", "full")
                        .queryParam("fdcIds", fdcIds.stream().map(String::valueOf)
                                .collect(Collectors.joining(",")))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, ((req, res) -> {
                    throw statusAware("FDC batch lookup failed: ", req.getURI(), res.getStatusCode().value());
                }))
                .onStatus(s -> !s.is2xxSuccessful(), (req, res) -> {
                    throw new FdcProviderException("FDC batch lookup failed: " + safeUri(req.getURI()));
                })
                .body(new ParameterizedTypeReference<List<FoodDetail>>() {});

        return response.stream()
                .map(this::mapDetail)
                .collect(Collectors.toMap(FoodProfile::fdcId, Function.identity(), (a, b) -> a));
    }

    /**
     * Renders a request URI for an error message with the API key redacted.
     *
     * <p>The key must travel as a query parameter because the FDC API accepts no other
     * form, and these URIs end up inside {@link FdcProviderException}, which the global
     * handler logs. Printing the raw URI would therefore write the key to the logs on the
     * first provider error, and logs outlive the fix that revoked the leaked key. The value
     * is replaced rather than the whole parameter, so the message still shows which call
     * failed and with what arguments.</p>
     */
    /**
     * Builds the exception for a 4xx, separating the quota case from the rest.
     *
     * <p>Registered as its own handler ahead of the generic one, because a 429 needs
     * the caller to fall back to a local copy instead of reporting an outage, and
     * a 4xx carries no server-side detail worth reporting either way. The URI is
     * still redacted here: a 429 is one of the most likely responses to see in
     * normal operation, which makes it the last place that must not print the key.</p>
     */
    private FdcProviderException statusAware(String prefix, URI uri, int statusCode) {
        if (statusCode == 429) {
            return new FdcRateLimitException(prefix + safeUri(uri));
        }
        return new FdcProviderException(prefix + safeUri(uri) + " (status=" + statusCode + ")");
    }

    private String safeUri(URI uri) {
        if (uri == null) return null;
        String rendered = uri.toString();
        String key = props.apiKey();
        if (key == null || key.isBlank()) return rendered;

        return rendered.replaceAll("(?i)([?&]api_key=)[^&]*", "$1***");
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
            String code = portionUnitCode(portionUnitName(p));
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
     * Picks the field that actually names the unit.
     *
     * <p>The live API reports {@code measureUnit.name = "undetermined"} for household
     * measures and carries the real unit in {@code modifier}. Preferring {@code modifier}
     * and falling back to {@code measureUnit.name} covers both: the observed shape, and
     * the shape used by older {@code SR Legacy} rows where {@code measureUnit} is
     * properly populated.</p>
     */
    private String portionUnitName(FoodPortion p) {
        if (p.modifier() != null && !p.modifier().isBlank()) {
            return p.modifier();
        }
        return p.measureUnit() == null ? null : p.measureUnit().name();
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

    /**
     * Reads one nutrient per 100 g by its provider id.
     *
     * <p>The id lives in the nested {@code nutrient} object and the quantity is
     * {@code amount}; entries may also be missing {@code nutrient} entirely, which
     * happens for derivation and conversion rows in the same array.</p>
     */
    private Double nutrient(FoodDetail d, int nutrientId) {
        if (d.foodNutrients() == null) return null;
        return d.foodNutrients().stream()
                .filter(n -> n.nutrient() != null && n.nutrient().id() == nutrientId)
                .map(FoodDetail.FoodNutrient::amount)
                .findFirst().orElse(null);
    }

}
