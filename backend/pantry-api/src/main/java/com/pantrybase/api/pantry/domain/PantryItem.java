package com.pantrybase.api.pantry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a user has of one ingredient, and where they keep it.
 *
 * <p>One row per (user, ingredient) rather than per physical container: two bottles of
 * milk are one line of an inventory, and a row per bottle would make every read an
 * aggregation and leave "which row am I editing" ambiguous. {@link #location} and
 * {@link #note} therefore describe the ingredient, not a container.</p>
 *
 * <p>{@link #quantity} is always in the unit {@link #unitCode} names, and that unit is
 * immutable: it is the one the catalog resolves the ingredient to, and changing it later
 * would silently reinterpret every audit entry ever written against it. Moving the
 * quantity is the job of an adjustment, which leaves a record behind.</p>
 */
@Entity
@Table(name = "pantry_items")
public class PantryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private Long ingredientId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, updatable = false)
    private String unitCode;

    @Column(name = "location")
    private String location;

    @Column(name = "note")
    private String note;

    /**
     * Incremented by Hibernate on every update.
     *
     * <p>A quantity is moved by more than one actor: a cooking session deducts stock
     * automatically in H5 while the user may be editing the same line in another tab.
     * Whoever loaded the row first must not have their change overwritten by a later
     * writer working from stale numbers.</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PantryItem() {
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

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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