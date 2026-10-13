package com.pantrybase.api.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pantrybase.api.pantry.service.PantryService;
import com.pantrybase.api.pantry.dto.PantryItemResponse;
import com.pantrybase.api.recipe.service.RecipeService;
import com.pantrybase.api.recipe.dto.RecipeSearchRequest;
import com.pantrybase.api.recipe.dto.RecipeSummaryDto;
import com.pantrybase.api.user.service.UserService;
import org.springframework.ai.chat.client.ChatClient;
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
            
            You MUST call the submitRecommendations function with your recommendations.
            Do NOT output any text outside of the function call.
            """;

    private static final String SUBMIT_RECOMMENDATIONS_FUNCTION = "submitRecommendations";

    private final FallbackChatClient fallbackChatClient;
    private final PantryService pantryService;
    private final RecipeService recipeService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public RecommendationService(FallbackChatClient fallbackChatClient,
                                 PantryService pantryService,
                                 RecipeService recipeService,
                                 UserService userService) {
        this.fallbackChatClient = fallbackChatClient;
        this.pantryService = pantryService;
        this.recipeService = recipeService;
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
    }

    public List<RecommendationResponse> recommendForUser(Long userId) {
        if (!userService.getPreferences(userId).useAi()) {
            return Collections.emptyList();
        }
        List<PantryItemResponse> pantry = pantryService.list(userId);
        // An untyped listing (no name/ingredient filter) stays local: the model picks from
        // the user's own and already-materialised recipes. `search` dereferences the request
        // unconditionally, so it must be an explicit empty filter, never null.
        List<RecipeSummaryDto> recipes = recipeService.search(userId,
                new RecipeSearchRequest(null, null, null, null, null));

        String pantryText = formatPantry(pantry);
        String recipesText = formatRecipes(recipes);

        var prompt = new PromptTemplate(RECOMMENDATION_PROMPT)
                .create(Map.of("pantry", pantryText, "recipes", recipesText));

        ObjectNode functionSchema = buildRecommendationSchema();

        String response = fallbackChatClient.callWithFunction(prompt, SUBMIT_RECOMMENDATIONS_FUNCTION, functionSchema);

        return parseRecommendations(response);
    }

    private ObjectNode buildRecommendationSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("description", "Submit recipe recommendations based on pantry and catalog");

        ObjectNode properties = schema.putObject("properties");
        ArrayNode recommendations = properties.putArray("recommendations");

        ObjectNode itemSchema = objectMapper.createObjectNode();
        itemSchema.put("type", "object");
        itemSchema.put("description", "A single recipe recommendation");

        ObjectNode itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("recipeId").put("type", "integer").put("description", "Recipe ID from the available recipes list");
        itemProperties.putObject("title").put("type", "string").put("description", "Recipe title");
        itemProperties.putObject("coveragePercentage").put("type", "integer").put("minimum", 0).put("maximum", 100).put("description", "Percentage of recipe the user can make");
        ObjectNode missingItems = itemProperties.putObject("missingIngredients");
        missingItems.put("type", "array").put("description", "List of ingredient names the user is missing or doesn't have enough of");
        missingItems.putObject("items").put("type", "string");
        itemProperties.putObject("reasoning").put("type", "string").put("description", "Brief explanation of why this recipe fits");

        ArrayNode required = itemSchema.putArray("required");
        required.add("recipeId").add("title").add("coveragePercentage").add("missingIngredients").add("reasoning");

        recommendations.add(itemSchema);

        ArrayNode requiredArray = schema.putArray("required");
        requiredArray.add("recommendations");

        return schema;
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
            JsonNode root = objectMapper.readTree(json);
            JsonNode recommendations = root.path("recommendations");
            if (!recommendations.isArray()) {
                throw new RecommendationParseException("Expected 'recommendations' array in response: " + json, new IllegalArgumentException());
            }

            return objectMapper.readValue(
                    recommendations.traverse(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<RecommendationResponse>>() {}
            );
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