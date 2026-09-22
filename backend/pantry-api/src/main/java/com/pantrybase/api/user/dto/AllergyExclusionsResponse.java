package com.pantrybase.api.user.dto;

import java.util.Set;

/**
 * Full representation of the allergy-exclusions sub-resource.
 * Returns the resolved allergens (id, code, name), not just the codes,
 * so clients can render labels without an extra lookup.
 */
public record AllergyExclusionsResponse(Set<AllergenResponse> exclusions) {
}
