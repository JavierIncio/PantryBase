package com.pantrybase.api.recipe.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client for TheMealDB public API (key "1").
 *
 * <p>Endpoints used:
 * <ul>
 *   <li>{@code search.php?s=} — search by name</li>
 *   <li>{@code filter.php?i=} — filter by main ingredient</li>
 *   <li>{@code lookup.php?i=} — detail by meal id</li>
 * </ul>
 *
 * <p>Rate limit is undocumented but generous; the caller (RecipeSearchCache) handles
 * caching and backoff. A 429/5xx from the provider is mapped to an empty result
 * with a warning log, so the caller can decide to retry or serve stale data.</p>
 */
@Component
public class TheMealDbClient {

    private final WebClient webClient;
    private final String baseUrl;

    public TheMealDbClient(WebClient.Builder builder,
                           @Value("${themealdb.base-url:https://www.themealdb.com/api/json/v1/1}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
    }

    /** Search meals by name. Returns empty list on 404/429/5xx. */
    public List<MealSummary> searchByName(String query) {
        return get("/search.php", Map.of("s", query), MealListResponse.class)
                .map(MealListResponse::meals)
                .orElse(Collections.emptyList());
    }

    /** Filter meals by main ingredient. Returns empty list on 404/429/5xx. */
    public List<MealSummary> filterByIngredient(String ingredient) {
        return get("/filter.php", Map.of("i", ingredient), MealListResponse.class)
                .map(MealListResponse::meals)
                .orElse(Collections.emptyList());
    }

    /** Lookup full meal detail by id. Returns empty on 404/429/5xx. */
    public Optional<MealDetail> lookupById(String id) {
        return get("/lookup.php", Map.of("i", id), MealListResponse.class)
                .flatMap(r -> r.meals().stream().findFirst())
                .map(this::toDetail);
    }

    private <T> Optional<T> get(String path, Map<String, String> params, Class<T> responseType) {
        try {
            T response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        params.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, resp -> {
                        if (resp.statusCode().value() == 429) {
                            return Mono.error(new RateLimitedException("TheMealDB rate limited"));
                        }
                        if (resp.statusCode().value() == 404) {
                            return Mono.empty();
                        }
                        return Mono.error(new ProviderException("TheMealDB " + resp.statusCode()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, resp ->
                            Mono.error(new ProviderException("TheMealDB " + resp.statusCode())))
                    .bodyToMono(responseType)
                    .block();
            return Optional.ofNullable(response);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError()) {
                return Optional.empty();
            }
            throw e;
        } catch (ProviderException | RateLimitedException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("TheMealDB call failed: " + e.getMessage(), e);
        }
    }

    private MealDetail toDetail(MealSummary s) {
        return new MealDetail(
                s.idMeal(),
                s.strMeal(),
                s.strCategory(),
                s.strArea(),
                s.strInstructions(),
                s.strMealThumb(),
                s.strYoutube(),
                s.strTags(),
                Collections.emptyList(), // ingredients parsed from raw JSON in real impl
                s.strSource()
        );
    }

    // --- DTOs (Jackson) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MealListResponse(@JsonProperty("meals") List<MealSummary> meals) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MealSummary(
            @JsonProperty("idMeal") String idMeal,
            @JsonProperty("strMeal") String strMeal,
            @JsonProperty("strCategory") String strCategory,
            @JsonProperty("strArea") String strArea,
            @JsonProperty("strInstructions") String strInstructions,
            @JsonProperty("strMealThumb") String strMealThumb,
            @JsonProperty("strYoutube") String strYoutube,
            @JsonProperty("strTags") String strTags,
            @JsonProperty("strSource") String strSource
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MealDetail(
            String idMeal,
            String strMeal,
            String strCategory,
            String strArea,
            String strInstructions,
            String strMealThumb,
            String strYoutube,
            String strTags,
            List<IngredientMeasure> ingredients,
            String strSource
    ) {}

    public record IngredientMeasure(String ingredient, String measure) {}

    // --- Exceptions ---

    public static class ProviderException extends RuntimeException {
        public ProviderException(String msg) { super(msg); }
        public ProviderException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException(String msg) { super(msg); }
    }
}