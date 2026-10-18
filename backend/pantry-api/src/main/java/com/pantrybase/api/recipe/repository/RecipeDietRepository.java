package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.RecipeDiet;
import com.pantrybase.api.recipe.domain.RecipeDietId;
import com.pantrybase.api.user.domain.Diet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Derived diet tags per recipe, materialised for fast filtering. */
public interface RecipeDietRepository extends JpaRepository<RecipeDiet, RecipeDietId> {

    @Query("SELECT rd.diet FROM RecipeDiet rd WHERE rd.recipeId = :recipeId")
    Set<Diet> findDietsByRecipeId(@Param("recipeId") Long recipeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RecipeDiet rd WHERE rd.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);

    /**
     * Ids of the recipes carrying a given diet.
     *
     * <p>Explicit query rather than a derived finder: the caller only needs the
     * ids to build a membership test, and a derived finder would hand back whole
     * entities to map again.</p>
     */
    @Query("SELECT DISTINCT rd.recipeId FROM RecipeDiet rd WHERE rd.diet = :diet")
    Set<Long> findRecipeIdsByDiet(@Param("diet") Diet diet);
}
