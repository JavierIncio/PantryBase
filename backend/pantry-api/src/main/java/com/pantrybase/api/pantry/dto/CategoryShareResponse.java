package com.pantrybase.api.pantry.dto;

/**
 * How many lines fall under one ingredient category.
 *
 * <p>Counts lines, not quantities: a pantry holding two kilos of rice and three eggs has
 * no total mass to report, because grams and eggs cannot be added into a number that means
 * anything. A line is the one unit the two have in common.</p>
 *
 * <p>{@code category} is never null here. The column in the catalog is, so a line whose
 * ingredient arrived without one is reported under a name rather than under an absent
 * value: the client would otherwise have to invent a heading, and two clients would invent
 * different ones.</p>
 *
 * @param category the provider's category, or the fallback name for a missing one
 * @param items    lines under that category
 */
public record CategoryShareResponse(String category, long items) {}