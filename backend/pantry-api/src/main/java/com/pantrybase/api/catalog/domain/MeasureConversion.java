package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Represents a conversion factor between a unit and its canonical unit.
 *
 * <p>Each MeasureConversion links a non-canonical unit to its corresponding canonical unit with a conversion factor.</p>
 */
@Entity
@Table(name = "measure_conversions")
public class MeasureConversion {
    @Id
    @Column(name = "unit_code")
    private String unitCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canonical_unit_code", nullable = false)
    private Unit canonicalUnit;

    @Column(name = "conversion_factor", nullable = false)
    private BigDecimal conversionFactor;

    public MeasureConversion() {
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public Unit getCanonicalUnit() {
        return canonicalUnit;
    }

    public void setCanonicalUnit(Unit canonicalUnit) {
        this.canonicalUnit = canonicalUnit;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public void setConversionFactor(BigDecimal conversionFactor) {
        this.conversionFactor = conversionFactor;
    }
}
