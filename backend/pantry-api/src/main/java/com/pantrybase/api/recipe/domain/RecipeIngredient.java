package com.pantrybase.api.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * One ingredient line inside a recipe, stored with both the original quantity/unit
 * (as the provider wrote it) and the canonical quantity/unit (what the pantry stores).
 *
 * <p>The canonical pair is produced by {@link com.pantrybase.api.catalog.domain.UnitConverter#toStorageUnit}
 * at materialisation time, so coverage can compare apples to apples.</p>
 */
@Entity
@Table(
        name = "recipe_ingredients",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipe_ingredients_recipe_ingredient", columnNames = { "recipe_id", "ingredient_id" }),
        indexes = {
                @Index(name = "idx_recipe_ingredients_recipe", columnList = "recipe_id"),
                @Index(name = "idx_recipe_ingredients_ingredient", columnList = "ingredient_id")
        }
)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 6)
    private BigDecimal originalAmount;

    @Column(name = "original_unit_code", nullable = false, length = 16)
    private String originalUnitCode;

    @Column(name = "canonical_amount", nullable = false, precision = 14, scale = 6)
    private BigDecimal canonicalAmount;

    @Column(name = "canonical_unit_code", nullable = false, length = 16)
    private String canonicalUnitCode;

    @Column(name = "position", nullable = false)
    private Integer position;

    public RecipeIngredient() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public String getOriginalUnitCode() { return originalUnitCode; }
    public void setOriginalUnitCode(String originalUnitCode) { this.originalUnitCode = originalUnitCode; }

    public BigDecimal getCanonicalAmount() { return canonicalAmount; }
    public void setCanonicalAmount(BigDecimal canonicalAmount) { this.canonicalAmount = canonicalAmount; }

    public String getCanonicalUnitCode() { return canonicalUnitCode; }
    public void setCanonicalUnitCode(String canonicalUnitCode) { this.canonicalUnitCode = canonicalUnitCode; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}