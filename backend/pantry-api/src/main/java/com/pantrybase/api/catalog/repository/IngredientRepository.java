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
            INSERT INTO ingredients (fdc_id, name, category, data_type, energy_kcal, protein_g, fat_g,
                                     carbs_g, synced_at)
                        VALUES (:fdcId, :name, :category, :dataType, :energyKcal, :proteinG, :fatG,
                                :carbsG, now())
                        ON CONFLICT (fdc_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("fdcId") Long fdcId,
                        @Param("name") String name,
                        @Param("category") String category,
                        @Param("dataType") String dataType,
                        @Param("energyKcal") Double energyKcal,
                        @Param("proteinG") Double proteinG,
                        @Param("fatG") Double fatG,
                        @Param("carbsG") Double carbsG);

    /**
     * Overwrites the fields the provider owns and stamps the read as successful.
     *
     * <p>{@code density_class} is intentionally absent from the SET list: it is a
     * curated claim of ours, so a provider refresh must not be able to erase a
     * classification a human made. The same reasoning applies to
     * {@code ingredient_measures}, which lives in another table and is only ever
     * added to. Both would silently lose a value nobody can reconstruct.</p>
     *
     * <p>What this does fix is the opposite problem: before, an insert-only
     * materialization meant a corrected nutrient value at the provider could
     * never reach the local catalog.</p>
     *
     * @return rows updated, zero when the ingredient is not materialized yet
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE ingredients
               SET name = :name,
                   category = :category,
                   data_type = :dataType,
                   energy_kcal = :energyKcal,
                   protein_g = :proteinG,
                   fat_g = :fatG,
                   carbs_g = :carbsG,
                   synced_at = now()
             WHERE fdc_id = :fdcId
            """, nativeQuery = true)
    int refreshProviderFields(@Param("fdcId") Long fdcId,
                              @Param("name") String name,
                              @Param("category") String category,
                              @Param("dataType") String dataType,
                              @Param("energyKcal") Double energyKcal,
                              @Param("proteinG") Double proteinG,
                              @Param("fatG") Double fatG,
                              @Param("carbsG") Double carbsG);
}
