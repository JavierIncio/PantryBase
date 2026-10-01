package com.pantrybase.api.catalog.domain;

/**
 * A household measure of an ingredient, normalized to this catalog's units.
 *
 * <p>Provider-agnostic: household measures described by the external provider
 * (e.g. "cup, nf") are translated by the adapter into a {@code unitCode} of
 * this catalog plus the grams that a single unit of that measure weighs.</p>
 *
 * @param unitCode     code of a volume unit of this catalog (TSP, TBSP, CUP, ...)
 * @param gramPerUnit  grams of one whole unit of the measure (never zero)
 */
public record Portion(String unitCode, Double gramPerUnit) {
}