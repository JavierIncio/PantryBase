package com.pantrybase.api.catalog.domain;

import java.math.BigDecimal;

public interface UnitConverter {
    /** Normalize for a given category: 'CUP' → factor → 'ML'. */
    QuantityInfo toCanonical(BigDecimal amount, String unitCode);
    /** Converts unit ↔ unit from the same category or different categories with density. */
    QuantityInfo convert(BigDecimal amount, String fromCode, String toCode, String ingredientCategory);
}
