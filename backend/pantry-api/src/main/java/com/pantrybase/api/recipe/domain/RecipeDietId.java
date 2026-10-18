package com.pantrybase.api.recipe.domain;

import com.pantrybase.api.user.domain.Diet;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link RecipeDiet}. */
public class RecipeDietId implements Serializable {

    private Long recipeId;
    private Diet diet;

    public RecipeDietId() {}
    public RecipeDietId(Long recipeId, Diet diet) {
        this.recipeId = recipeId;
        this.diet = diet;
    }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }
    public Diet getDiet() { return diet; }
    public void setDiet(Diet diet) { this.diet = diet; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeDietId that)) return false;
        return Objects.equals(recipeId, that.recipeId) && diet == that.diet;
    }
    @Override public int hashCode() { return Objects.hash(recipeId, diet); }
}
