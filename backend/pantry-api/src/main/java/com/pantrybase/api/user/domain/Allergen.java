package com.pantrybase.api.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents the allergens in the system identified by a unique code defined by the EU convention.
 */
@Entity
@Table(name = "allergens")
public class Allergen {
    @Id
    private Short id; // seeded by V3
    private String code;
    private String name;

    public Allergen() {
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

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
}
