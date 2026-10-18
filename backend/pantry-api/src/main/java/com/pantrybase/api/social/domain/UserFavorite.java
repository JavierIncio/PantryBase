package com.pantrybase.api.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Maps a user to a recipe they marked as favorite.
 */
@Entity
@Table(
        name = "user_favorites",
        indexes = {@Index(name = "idx_user_favorites_user", columnList = "user_id")}
)
public class UserFavorite {

    @EmbeddedId
    private UserFavoriteId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserFavorite() {}

    public UserFavorite(UserFavoriteId id) {
        this.id = id;
    }

    public UserFavoriteId getId() {
        return id;
    }

    public void setId(UserFavoriteId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
