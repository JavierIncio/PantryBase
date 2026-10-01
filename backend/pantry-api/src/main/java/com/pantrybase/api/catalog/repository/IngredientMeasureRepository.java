package com.pantrybase.api.catalog.repository;

import com.pantrybase.api.catalog.domain.IngredientMeasure;
import com.pantrybase.api.catalog.domain.IngredientMeasureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IngredientMeasureRepository
        extends JpaRepository<IngredientMeasure, IngredientMeasureId> {

    Optional<IngredientMeasure> findByIngredientIdAndUnitCode(Long ingredientId, String unitCode);

    List<IngredientMeasure> findByIngredientId(Long ingredientId);

    /**
     * Inserts the measure only if the ingredient does not have that unit yet.
     *
     * <p>Mirrors {@link com.pantrybase.api.catalog.repository.IngredientRepository#insertIfAbsent}:
     * idempotent and race-safe, the composite primary key is the arbiter on conflict.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO ingredient_measures (ingredient_id, unit_code, gram_per_unit)
                        VALUES (:ingredientId, :unitCode, :gramPerUnit)
                        ON CONFLICT (ingredient_id, unit_code) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("ingredientId") Long ingredientId,
                        @Param("unitCode") String unitCode,
                        @Param("gramPerUnit") BigDecimal gramPerUnit);
}