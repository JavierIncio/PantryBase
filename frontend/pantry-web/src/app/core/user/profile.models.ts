/**
 * Per-user profile settings exposed by the `/api/users` endpoints.
 *
 * Hand-written DTOs mirroring the backend contract (roadmap H1/H7); like the
 * auth models they are stand-ins to be replaced by the generated OpenAPI
 * client once the springdoc spec is wired into the build.
 */
import { ThemeId } from '../theme/theme.types';

/** How strictly the recipe filter must cover the available pantry. */
export type FilterMode = 'STRICT' | 'LAX';

/**
 * Dietary profile a recipe search can be filtered by, matching the backend enum.
 *
 * `BALANCED` is the "no filter" value: the backend derives the other three from
 * each recipe's per-serving nutrients, which only exist server-side.
 */
export type Diet = 'BALANCED' | 'HIGH_PROTEIN' | 'LOW_CARB' | 'LOW_FAT';

/**
 * Every {@link Diet} paired with the name the UI shows it under.
 *
 * One shared list for all diet selectors (the profile preferences form and the
 * recipes filter), so a choice can never be spelled differently in two places
 * nor survive the removal of its backend enum value. Labels are English to
 * match the sibling selects of both forms (`Filter mode`, `Strict`/`Lax`).
 */
export const DIET_OPTIONS: ReadonlyArray<{ value: Diet; label: string }> = [
  { value: 'BALANCED', label: 'Balanced' },
  { value: 'HIGH_PROTEIN', label: 'High protein' },
  { value: 'LOW_CARB', label: 'Low carb' },
  { value: 'LOW_FAT', label: 'Low fat' },
];

/** Default filtering preferences; all four fields are required upstream. */
export interface UserPreferences {
  readonly filterMode: FilterMode;
  /** Pantry coverage percentage required by the filter (0-100). */
  readonly coverageThreshold: number;
  readonly diet: Diet;
  readonly themeId: ThemeId;
}

/** One entry of the allergen catalog or of the user's exclusion list. */
export interface Allergen {
  readonly id: number;
  /** Stable catalog code (uppercase, e.g. `EGG`) — the value sent on save. */
  readonly code: string;
  readonly name: string;
}

/** Per-user allergy exclusions, `GET/PUT /api/users/allergy-exclusions`. */
export interface AllergyExclusions {
  readonly exclusions: Allergen[];
}

/**
 * Body of `PUT /api/users/profile` — identity replacement semantics.
 *
 * `username` is IGNORED by the backend when null/absent (keeps the current
 * value: renaming is optional on every save); `firstName` and `lastName` are
 * ALWAYS applied, so a null name CLEARS it. The client mirrors this by
 * encoding every empty input as `null` — the form always sends all three
 * fields, never a partial patch.
 */
export interface UpdateProfileRequest {
  readonly username: string | null;
  readonly firstName: string | null;
  readonly lastName: string | null;
}
