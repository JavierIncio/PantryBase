package com.pantrybase.api.catalog.dto;

import java.math.BigDecimal;

/**
 * Grams that one unit of measure weighs for a specific ingredient.
 *
 * <p>Carried inside {@link IngredientDetailResponse} rather than behind an endpoint of its
 * own, because the detail lookup is the call that captures the measures: a client asking
 * what a food weighs in a cup is asking for the same row, and splitting them would make
 * the two responses disagree after a provider refresh.</p>
 *
 * <p>Only the unit code travels here, never its name. Labels belong to {@code GET
 * /api/units}, which is a fixed table, and repeating them per ingredient would make
 * changing a label a change in two places.</p>
 *
 * <p>The scale of {@code gramPerUnit} is a storage detail and not part of this contract:
 * what the provider sends and what the column keeps do not agree on trailing zeros, so
 * compare these values numerically rather than with {@link BigDecimal#equals}.</p>
 *
 * @param unitCode    the unit the weight was measured in, e.g. {@code CUP} or {@code TBSP}
 * @param gramPerUnit grams in one unit of that measure, not in one millilitre
 */
public record IngredientMeasureResponse(String unitCode, BigDecimal gramPerUnit) {}