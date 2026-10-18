package com.pantrybase.api.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for a user favorite.
 */
@Embeddable
public class UserFavoriteId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    public UserFavoriteId() {}

    public UserFavoriteId(Long userId, Long recipeId) {
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
        if (!(o instanceof UserFavoriteId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(recipeId, that.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, recipeId);
    }
}
