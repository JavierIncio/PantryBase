package com.pantrybase.api.recipe.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link AllergenMapping}. */
public class AllergenMappingId implements Serializable {

    private Long ingredientId;
    private String allergenCode;

    public AllergenMappingId() {}
    public AllergenMappingId(Long ingredientId, String allergenCode) {
        this.ingredientId = ingredientId;
        this.allergenCode = allergenCode;
    }

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
    public String getAllergenCode() { return allergenCode; }
    public void setAllergenCode(String allergenCode) { this.allergenCode = allergenCode; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AllergenMappingId that)) return false;
        return Objects.equals(ingredientId, that.ingredientId) && Objects.equals(allergenCode, that.allergenCode);
    }
    @Override public int hashCode() { return Objects.hash(ingredientId, allergenCode); }
}