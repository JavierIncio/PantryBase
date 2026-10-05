/**
 * Ingredient catalog data models exposed by `/api/catalog` and `/api/units`
 * (roadmap H3).
 *
 * Hand-written DTOs mirroring the records in
 * `backend/pantry-api/.../catalog/dto` one to one, like the auth and pantry
 * models; like them they are stand-ins to be replaced by the generated OpenAPI
 * client once the springdoc spec is wired into the build.
 *
 * Response records drop the `Response` suffix the backend uses
 * (`IngredientSearchResponse` becomes {@link IngredientSearchHit}, `UnitResponse`
 * becomes {@link Unit}), the same way `pantry.models.ts` names
 * `PantryItemResponse`.
 */

/**
 * One food the provider matched, as returned by a search.
 *
 * Deliberately a provider reference and nothing more: a search must not write to
 * the local catalog, so a hit carries no internal id and does not claim to exist
 * here. The local row appears when the detail of this `fdcId` is read, which is
 * also what a pantry line needs before it can be created.
 */
export interface IngredientSearchHit {
  readonly fdcId: number;
  readonly name: string;
  /** Provider dataset, e.g. `Foundation` or `SR Legacy`. */
  readonly dataType: string;
}

/**
 * Grams that one household measure weighs for a specific food.
 *
 * Only the unit code travels here, never a label: labels belong to the fixed
 * unit catalog, and repeating them per ingredient would make renaming a unit a
 * change in as many places as there are ingredients.
 */
export interface IngredientMeasure {
  readonly unitCode: string;
  /** Grams in one unit of `unitCode` (a cup of flour, not of water). */
  readonly gramPerUnit: number;
}

/**
 * The four nutrients a pantry feature can do something with; `null` when the
 * provider has no value for the food, never `0` (an absent value and a food with
 * no energy are different facts).
 */
export interface NutrientProfile {
  readonly energyKcal: number | null;
  readonly proteinG: number | null;
  readonly fatG: number | null;
  readonly carbsG: number | null;
}

/**
 * Full detail of one catalog ingredient, which is also the moment it becomes
 * part of the local catalog.
 *
 * `id` is the internal key and is of no use to a caller: the pantry endpoints
 * are addressed by `fdcId`, so this field is read as proof that the ingredient
 * now exists locally and is otherwise ignored.
 *
 * `measures` is empty when the provider weighed no household measure for the
 * food. That is a normal outcome, not a failure, and it is never `null` so
 * callers need no null check.
 */
export interface IngredientDetail {
  readonly id: number;
  readonly fdcId: number;
  readonly name: string;
  readonly dataType: string;
  /** Provider category; the pantry list is later grouped and sorted by it. */
  readonly category: string;
  readonly nutrients: NutrientProfile;
  /** Curated density used when a volume has to become a weight, else `null`. */
  readonly densityClass: string | null;
  readonly measures: IngredientMeasure[];
}

/** What a unit measures, matching the `UnitCategory` enum. */
export type UnitCategory = 'WEIGHT' | 'VOLUME' | 'COUNT';

/**
 * One row of the fixed unit catalog.
 *
 * `canonical` marks the three storage anchors (`GRAM`, `ML`, `UNIT`) that every
 * conversion points at; the client only reads it to understand why those three
 * are not interchangeable, never to decide anything about a quantity.
 */
export interface Unit {
  readonly code: string;
  /** Display name of the unit, e.g. `Tablespoon`. */
  readonly name: string;
  readonly category: UnitCategory;
  readonly canonical: boolean;
}
