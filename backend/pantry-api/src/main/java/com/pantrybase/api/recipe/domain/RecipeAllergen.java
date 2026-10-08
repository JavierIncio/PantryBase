package com.pantrybase.api.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Allergens derived for a recipe by joining its ingredients with {@link AllergenMapping}.
 *
 * <p>Materialised at recipe creation/refresh time so that filtering by allergen is a
 * single indexed lookup instead of a join across three tables.</p>
 */
@Entity
@Table(
        name = "recipe_allergens",
        indexes = @Index(name = "idx_recipe_allergens_recipe", columnList = "recipe_id")
)
@IdClass(RecipeAllergenId.class)
public class RecipeAllergen {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Id
    @Column(name = "allergen_code", nullable = false, length = 32)
    private String allergenCode;

    public RecipeAllergen() {}

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public String getAllergenCode() { return allergenCode; }
    public void setAllergenCode(String allergenCode) { this.allergenCode = allergenCode; }
}