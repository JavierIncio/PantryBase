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
 *
 * <p>A provider line that matches nothing in the catalog is still persisted, with
 * {@code matched=false}, a null catalog id and the provider wording in
 * {@link #ingredientName}. Keeping it makes the coverage denominator the real recipe
 * rather than the subset the catalog happened to understand: a stew of twenty
 * ingredients with one matching line must score 1/20, never 100%.</p>
 *
 * <p>{@link #ingredientName} is the line's display name for <em>every</em> line, not just
 * the unmatched ones: the catalog name when matched ({@link #ingredientId} set) and the
 * provider wording otherwise. Consumers that render a line without going through the
 * catalog —cooking deductions and profile statistics— read it directly, so a matched line
 * left without a name would surface as a blank label.</p>
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

    @Column(name = "ingredient_id")
    private Long ingredientId;

    @Column(name = "ingredient_name", length = 255)
    private String ingredientName;

    @Column(name = "matched", nullable = false)
    private Boolean matched;

    @Column(name = "original_amount", precision = 14, scale = 6)
    private BigDecimal originalAmount;

    @Column(name = "original_unit_code", length = 16)
    private String originalUnitCode;

    @Column(name = "canonical_amount", precision = 14, scale = 6)
    private BigDecimal canonicalAmount;

    @Column(name = "canonical_unit_code", length = 16)
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

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public Boolean getMatched() { return matched; }
    public void setMatched(Boolean matched) { this.matched = matched; }

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