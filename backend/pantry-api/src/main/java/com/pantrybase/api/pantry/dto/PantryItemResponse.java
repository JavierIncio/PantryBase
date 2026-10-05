package com.pantrybase.api.pantry.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A pantry line as the inventory view needs it.
 *
 * <p>Carries the ingredient's name and category because the client renders one section
 * per category and labels every line with the food's name; a response of bare ids would
 * force a second request per item to draw the list.</p>
 *
 * <p>{@code version} travels with it because the server keeps a count of how many times
 * the line has been written, which is what turns two actors touching the same quantity
 * into a reported conflict rather than a silent overwrite. Nothing asks the client to
 * send it back: the check happens inside the write, between reading the line and saving
 * it, so a caller that adjusts stock is not required to have loaded the item first.</p>
 */
public record PantryItemResponse(
        long id,
        long fdcId,
        String name,
        String category,
        BigDecimal quantity,
        String unitCode,
        String location,
        String note,
        long version,
        Instant updatedAt
) {}