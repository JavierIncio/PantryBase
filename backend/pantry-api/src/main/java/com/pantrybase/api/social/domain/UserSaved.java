package com.pantrybase.api.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Maps a user to a recipe they saved for later.
 */
@Entity
@Table(
        name = "user_saved",
        indexes = {@Index(name = "idx_user_saved_user", columnList = "user_id")}
)
public class UserSaved {

    @EmbeddedId
    private UserSavedId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserSaved() {}

    public UserSaved(UserSavedId id) {
        this.id = id;
    }

    public UserSavedId getId() {
        return id;
    }

    public void setId(UserSavedId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
