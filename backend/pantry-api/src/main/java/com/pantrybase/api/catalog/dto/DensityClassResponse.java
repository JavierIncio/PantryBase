package com.pantrybase.api.catalog.dto;

import java.math.BigDecimal;

/**
 * A curated density class offered to an ingredient.
 *
 * <p>{@code source} travels with the class so a caller can see how the density was
 * established before assigning it to something.</p>
 */
public record DensityClassResponse(
        String code,
        BigDecimal densityGPerMl,
        String source
) {}
