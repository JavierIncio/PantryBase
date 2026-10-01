package com.pantrybase.api.catalog.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link IngredientMeasure}.
 *
 * <p>It is a separate class because {@link IngredientMeasure} combines an assigned
 * relationship id with an assigned basic id. The {@code ingredient} field is typed as
 * {@link Ingredient} because that is the type of the corresponding primary key field;
 * equals/hashCode are mandatory so the id can be compared across Hibernate proxies.</p>
 */
public class IngredientMeasureId implements Serializable {

    private Ingredient ingredient;
    private String unitCode;

    public IngredientMeasureId() {
    }

    public IngredientMeasureId(Ingredient ingredient, String unitCode) {
        this.ingredient = ingredient;
        this.unitCode = unitCode;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngredientMeasureId that = (IngredientMeasureId) o;
        return Objects.equals(ingredient, that.ingredient)
                && Objects.equals(unitCode, that.unitCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingredient, unitCode);
    }
}