package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Persistence model of a catalog ingredient.
 *
 * <p>Keeps the surrogate id separate from the provider's fdcId so pantry and
 * recipes reference a stable id that does not depend on the external catalog.</p>
 */
@Entity
@Table(name = "ingredients")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fdc_id", nullable = false, unique = true)
    private Long fdcId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    /**
     * Density class this ingredient belongs to, or {@code null} when none has been curated.
     *
     * <p>Curated by us and never written by materialization, so it survives provider
     * lookups. It is what lets an ingredient with no usable measure still convert: the
     * provider's {@link #category} cannot serve that purpose because it is too coarse
     * to carry a density.</p>
     */
    @Column(name = "density_class")
    private String densityClass;

    @Column(name = "energy_kcal")
    private Double energyKcal;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "carbs_g")
    private Double carbsG;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Ingredient() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFdcId() {
        return fdcId;
    }

    public void setFdcId(Long fdcId) {
        this.fdcId = fdcId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDensityClass() {
        return densityClass;
    }

    public void setDensityClass(String densityClass) {
        this.densityClass = densityClass;
    }

    public Double getEnergyKcal() {
        return energyKcal;
    }

    public void setEnergyKcal(Double energyKcal) {
        this.energyKcal = energyKcal;
    }

    public Double getProteinG() {
        return proteinG;
    }

    public void setProteinG(Double proteinG) {
        this.proteinG = proteinG;
    }

    public Double getFatG() {
        return fatG;
    }

    public void setFatG(Double fatG) {
        this.fatG = fatG;
    }

    public Double getCarbsG() {
        return carbsG;
    }

    public void setCarbsG(Double carbsG) {
        this.carbsG = carbsG;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}