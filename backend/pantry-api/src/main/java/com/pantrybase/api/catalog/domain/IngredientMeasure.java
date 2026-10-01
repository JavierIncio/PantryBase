package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One household measure of an ingredient captured from the provider.
 *
 * <p>Records that a single unit of a volume measure (e.g. one US cup) of this
 * ingredient weighs {@code gramPerUnit} grams. It lets volume-to-weight
 * conversions use the ingredient's own measure instead of the category average.</p>
 */
@Entity
@Table(name = "ingredient_measures")
@IdClass(IngredientMeasureId.class)
public class IngredientMeasure {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Id
    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "gram_per_unit", nullable = false)
    private BigDecimal gramPerUnit;

    public IngredientMeasure() {
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public BigDecimal getGramPerUnit() {
        return gramPerUnit;
    }

    public void setGramPerUnit(BigDecimal gramPerUnit) {
        this.gramPerUnit = gramPerUnit;
    }
}