package com.pantrybase.api.social.repository;

import com.pantrybase.api.social.domain.UserFavorite;
import com.pantrybase.api.social.domain.UserFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and writes the favorites of a user. */
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {

    List<UserFavorite> findAllByIdUserIdOrderByCreatedAtDesc(Long userId);

    List<UserFavorite> findAllByIdUserIdAndIdRecipeIdIn(Long userId, Collection<Long> recipeIds);

    boolean existsByIdUserIdAndIdRecipeId(Long userId, Long recipeId);
}
