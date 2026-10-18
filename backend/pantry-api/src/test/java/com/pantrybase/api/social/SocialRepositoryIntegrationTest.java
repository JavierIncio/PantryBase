package com.pantrybase.api.social;

import com.pantrybase.api.AbstractIntegrationTest;
import com.pantrybase.api.cooking.domain.CookingSession;
import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import com.pantrybase.api.cooking.repository.CookingSessionRepository;
import com.pantrybase.api.recipe.domain.Recipe;
import com.pantrybase.api.recipe.repository.RecipeRepository;
import com.pantrybase.api.social.domain.RecipeHistory;
import com.pantrybase.api.social.domain.UserFavorite;
import com.pantrybase.api.social.domain.UserFavoriteId;
import com.pantrybase.api.social.domain.UserSaved;
import com.pantrybase.api.social.domain.UserSavedId;
import com.pantrybase.api.social.repository.RecipeHistoryRepository;
import com.pantrybase.api.social.repository.UserFavoriteRepository;
import com.pantrybase.api.social.repository.UserSavedRepository;
import com.pantrybase.api.user.domain.User;
import com.pantrybase.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for social repositories: favorites, saved and history.
 */
class SocialRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserFavoriteRepository favoriteRepository;

    @Autowired
    private UserSavedRepository savedRepository;

    @Autowired
    private RecipeHistoryRepository historyRepository;

    @Autowired
    private CookingSessionRepository cookingSessionRepository;

    private User user;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        favoriteRepository.deleteAll();
        savedRepository.deleteAll();
        historyRepository.deleteAll();
        cookingSessionRepository.deleteAll();
        recipeRepository.deleteAll();
        userRepository.deleteAll();

        user = new User("tester", "test@example.com", "hash");
        user.setRoles(java.util.Set.of(com.pantrybase.api.user.domain.Role.USER));
        user = userRepository.save(user);

        recipe = new Recipe();
        recipe.setSource(Recipe.Source.USER.name());
        recipe.setExternalId("uuid-1");
        recipe.setName("Test Recipe");
        recipe.setCreatedAt(java.time.Instant.now());
        recipe.setUpdatedAt(recipe.getCreatedAt());
        recipe = recipeRepository.save(recipe);
    }

    @Test
    void favoritesAreUniquePerUserAndRecipe() {
        UserFavorite f1 = new UserFavorite(new UserFavoriteId(user.getId(), recipe.getId()));
        favoriteRepository.save(f1);

        assertThat(favoriteRepository.findAllByIdUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(favoriteRepository.existsByIdUserIdAndIdRecipeId(user.getId(), recipe.getId())).isTrue();
    }

    @Test
    void savedAreSeparatedFromFavorites() {
        UserSaved s1 = new UserSaved(new UserSavedId(user.getId(), recipe.getId()));
        savedRepository.save(s1);

        assertThat(savedRepository.findAllByIdUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(savedRepository.existsByIdUserIdAndIdRecipeId(user.getId(), recipe.getId())).isTrue();
        assertThat(favoriteRepository.findAllByIdUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
    }

    @Test
    void historyEnforcesOnePerCompletedSession() {
        CookingSession session = new CookingSession();
        session.setUserId(user.getId());
        session.setRecipeId(recipe.getId());
        session.setStatus(CookingSessionStatus.COMPLETED);
        session = cookingSessionRepository.save(session);

        RecipeHistory h1 = new RecipeHistory(user.getId(), recipe.getId(), session.getId());
        historyRepository.save(h1);

        assertThat(historyRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(historyRepository.existsByCookingSessionId(session.getId())).isTrue();
        assertThat(historyRepository.findByCookingSessionId(session.getId())).isPresent();
    }

    @Test
    void historyRejectsASecondRowForTheSameSession() {
        CookingSession session = new CookingSession();
        session.setUserId(user.getId());
        session.setRecipeId(recipe.getId());
        session.setStatus(CookingSessionStatus.COMPLETED);
        CookingSession completed = cookingSessionRepository.save(session);

        historyRepository.save(new RecipeHistory(user.getId(), recipe.getId(), completed.getId()));

        // A second insert for the same session is blocked by uk_recipe_history_session,
        // not by a read-then-write guard in the caller.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> historyRepository.saveAndFlush(
                        new RecipeHistory(user.getId(), recipe.getId(), completed.getId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(historyRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
    }
}
