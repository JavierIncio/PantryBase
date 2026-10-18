package com.pantrybase.api.user.domain;

/**
 * Dietary preference of the user, and the diet tag a recipe can carry.
 *
 * <p>{@link #BALANCED} is the neutral value on both sides: as a preference it
 * means "no diet filter", and it is never derived as a tag — a recipe only
 * carries the restrictive tags it actually meets. The three restrictive values
 * are derived from the per-serving nutrient profile in the recipe module.</p>
 *
 * <p>Fibre- and sodium-based diets are deliberately not modelled: the stored
 * profile (energy, protein, fat, carbs) cannot derive them, so claiming them
 * would be a guess.</p>
 */
public enum Diet {
    BALANCED,
    HIGH_PROTEIN,
    LOW_CARB,
    LOW_FAT
}
