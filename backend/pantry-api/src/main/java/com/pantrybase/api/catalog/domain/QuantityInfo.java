package com.pantrybase.api.catalog.domain;

import java.math.BigDecimal;

/** Represents the quantity information of a product, including the amount and unit code. */
public record QuantityInfo(BigDecimal amount, String unitCode) {}
