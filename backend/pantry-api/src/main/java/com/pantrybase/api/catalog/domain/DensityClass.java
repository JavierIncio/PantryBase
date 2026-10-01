package com.pantrybase.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Density of one of our own density classes, in grams per milliliter.
 *
 * <p>The code is deliberately ours and not the provider's category: the USDA FDC category
 * is top level only, and "Dairy and Egg Products" holds both milk at about 1.03 g/ml and
 * cheddar at about 0.40 g/ml, so a density per provider category would be confidently
 * wrong. A class is narrow enough for its members to genuinely share a density, and an
 * ingredient opts into one explicitly through {@link Ingredient#getDensityClass()}.</p>
 */
@Entity
@Table(name = "density_classes")
public class DensityClass {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "density_g_per_ml", nullable = false)
    private BigDecimal densityGPerMl;

    /**
     * How this density was established, for example "FDC fdcId 171265: 1 cup = 244 g".
     *
     * <p>Optional and not a constraint: the point is auditability, so that a class
     * added later can be traced back to a measurement rather than appearing as a
     * bare number nobody can defend.</p>
     */
    @Column(name = "source")
    private String source;

    public DensityClass() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getDensityGPerMl() {
        return densityGPerMl;
    }

    public void setDensityGPerMl(BigDecimal densityGPerMl) {
        this.densityGPerMl = densityGPerMl;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
