package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.RecipeNutrients;
import org.springframework.data.jpa.repository.JpaRepository;

/** Nutrient profile per serving, one per recipe. */
public interface RecipeNutrientsRepository extends JpaRepository<RecipeNutrients, Long> {
    // findById(recipeId) is enough
}