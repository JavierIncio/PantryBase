package com.pantrybase.api.user.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Stores the optional preferences associated with a user.
 *
 * <p>A preferences row is created lazily when the user first saves preferences,
 * rather than during registration. Until then, the application uses the default
 * domain values without persisting a row.</p>
 *
 * <p>The entity uses the user's ID as both its primary key and foreign key,
 * enforcing a one-to-one relationship with {@link User} through a shared
 * primary key.</p>
 *
 * <p>{@link Persistable} is implemented because the identifier is assigned
 * before persistence and is therefore not a reliable indicator of whether
 * the entity is new. The {@link #isNew()} implementation uses the creation
 * timestamp to distinguish new entities from existing ones.</p>
 * */
@Entity
@Table(name = "user_preferences")
public class UserPreferences implements Persistable<Long> {
    @Id
    private Long userId;

    @MapsId("userId")
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "filter_mode")
    private FilterMode filterMode = FilterMode.LAX;

    @Column(name = "coverage_threshold")
    private Short coverageThreshold = 80;

    @Enumerated(EnumType.STRING)
    @Column(name = "diet")
    private Diet diet = Diet.BALANCED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPreferences() {}

    @Override
    public Long getId() { return userId; }

    @Override
    @Transient
    public boolean isNew() { return createdAt == null; }

    public void setId(Long userId) { this.userId = userId; }

    public User getUser() { return user; }

    public void setUser(User user) {
        this.user = user;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    public Short getCoverageThreshold() {
        return coverageThreshold;
    }

    public void setCoverageThreshold(Short coverageThreshold) {
        this.coverageThreshold = coverageThreshold;
    }

    public Diet getDiet() {
        return diet;
    }

    public void setDiet(Diet diet) {
        this.diet = diet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}