package com.pantrybase.api.cooking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One time a user cooks one recipe, from "Start cooking" to completion or abandonment.
 *
 * <p>Holds an owner and a recipe as plain ids rather than associations. The recipe is
 * shared catalog data, not a relation owned by the user, and a session is only ever
 * read and written through its owner, so a real {@code @ManyToOne} would force every
 * read to answer a question the caller never asked.</p>
 *
 * <p>{@code startedAt} is stamped on insert like a row-level default, so a code path
 * that builds a bare {@code CookingSession} does not need to remember the column is
 * NOT NULL — the same shape of fix as {@code Recipe.refreshedAt}.</p>
 */
@Entity
@Table(name = "cooking_sessions",
        indexes = @Index(name = "idx_cooking_sessions_user", columnList = "user_id"))
public class CookingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false, updatable = false)
    private Long recipeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CookingSessionStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CookingSession() {
    }

    @PrePersist
    void ensureStartedStamp() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
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

    public CookingSessionStatus getStatus() {
        return status;
    }

    public void setStatus(CookingSessionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}