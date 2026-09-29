package com.pantrybase.api.catalog.repository;

import com.pantrybase.api.catalog.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByFdcId(Long fdcId);

    /**
     * Inserts the ingredient only if no row with the same fdc_id exists.
     *
     * <p>Race-safe idempotent insert: the uniqueness constraint is the
     * arbiter of a concurrent double-persist, and the statement never
     * fails on conflict — callers must re-read with
     * {@link #findByFdcId(Long)} to obtain the winning row.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO ingredients (fdc_id, name, category, energy_kcal, protein_g, fat_g, carbs_g)
                        VALUES (:fdcId, :name, :category, :energyKcal, :proteinG, :fatG, :carbsG)
                        ON CONFLICT (fdc_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("fdcId") Long fdcId,
                        @Param("name") String name,
                        @Param("category") String category,
                        @Param("energyKcal") Double energyKcal,
                        @Param("proteinG") Double proteinG,
                        @Param("fatG") Double fatG,
                        @Param("carbsG") Double carbsG);
}
