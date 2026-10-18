package com.pantrybase.api.recipe.port;

import java.util.Collection;
import java.util.Set;

/**
 * Reads the current user's social flags (favorite/saved) for recipes.
 *
 * <p>Declared in the recipe module as a port so recipe DTOs can carry the flags
 * without depending on the social module: the social module implements it, keeping
 * the dependency one-directional (social → recipe, never the reverse).</p>
 */
public interface SocialFlagsPort {

    /** Recipe ids among {@code recipeIds} the user marked as favorite. */
    Set<Long> favoriteRecipeIds(Long userId, Collection<Long> recipeIds);

    /** Recipe ids among {@code recipeIds} the user saved for later. */
    Set<Long> savedRecipeIds(Long userId, Collection<Long> recipeIds);
}
