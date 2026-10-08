package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.RecipeAllergen;
import com.pantrybase.api.recipe.domain.RecipeAllergenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Derived allergens per recipe, materialised for fast filtering. */
public interface RecipeAllergenRepository extends JpaRepository<RecipeAllergen, RecipeAllergenId> {

    List<RecipeAllergen> findByRecipeId(Long recipeId);

    /**
     * Returns only the allergen codes of a recipe.
     *
     * <p>Explicit query rather than a derived one: the caller wants plain strings for
     * filtering and comparison against the user's exclusions, and a derived finder would
     * hand back whole entities for the service to map again on every row.</p>
     */
    @Query("SELECT ra.allergenCode FROM RecipeAllergen ra WHERE ra.recipeId = :recipeId")
    Set<String> findAllergenCodesByRecipeId(@Param("recipeId") Long recipeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RecipeAllergen ra WHERE ra.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);

    @Query("SELECT DISTINCT ra.recipeId FROM RecipeAllergen ra WHERE ra.allergenCode IN :codes")
    Set<Long> findRecipeIdsByAllergenCodes(@Param("codes") Set<String> codes);
}