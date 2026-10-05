package com.pantrybase.api.pantry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One movement of stock, in the ledger that explains a pantry item's quantity.
 *
 * <p>{@link #deltaQuantity} is signed, so an item's entries sum to its current
 * quantity. That identity is the point of the table: it turns "why does it say 300 g"
 * into a question with an answer, and it can be asserted in a test rather than trusted.</p>
 *
 * <p>There is deliberately no unit column. An item's unit never changes after creation,
 * so a copy of it here could only ever drift away from the item it belongs to without
 * anything detecting it, and reading it would raise the question of which one wins.</p>
 *
 * <p>Not written by H3-1: the table ships with the item so that the audit trail is part
 * of the original schema rather than a later addition to a live one. Entries are written
 * from H3-2, the block that introduces the adjustment endpoint.</p>
 */
@Entity
@Table(name = "consumption_entries")
public class ConsumptionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pantry_item_id", nullable = false, updatable = false)
    private Long pantryItemId;

    /**
     * Denormalized from the item so a history can be read without joining it, and so a
     * movement always has an owner even for rows whose item is being removed.
     */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "delta_quantity", nullable = false, updatable = false)
    private BigDecimal deltaQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private ConsumptionOrigin origin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ConsumptionEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPantryItemId() {
        return pantryItemId;
    }

    public void setPantryItemId(Long pantryItemId) {
        this.pantryItemId = pantryItemId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getDeltaQuantity() {
        return deltaQuantity;
    }

    public void setDeltaQuantity(BigDecimal deltaQuantity) {
        this.deltaQuantity = deltaQuantity;
    }

    public ConsumptionOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(ConsumptionOrigin origin) {
        this.origin = origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}