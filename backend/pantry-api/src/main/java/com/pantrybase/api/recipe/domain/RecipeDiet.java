package com.pantrybase.api.recipe.domain;

import com.pantrybase.api.user.domain.Diet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A restrictive diet a recipe satisfies, derived from its per-serving nutrients.
 *
 * <p>Materialised at recipe creation/refresh time, like {@link RecipeAllergen}, so
 * filtering by diet is a single indexed lookup. Only restrictive diets are stored:
 * {@link Diet#BALANCED} is the absence of a filter, never a tag.</p>
 */
@Entity
@Table(
        name = "recipe_diets",
        indexes = @Index(name = "idx_recipe_diets_diet", columnList = "diet")
)
@IdClass(RecipeDietId.class)
public class RecipeDiet {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "diet", nullable = false, length = 16)
    private Diet diet;

    public RecipeDiet() {}

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Diet getDiet() { return diet; }
    public void setDiet(Diet diet) { this.diet = diet; }
}
