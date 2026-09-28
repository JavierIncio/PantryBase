package com.pantrybase.api.catalog.dto;

import java.math.BigDecimal;

/** Represents the response for a unit conversion request. */
public record UnitConversionResponse(BigDecimal amount, String unitCode) {}
