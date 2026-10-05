package com.pantrybase.api.pantry.dto;

import jakarta.validation.constraints.Size;

/**
 * Replaces the editable fields of a pantry item.
 *
 * <p>No quantity field, on purpose. A quantity change is an adjustment and produces an
 * audit entry; keeping the field out of this contract is what makes bypassing the audit
 * impossible rather than merely discouraged.</p>
 *
 * <p>Replaces rather than merges: both fields are optional and an omitted one is cleared.
 * That is the honest reading of an update of a resource's only two editable attributes,
 * and a merge would leave the caller unable to tell whether an omitted field means
 * "leave it" or "I forgot".</p>
 */
public record UpdatePantryItemRequest(
        @Size(max = 100) String location,
        @Size(max = 255) String note
) {}