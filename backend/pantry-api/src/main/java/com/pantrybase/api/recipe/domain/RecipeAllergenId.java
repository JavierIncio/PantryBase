package com.pantrybase.api.recipe.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link RecipeAllergen}. */
public class RecipeAllergenId implements Serializable {

    private Long recipeId;
    private String allergenCode;

    public RecipeAllergenId() {}
    public RecipeAllergenId(Long recipeId, String allergenCode) {
        this.recipeId = recipeId;
        this.allergenCode = allergenCode;
    }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }
    public String getAllergenCode() { return allergenCode; }
    public void setAllergenCode(String allergenCode) { this.allergenCode = allergenCode; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeAllergenId that)) return false;
        return Objects.equals(recipeId, that.recipeId) && Objects.equals(allergenCode, that.allergenCode);
    }
    @Override public int hashCode() { return Objects.hash(recipeId, allergenCode); }
}