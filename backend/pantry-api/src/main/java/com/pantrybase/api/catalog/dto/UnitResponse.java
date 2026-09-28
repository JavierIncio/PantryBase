package com.pantrybase.api.catalog.dto;

import com.pantrybase.api.catalog.domain.UnitCategory;

/**  Represents a unit of measurement in the catalog. */
public record UnitResponse(String code, String name, UnitCategory category, boolean canonical) {}
