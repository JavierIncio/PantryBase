package com.pantrybase.api.pantry.dto;

import java.util.List;

/**
 * The size of a user's inventory, counted two ways.
 *
 * <p>A read-only aggregate, deliberately not a cache of one: {@link #totalItems} is the sum
 * of {@link #byCategory} rather than a second count, because two queries for the same
 * number can disagree and the client would have no way to tell which one to believe.</p>
 *
 * <p>{@code zeroItems} is counted separately instead of being left out, and the total
 * includes those lines. Running out of the last of something is an ordinary event that the
 * schema represents on purpose, so a total that quietly dropped those lines would report
 * fewer things than the inventory view is showing on screen. Publishing the count as well
 * hands the decision to the user instead of hiding it in an omission.</p>
 *
 * <p>Both breakdowns describe the same set of lines from one query, so the two axes cannot
 * fall out of step with each other.</p>
 *
 * @param totalItems lines in the pantry, zero-quantity ones included
 * @param zeroItems  how many of those lines are at exactly zero
 * @param byCategory lines per ingredient category, largest first
 * @param byUnitType lines per kind of storage unit
 */
public record PantrySummaryResponse(
        long totalItems,
        long zeroItems,
        List<CategoryShareResponse> byCategory,
        List<UnitShareResponse> byUnitType
) {}