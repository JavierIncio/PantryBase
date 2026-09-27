package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Represents the density of an ingredient category in grams per milliliter.
 *
 * <p>The density is used to convert between weight and volume measurements for each ingredient category.</p>
 */
@Entity
@Table(name = "ingredient_densities")
public class IngredientDensity {

    @Id
    @Column(name = "ingredient_category")
    private String ingredientCategory;

    @Column(name = "density_g_per_ml", nullable = false)
    private BigDecimal densityGPerMl;

    public IngredientDensity() {
    }

    public String getIngredientCategory() {
        return ingredientCategory;
    }

    public void setIngredientCategory(String ingredientCategory) {
        this.ingredientCategory = ingredientCategory;
    }

    public BigDecimal getDensityGPerMl() {
        return densityGPerMl;
    }

    public void setDensityGPerMl(BigDecimal densityGPerMl) {
        this.densityGPerMl = densityGPerMl;
    }
}
