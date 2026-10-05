package com.pantrybase.api.pantry.dto;

import com.pantrybase.api.catalog.domain.UnitCategory;

/**
 * How many lines are kept in each kind of unit.
 *
 * <p>Answers "what is this pantry made of, storage-wise": a pantry of weights converts to
 * anything, a pantry of volumes needs a density for every ingredient it holds, and a
 * pantry of counts needs neither. That distinction is the reason the split exists, and it
 * is also why this cannot be derived from the unit codes a client is shown.</p>
 *
 * <p>The category is the catalog's own {@link UnitCategory} rather than a set of codes,
 * so a client does not have to hold the mapping from GRAM/KG/LB to "weight" to render it.</p>
 *
 * @param category the kind of unit the lines are stored in
 * @param items    lines stored that way
 */
public record UnitShareResponse(UnitCategory category, long items) {}