package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Ingredients that belong to a recipe. */
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    List<RecipeIngredient> findByRecipeIdOrderByPositionAsc(Long recipeId);

    @Query("DELETE FROM RecipeIngredient ri WHERE ri.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);

    @Query("""
            SELECT ri FROM RecipeIngredient ri
            WHERE ri.recipeId = :recipeId AND ri.ingredientId = :ingredientId
            """)
    Optional<RecipeIngredient> findByRecipeIdAndIngredientId(
            @Param("recipeId") Long recipeId,
            @Param("ingredientId") Long ingredientId);
}