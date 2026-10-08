package com.pantrybase.api.recipe.web;

import com.pantrybase.api.recipe.dto.CreateRecipeRequest;
import com.pantrybase.api.recipe.dto.RecipeDetailDto;
import com.pantrybase.api.recipe.dto.RecipeSearchRequest;
import com.pantrybase.api.recipe.dto.RecipeSummaryDto;
import com.pantrybase.api.recipe.exception.RecipeNotFoundException;
import com.pantrybase.api.recipe.service.RecipeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recipe search, detail and creation for the authenticated user.
 *
 * <p>Coverage and allergen filtering are evaluated against the caller's own pantry and
 * exclusions: the owner comes from the authentication principal, never from the path or
 * the body, so no parameter lets one user compute another user's match score.</p>
 *
 * <p>No pagination, mirroring {@code /api/pantry}: the candidate set is the local recipe
 * store (user + materialised provider recipes) plus at most one provider round-trip, and
 * the grid client already renders that whole set. Paging would have to be re-computed on
 * every pantry change, because the score depends on quantities the user edits elsewhere.</p>
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * Searches recipes, scored against the caller's pantry.
     *
     * <p>The optional filters ({@code name}, {@code ingredientId}, {@code strict},
     * {@code threshold}, {@code allergenSafe}) are query parameters rather than a JSON
     * body because a GET keeps the result cacheable and shareable as a URL — a search
     * with a body cannot be bookmarked or linked.</p>
     */
    @GetMapping
    public List<RecipeSummaryDto> search(@AuthenticationPrincipal Long userId,
                                         RecipeSearchRequest request) {
        return recipeService.search(userId, request);
    }

    /**
     * Suggests recipes driven by what the caller's pantry already holds.
     *
     * <p>Shares the search filters ({@code strict}, {@code allergenSafe}) and returns the
     * same summary shape, so the recipes page reuses one grid. The route is declared
     * before {@code /{recipeId}}; a literal segment always wins over a path variable, and
     * "recommend" could never parse as a numeric id anyway.</p>
     */
    @GetMapping("/recommend")
    public List<RecipeSummaryDto> recommend(@AuthenticationPrincipal Long userId,
                                            RecipeSearchRequest request) {
        return recipeService.recommend(userId, request);
    }

    /**
     * Returns one recipe with its per-ingredient coverage breakdown.
     *
     * <p>404 rather than an empty body for an unknown id: the client renders a route for
     * every recipe it lists, and only an explicit status tells it the recipe is gone
     * instead of merely empty.</p>
     */
    @GetMapping("/{recipeId}")
    public RecipeDetailDto detail(@AuthenticationPrincipal Long userId,
                                  @PathVariable long recipeId) {
        return recipeService.detail(userId, recipeId)
                .orElseThrow(() -> new RecipeNotFoundException(recipeId));
    }

    /** Creates a recipe owned by nobody but visible to everybody, as source USER. */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public RecipeDetailDto create(@AuthenticationPrincipal Long userId,
                                  @Valid @RequestBody CreateRecipeRequest request) {
        return recipeService.createUserRecipe(userId, request);
    }
}
