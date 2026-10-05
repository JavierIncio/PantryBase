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
 * <p>{@code version} travels with it so a later write can tell the server which revision
 * the client was looking at, which is what makes a lost update reportable instead of
 * silently applied.</p>
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