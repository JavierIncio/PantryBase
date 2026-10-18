package com.pantrybase.api.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for a saved recipe.
 */
@Embeddable
public class UserSavedId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    public UserSavedId() {}

    public UserSavedId(Long userId, Long recipeId) {
        this.userId = userId;
        this.recipeId = recipeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSavedId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(recipeId, that.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, recipeId);
    }
}
