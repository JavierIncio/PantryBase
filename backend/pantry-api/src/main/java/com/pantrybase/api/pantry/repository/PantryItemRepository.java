package com.pantrybase.api.pantry.repository;

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
}