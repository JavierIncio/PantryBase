package com.pantrybase.api.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Records that a user finished cooking a recipe.
 *
 * <p>A row is written only when a cooking session reaches {@code COMPLETED}, inside the same
 * transaction, and the unique {@code cooking_session_id} makes a double completion idempotent: the
 * database, not the caller, guarantees history never diverges from completed sessions.</p>
 */
@Entity
@Table(
        name = "recipe_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_history_session", columnNames = "cooking_session_id"),
        indexes = {
                @Index(name = "idx_recipe_history_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_recipe_history_recipe", columnList = "recipe_id")
        }
)
public class RecipeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false, updatable = false)
    private Long recipeId;

    @Column(name = "cooking_session_id", nullable = false, updatable = false)
    private Long cookingSessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RecipeHistory() {}

    public RecipeHistory(Long userId, Long recipeId, Long cookingSessionId) {
        this.userId = userId;
        this.recipeId = recipeId;
        this.cookingSessionId = cookingSessionId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getCookingSessionId() {
        return cookingSessionId;
    }

    public void setCookingSessionId(Long cookingSessionId) {
        this.cookingSessionId = cookingSessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
