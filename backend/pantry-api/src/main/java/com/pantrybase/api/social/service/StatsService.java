package com.pantrybase.api.social.service;

import com.pantrybase.api.recipe.domain.RecipeIngredient;
import com.pantrybase.api.recipe.repository.RecipeIngredientRepository;
import com.pantrybase.api.social.domain.RecipeHistory;
import com.pantrybase.api.social.dto.IngredientUsageResponse;
import com.pantrybase.api.social.dto.UserStatsResponse;
import com.pantrybase.api.social.repository.RecipeHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the profile statistics from the cooking history.
 *
 * <p>Lives in the social module on purpose: it reads history and recipe lines, and both
 * are reachable from here (social already depends on recipe). Placing it in the user
 * module would close a cycle, since recipe already depends on user.</p>
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final int TOP_INGREDIENTS = 5;

    private final RecipeHistoryRepository historyRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public StatsService(RecipeHistoryRepository historyRepository,
                        RecipeIngredientRepository recipeIngredientRepository) {
        this.historyRepository = historyRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
    }

    /**
     * Builds the user's stats: how many times they cooked, over how many distinct
     * recipes, and which ingredients they reached for most.
     */
    public UserStatsResponse stats(Long userId) {
        List<RecipeHistory> history = historyRepository.findAllByUserId(userId);
        Set<Long> recipeIds = history.stream()
                .map(RecipeHistory::getRecipeId)
                .collect(Collectors.toSet());

        return new UserStatsResponse(history.size(), recipeIds.size(),
                topIngredients(history, recipeIds));
    }

    /**
     * Ranks ingredients by how many completed cooks used them.
     *
     * <p>Each completed session of a recipe counts its lines once, so cooking the same
     * recipe twice counts its ingredients twice, which is what "most cooked" should mean.
     * Unmatched lines carry no catalog ingredient and are skipped.</p>
     */
    private List<IngredientUsageResponse> topIngredients(List<RecipeHistory> history, Set<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RecipeIngredient>> linesByRecipe = recipeIngredientRepository
                .findByRecipeIdIn(recipeIds).stream()
                .collect(Collectors.groupingBy(RecipeIngredient::getRecipeId));

        Map<Long, Long> counts = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (RecipeHistory entry : history) {
            for (RecipeIngredient line : linesByRecipe.getOrDefault(entry.getRecipeId(), List.of())) {
                Long ingredientId = line.getIngredientId();
                if (ingredientId == null) {
                    continue;
                }
                counts.merge(ingredientId, 1L, Long::sum);
                names.putIfAbsent(ingredientId, line.getIngredientName());
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new IngredientUsageResponse(
                        entry.getKey(), names.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(IngredientUsageResponse::count).reversed()
                        .thenComparing(IngredientUsageResponse::name,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(TOP_INGREDIENTS)
                .toList();
    }
}
