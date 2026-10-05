package com.pantrybase.api.pantry.repository;

import com.pantrybase.api.catalog.domain.UnitCategory;
import com.pantrybase.api.pantry.domain.PantryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    /**
     * Every read and write of a pantry item filters on the owner, never on the id alone.
     *
     * <p>The methods exist per user rather than as a generic {@code findById} call site
     * precisely so that scoping cannot be forgotten at the call site: there is no
     * id-only finder to reach for by mistake.</p>
     */
    Optional<PantryItem> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndIngredientId(Long userId, Long ingredientId);

    /**
     * The whole pantry with its ingredient, in one query.
     *
     * <p>Category first because the client renders one section per category; name next so
     * the order inside a section is stable instead of depending on insertion order.</p>
     *
     * <p>Joined rather than looked up per line: the list is drawn in full on every visit,
     * and resolving each ingredient separately would make one request into N+1. The item
     * holds the ingredient id, not a reference, so the join is spelled out here.</p>
     */
    @Query("""
            SELECT p, i FROM PantryItem p
              JOIN Ingredient i ON i.id = p.ingredientId
             WHERE p.userId = :userId
             ORDER BY i.category, i.name
            """)
    List<Object[]> findAllWithIngredientByUserId(@Param("userId") Long userId);

    /**
     * Lines counted by ingredient category and by storage unit kind, in one query.
     *
     * <p>The first GROUP BY in the project, and the reason the two breakdowns of the
     * summary are read from the same rows: two separate count queries can disagree, and the
     * client would have no way to tell which of the two numbers to believe.</p>
     *
     * <p>Grouped by both axes at once rather than once per axis on purpose. The result is
     * a grid, which the service folds into each axis separately: a few lines of Java in
     * exchange for one round trip whose answer cannot already be stale.</p>
     *
     * <p>Neither join can drop a line. {@code unit_code} carries a foreign key onto
     * {@code units (code)}, so a pantry line can only name a unit the catalog has, and the
     * ingredient join goes through the primary key.</p>
     *
     * <p>No ORDER BY: the order the summary is shown in is a presentation choice, and
     * {@code category} is nullable, so settling it here would mean guessing how absent
     * values are sorted. The service sorts both axes explicitly instead.</p>
     *
     * @return rows of {@code [category, unitCategory, lines, linesAtZero]}, where
     *         {@code category} is the provider's free text and is nullable, and
     *         {@code unitCategory} is a {@link UnitCategory}
     */
    @Query("""
            SELECT i.category, u.category, COUNT(p),
                   SUM(CASE WHEN p.quantity = 0 THEN 1 ELSE 0 END)
              FROM PantryItem p
              JOIN Ingredient i ON i.id = p.ingredientId
              JOIN Unit u ON u.code = p.unitCode
             WHERE p.userId = :userId
             GROUP BY i.category, u.category
            """)
    List<Object[]> countGroupedByCategoryAndUnitCategoryByUserId(@Param("userId") Long userId);
}