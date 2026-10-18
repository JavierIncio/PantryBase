package com.pantrybase.api.social.repository;

import com.pantrybase.api.social.domain.RecipeHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the cooking history of a user.
 *
 * <p>Inserts are guarded by {@link #findByCookingSessionId}: the unique constraint on
 * {@code cooking_session_id} is the final arbiter, but the lookup lets callers treat a
 * repeated completion as a no-op instead of fighting a constraint violation.</p>
 */
public interface RecipeHistoryRepository extends JpaRepository<RecipeHistory, Long> {

    Optional<RecipeHistory> findByCookingSessionId(Long cookingSessionId);

    List<RecipeHistory> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<RecipeHistory> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /** Every completed cook by the user, used to weight the profile ingredient ranking. */
    List<RecipeHistory> findAllByUserId(Long userId);

    boolean existsByCookingSessionId(Long cookingSessionId);
}
