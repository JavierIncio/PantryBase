package com.pantrybase.api.recipe.repository;

import com.pantrybase.api.recipe.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for recipes. Every read that can be scoped by source uses an explicit
 * method rather than a generic finder, so the caller cannot forget the source.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findBySourceAndExternalId(String source, String externalId);

    @Query("SELECT r FROM Recipe r WHERE r.source = :source ORDER BY r.name")
    List<Recipe> findBySourceOrderByName(@Param("source") String source);

    @Query("SELECT r FROM Recipe r WHERE r.source = :source AND LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY r.name")
    List<Recipe> searchByName(@Param("source") String source, @Param("query") String query);

    @Query("SELECT DISTINCT r FROM Recipe r JOIN RecipeIngredient ri ON ri.recipeId = r.id WHERE r.source = :source AND ri.ingredientId = :ingredientId ORDER BY r.name")
    List<Recipe> findBySourceAndIngredient(@Param("source") String source, @Param("ingredientId") Long ingredientId);

    @Query("""
            SELECT r FROM Recipe r
            WHERE r.source = :source
              AND r.id NOT IN (
                  SELECT ra.recipeId FROM RecipeAllergen ra WHERE ra.allergenCode IN :excluded
              )
            ORDER BY r.name
            """)
    List<Recipe> findBySourceExcludingAllergens(
            @Param("source") String source,
            @Param("excluded") List<String> excludedAllergens);

    boolean existsBySourceAndExternalId(String source, String externalId);
}