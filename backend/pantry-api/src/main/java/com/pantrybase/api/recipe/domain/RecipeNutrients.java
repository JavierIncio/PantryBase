package com.pantrybase.api.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Nutrient profile per serving, computed at materialisation from the ingredient
 * canonical amounts and the USDA nutrient data.
 */
@Entity
@Table(name = "recipe_nutrients")
public class RecipeNutrients {

    @Id
    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "energy_kcal", precision = 10, scale = 3)
    private BigDecimal energyKcal;

    @Column(name = "protein_g", precision = 10, scale = 3)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 10, scale = 3)
    private BigDecimal fatG;

    @Column(name = "carbs_g", precision = 10, scale = 3)
    private BigDecimal carbsG;

    public RecipeNutrients() {}

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }

    public BigDecimal getEnergyKcal() { return energyKcal; }
    public void setEnergyKcal(BigDecimal energyKcal) { this.energyKcal = energyKcal; }

    public BigDecimal getProteinG() { return proteinG; }
    public void setProteinG(BigDecimal proteinG) { this.proteinG = proteinG; }

    public BigDecimal getFatG() { return fatG; }
    public void setFatG(BigDecimal fatG) { this.fatG = fatG; }

    public BigDecimal getCarbsG() { return carbsG; }
    public void setCarbsG(BigDecimal carbsG) { this.carbsG = carbsG; }
}