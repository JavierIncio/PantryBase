package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a unit of measurement in the catalog.
 *
 * <p>canonical = true marks the anchors (GRAM/ML/UNIT) that every MeasureConversion points at</p>
 */
@Entity
@Table(name = "units")
public class Unit {
    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "canonical", nullable = false)
    private boolean isCanonical;

    @Enumerated(EnumType.STRING)
    private UnitCategory category;

    public Unit() {}

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCanonical() {
        return isCanonical;
    }

    public void setCanonical(boolean canonical) {
        isCanonical = canonical;
    }

    public UnitCategory getCategory() {
        return category;
    }

    public void setCategory(UnitCategory category) {
        this.category = category;
    }
}
