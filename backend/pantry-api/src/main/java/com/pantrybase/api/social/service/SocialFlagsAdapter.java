package com.pantrybase.api.social.service;

import com.pantrybase.api.recipe.port.SocialFlagsPort;
import com.pantrybase.api.social.repository.UserFavoriteRepository;
import com.pantrybase.api.social.repository.UserSavedRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements {@link SocialFlagsPort} so recipe DTOs can show favorite/saved toggles.
 */
@Component
public class SocialFlagsAdapter implements SocialFlagsPort {

    private final UserFavoriteRepository favoriteRepository;
    private final UserSavedRepository savedRepository;

    public SocialFlagsAdapter(UserFavoriteRepository favoriteRepository,
                              UserSavedRepository savedRepository) {
        this.favoriteRepository = favoriteRepository;
        this.savedRepository = savedRepository;
    }

    @Override
    public Set<Long> favoriteRecipeIds(Long userId, Collection<Long> recipeIds) {
        if (userId == null || recipeIds.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findAllByIdUserIdAndIdRecipeIdIn(userId, recipeIds).stream()
                .map(fav -> fav.getId().getRecipeId())
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Long> savedRecipeIds(Long userId, Collection<Long> recipeIds) {
        if (userId == null || recipeIds.isEmpty()) {
            return Set.of();
        }
        return savedRepository.findAllByIdUserIdAndIdRecipeIdIn(userId, recipeIds).stream()
                .map(saved -> saved.getId().getRecipeId())
                .collect(Collectors.toSet());
    }
}
