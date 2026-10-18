package com.pantrybase.api.social.service;

import com.pantrybase.api.recipe.exception.RecipeNotFoundException;
import com.pantrybase.api.recipe.repository.RecipeRepository;
import com.pantrybase.api.social.domain.RecipeHistory;
import com.pantrybase.api.social.domain.UserFavorite;
import com.pantrybase.api.social.domain.UserFavoriteId;
import com.pantrybase.api.social.domain.UserSaved;
import com.pantrybase.api.social.domain.UserSavedId;
import com.pantrybase.api.social.dto.HistoryItemResponse;
import com.pantrybase.api.social.repository.RecipeHistoryRepository;
import com.pantrybase.api.social.repository.UserFavoriteRepository;
import com.pantrybase.api.social.repository.UserSavedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service backing the social features: favorites, saved recipes and cooking history.
 *
 * <p>Toggles are idempotent: a second {@code PUT} of an already-favorite recipe is a no-op
 * and a {@code DELETE} of something not present succeeds silently, so the client's heart
 * and bookmark buttons can retry without a read-before-write dance.</p>
 */
@Service
@Transactional(readOnly = true)
public class SocialService {

    private final RecipeHistoryRepository historyRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final UserSavedRepository savedRepository;
    private final RecipeRepository recipeRepository;

    public SocialService(RecipeHistoryRepository historyRepository,
                         UserFavoriteRepository favoriteRepository,
                         UserSavedRepository savedRepository,
                         RecipeRepository recipeRepository) {
        this.historyRepository = historyRepository;
        this.favoriteRepository = favoriteRepository;
        this.savedRepository = savedRepository;
        this.recipeRepository = recipeRepository;
    }

    /** The user's cooking history, most recent first. */
    public List<HistoryItemResponse> history(Long userId) {
        return historyRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    /** The ids the user marked as favorite, most recent first. */
    public List<Long> favoriteIds(Long userId) {
        return favoriteRepository.findAllByIdUserIdOrderByCreatedAtDesc(userId).stream()
                .map(fav -> fav.getId().getRecipeId())
                .toList();
    }

    /** The ids the user saved for later, most recent first. */
    public List<Long> savedIds(Long userId) {
        return savedRepository.findAllByIdUserIdOrderByCreatedAtDesc(userId).stream()
                .map(saved -> saved.getId().getRecipeId())
                .toList();
    }

    /**
     * Marks a recipe as favorite.
     *
     * @throws RecipeNotFoundException if the recipe does not exist
     */
    @Transactional
    public void addFavorite(Long userId, long recipeId) {
        requireRecipe(recipeId);
        if (!favoriteRepository.existsByIdUserIdAndIdRecipeId(userId, recipeId)) {
            favoriteRepository.save(new UserFavorite(new UserFavoriteId(userId, recipeId)));
        }
    }

    /** Removes the favorite mark; a missing mark is a no-op. */
    @Transactional
    public void removeFavorite(Long userId, long recipeId) {
        favoriteRepository.deleteById(new UserFavoriteId(userId, recipeId));
    }

    /**
     * Saves a recipe for later.
     *
     * @throws RecipeNotFoundException if the recipe does not exist
     */
    @Transactional
    public void addSaved(Long userId, long recipeId) {
        requireRecipe(recipeId);
        if (!savedRepository.existsByIdUserIdAndIdRecipeId(userId, recipeId)) {
            savedRepository.save(new UserSaved(new UserSavedId(userId, recipeId)));
        }
    }

    /** Removes the saved mark; a missing mark is a no-op. */
    @Transactional
    public void removeSaved(Long userId, long recipeId) {
        savedRepository.deleteById(new UserSavedId(userId, recipeId));
    }

    /**
     * Records a completed cooking session exactly once.
     *
     * <p>Called inside the completing transaction so history and the pantry deduction
     * commit together; the unique constraint on {@code cooking_session_id} is the real
     * guard against a double completion, and the existence check only avoids a pointless
     * constraint violation on the retry path.</p>
     */
    @Transactional
    public void recordCompletion(Long userId, long recipeId, long cookingSessionId) {
        if (!historyRepository.existsByCookingSessionId(cookingSessionId)) {
            historyRepository.save(new RecipeHistory(userId, recipeId, cookingSessionId));
        }
    }

    private void requireRecipe(long recipeId) {
        if (!recipeRepository.existsById(recipeId)) {
            throw new RecipeNotFoundException(recipeId);
        }
    }

    private HistoryItemResponse toDto(RecipeHistory h) {
        return new HistoryItemResponse(h.getId(), h.getUserId(), h.getRecipeId(), h.getCookingSessionId(), h.getCreatedAt());
    }
}
