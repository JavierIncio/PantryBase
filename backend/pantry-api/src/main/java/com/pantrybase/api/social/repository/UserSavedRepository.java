package com.pantrybase.api.social.repository;

import com.pantrybase.api.social.domain.UserSaved;
import com.pantrybase.api.social.domain.UserSavedId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Reads and writes the recipes a user saved for later. */
public interface UserSavedRepository extends JpaRepository<UserSaved, UserSavedId> {

    List<UserSaved> findAllByIdUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSaved> findAllByIdUserIdAndIdRecipeIdIn(Long userId, Collection<Long> recipeIds);

    boolean existsByIdUserIdAndIdRecipeId(Long userId, Long recipeId);
}
