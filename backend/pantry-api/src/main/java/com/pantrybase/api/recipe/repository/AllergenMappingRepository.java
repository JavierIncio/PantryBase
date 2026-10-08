package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.AllergenMapping;
import com.pantrybase.api.recipe.domain.AllergenMappingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/** Curated ingredient -> allergen mapping. */
public interface AllergenMappingRepository extends JpaRepository<AllergenMapping, AllergenMappingId> {

    List<AllergenMapping> findByIngredientId(Long ingredientId);

    @Query("SELECT DISTINCT am.allergenCode FROM AllergenMapping am WHERE am.ingredientId IN :ingredientIds")
    Set<String> findAllergenCodesByIngredientIds(@Param("ingredientIds") List<Long> ingredientIds);
}