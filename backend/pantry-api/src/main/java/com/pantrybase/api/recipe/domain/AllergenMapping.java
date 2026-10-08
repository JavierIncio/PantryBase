package com.pantrybase.api.recipe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Curated mapping from an ingredient to an allergen code.
 *
 * <p>This table is seeded by migration and maintained by the team; it is NOT derived
 * from the provider, because TheMealDB does not expose structured allergens.</p>
 */
@Entity
@Table(name = "allergen_mapping")
@IdClass(AllergenMappingId.class)
public class AllergenMapping {

    @Id
    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Id
    @Column(name = "allergen_code", nullable = false, length = 32)
    private String allergenCode;

    public AllergenMapping() {}

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

    public String getAllergenCode() { return allergenCode; }
    public void setAllergenCode(String allergenCode) { this.allergenCode = allergenCode; }
}