/**
 * Recipe data models exposed by the `/api/recipes` endpoints (roadmap H4).
 *
 * Hand-written DTOs mirroring the records in
 * `backend/pantry-api/.../recipes/dto` one to one, like the pantry and catalog
 * models; like them they are stand-ins to be replaced by the generated OpenAPI
 * client once the springdoc spec is wired into the build.
 *
 * Response records drop the `Response` suffix the backend uses
 * (`RecipeSummaryResponse` becomes {@link RecipeSummary}), the same way
 * `pantry.models.ts` names `PantryItemResponse`.
 */

/** Where a recipe came from, matching the `RecipeSource` enum of the API. */
export type RecipeSource = 'USER' | 'THE_MEAL_DB';

/**
 * Query `GET /api/recipes` accepts; every field is optional.
 *
 * `name` is a free-text filter the grid sends once the user stops typing (see
 * `RecipesPage`); `threshold`, `strict` and `allergenSafe` mirror the backend's
 * filter semantics — the grid only uses `strict` and `allergenSafe`, because
 * its own threshold slider is a client-side decision over the rows already
 * fetched; `ingredientId` exists in the contract for callers that browse by a
 * canned ingredient.
 */
export interface RecipeSearchQuery {
  /** Free-text name filter; a blank value is never sent. */
  readonly name?: string;
  /** Only recipes that use this catalog ingredient (backend contract H4). */
  readonly ingredientId?: number;
  /** Strict mode on the server: only recipes with `matchScore == 1`. */
  readonly strict?: boolean;
  /** Coverage percentage the server's lax filter uses (0-100). */
  readonly threshold?: number;
  /** Excludes recipes containing any allergen the user has excluded. */
  readonly allergenSafe?: boolean;
}

/**
 * One row of the recipe grid, as `GET /api/recipes` answers it.
 *
 * `matchScore` is a fraction 0..1 the UI renders as a percentage, and it may be
 * `null`: a recipe whose coverage was never computed (for example a local
 * `USER` recipe without shelf quantities behind it) has no number to show, and
 * null is a different fact from zero — zero would say "nothing matches", null
 * says "nobody counted". Consumers must treat null as "no coverage known",
 * which is exactly what {@link passesThreshold} does: it can never reach a
 * threshold above 0%.
 *
 * `missingCount` counts the ingredient lines the pantry does not cover at 100%,
 * a companion figure to `matchScore` that says how far the gap is.
 */
export interface RecipeSummary {
  readonly id: number;
  readonly source: RecipeSource;
  /** Provider id (e.g. a TheMealDB meal id); null for recipes created here. */
  readonly externalId: string | null;
  readonly name: string;
  /** Category as the provider named it (e.g. `Chicken`); may be empty. */
  readonly category: string;
  /** Regional style as the provider named it (e.g. `Italian`); may be empty. */
  readonly area: string;
  readonly imageUrl: string | null;
  readonly servings: number | null;
  readonly matchScore: number | null;
  readonly missingCount: number | null;
  /** EU allergen codes (`EGG`, `MILK`, ...); empty when the recipe has none. */
  readonly allergenCodes: string[];
}

/**
 * Full recipe read by `GET /api/recipes/{recipeId}`.
 *
 * The payload carries two parallel collections that must never be rendered as
 * they arrive: {@link ingredients} is the recipe's own ordered list and
 * {@link coverage} measures the same lines against the user's pantry, but
 * coverage has no `position` of its own, so pairing them is a join by
 * `ingredientId` (see {@link pairCoverage} in `recipe-matching.ts`). Iterating
 * `coverage` would lose the recipe's order; iterating `ingredients` without the
 * join would lose how much of each is available.
 */
export interface RecipeDetail {
  readonly id: number;
  readonly source: RecipeSource;
  readonly externalId: string | null;
  readonly name: string;
  readonly category: string;
  readonly area: string;
  /** Free-text preparation steps; the provider writes one step per line. */
  readonly instructions: string;
  readonly imageUrl: string | null;
  readonly videoUrl: string | null;
  readonly tags: string[];
  readonly servings: number | null;
  /** Link to the provider's own page, when the recipe has one. */
  readonly sourceUrl: string | null;
  /** Ingredient lines, in the recipe's own order. */
  readonly ingredients: RecipeIngredient[];
  /** Per-serving nutrients; null when the provider has none computed. */
  readonly nutrients: RecipeNutrients | null;
  readonly allergenCodes: string[];
  readonly matchScore: number | null;
  /** Coverage of each ingredient against the signed-in user's pantry. */
  readonly coverage: IngredientCoverage[];
}

/**
 * One ingredient line of a recipe.
 *
 * Two amounts travel per line: `original` is what the provider wrote (a cup,
 * a pinch) and `canonical` is that amount normalized to a catalog unit, which
 * is the figure a pantry comparison can use. The client renders the canonical
 * amount — mixing original wording into a coverage comparison would compare
 * "1 cup" with a gram count.
 */
export interface RecipeIngredient {
  /** Catalog ingredient this line resolves to. */
  readonly ingredientId: number;
  readonly ingredientName: string;
  readonly ingredientCategory: string;
  /** Amount as the provider wrote it, in the provider's unit. */
  readonly originalAmount: number;
  readonly originalUnitCode: string;
  /** Amount normalized to a catalog unit, in `canonicalUnitCode`. */
  readonly canonicalAmount: number;
  readonly canonicalUnitCode: string;
  /** Unit category of the canonical unit (`WEIGHT`, `VOLUME`, `COUNT`). */
  readonly canonicalUnitCategory: string;
  /** Line order inside the recipe; what `coverage` is missing. */
  readonly position: number;
}

/**
 * Per-serving nutrient values.
 *
 * A `null` field is an absent value, never zero: no measured energy is not the
 * same as a zero-calorie recipe, and the UI renders it as such.
 */
export interface RecipeNutrients {
  readonly energyKcal: number | null;
  readonly proteinG: number | null;
  readonly fatG: number | null;
  readonly carbsG: number | null;
}

/**
 * How well the pantry covers one ingredient of a recipe.
 *
 * `needed` is the amount the recipe asks for (in a catalog unit) and
 * `available` is what the pantry holds, in the unit the pantry line keeps, so
 * the two may well be expressed in different units — the UI labels each with
 * its own. Exactly `neededAmount` is what "covered" means: `fullyCovered` is
 * true when the pantry holds at least that, with no partial credit.
 *
 * The list deliberately carries no `position`: it is joined back to the
 * ingredient lines by `ingredientId`, never rendered on its own.
 */
export interface IngredientCoverage {
  readonly ingredientId: number;
  readonly ingredientName: string;
  readonly neededAmount: number;
  readonly neededUnitCode: string;
  readonly availableAmount: number;
  readonly availableUnitCode: string;
  readonly fullyCovered: boolean;
}
