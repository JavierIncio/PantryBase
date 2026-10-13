package com.pantrybase.api.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pantrybase.api.pantry.service.PantryService;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.recipe.service.RecipeService;
import com.pantrybase.api.recipe.dto.RecipeSummaryDto;
import com.pantrybase.api.user.service.UserService;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final String RECOMMENDATION_PROMPT = """
            You are a cooking assistant that recommends recipes based on the user's pantry inventory.
            
            USER'S PANTRY:
            {{pantry}}
            
            AVAILABLE RECIPES (from catalog):
            {{recipes}}
            
            Your task: Recommend up to 5 recipes that the user can make or nearly make with their current inventory.
            For each recommendation, provide:
            - recipeId (from the available recipes list)
            - title
            - coveragePercentage (how much of the recipe the user can make)
            - missingIngredients (list of ingredient names they don't have or don't have enough of)
            - reasoning (brief explanation of why this recipe fits)
            
            Return ONLY a valid JSON array of recommendations. No extra text.
            
            Example format:
            [
              {
                "recipeId": 123,
                "title": "Chicken Stir Fry",
                "coveragePercentage": 85,
                "missingIngredients": ["soy sauce", "ginger"],
                "reasoning": "You have chicken, vegetables, and rice. Only missing Asian pantry staples."
              }
            ]
            """;

    private final FallbackChatClient fallbackChatClient;
    private final PantryService pantryService;
    private final RecipeService recipeService;
    private final UserService userService;

    public RecommendationService(FallbackChatClient fallbackChatClient,
                                 PantryService pantryService,
                                 RecipeService recipeService,
                                 UserService userService) {
        this.fallbackChatClient = fallbackChatClient;
        this.pantryService = pantryService;
        this.recipeService = recipeService;
        this.userService = userService;
    }

    public List<RecommendationResponse> recommendForUser(Long userId) {
        if (!userService.getPreferences(userId).useAi()) {
            return Collections.emptyList();
        }
        List<PantryItemResponse> pantry = pantryService.list(userId);
        List<RecipeSummaryDto> recipes = recipeService.search(userId, null);

        String pantryText = formatPantry(pantry);
        String recipesText = formatRecipes(recipes);

        var prompt = new PromptTemplate(RECOMMENDATION_PROMPT)
                .create(Map.of("pantry", pantryText, "recipes", recipesText));

        String response = fallbackChatClient.callTextWithFallback(prompt);

        return parseRecommendations(response);
    }

    private String formatPantry(List<PantryItemResponse> pantry) {
        if (pantry.isEmpty()) {
            return "(empty)";
        }
        return pantry.stream()
                .map(item -> "- " + item.name() + ": " + item.quantity() + " " + item.unitCode())
                .collect(Collectors.joining("\n"));
    }

    private String formatRecipes(List<RecipeSummaryDto> recipes) {
        if (recipes.isEmpty()) {
            return "(none)";
        }
        return recipes.stream()
                .limit(50)
                .map(r -> "- id: " + r.id() + ", name: " + r.name() + ", coverage: " + r.matchScore())
                .collect(Collectors.joining("\n"));
    }

    private List<RecommendationResponse> parseRecommendations(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<RecommendationResponse>>() {});
        } catch (Exception e) {
            throw new RecommendationParseException("Failed to parse LLM response: " + json, e);
        }
    }

    public record RecommendationResponse(
            @JsonProperty("recipeId") Long recipeId,
            @JsonProperty("title") String title,
            @JsonProperty("coveragePercentage") Integer coveragePercentage,
            @JsonProperty("missingIngredients") List<String> missingIngredients,
            @JsonProperty("reasoning") String reasoning
    ) {}

    public static class RecommendationParseException extends RuntimeException {
        public RecommendationParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}