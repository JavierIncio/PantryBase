import {
  IngredientCoverage,
  RecipeDetail,
  RecipeIngredient,
  RecipeSummary,
} from './recipes.models';

/**
 * Pure recipe-matching rules shared by the recipes pages.
 *
 * No DOM and no HTTP here: the threshold filter, the coverage ordering and the
 * ingredients↔coverage join are the decisions the grid and the detail are built
 * on, and keeping them as exported pure functions lets their tests pin the
 * rules without rendering a component, the same way `unit-groups.ts` does for
 * the unit selectors.
 */

/**
 * Coverage fraction as the percentage the badges show.
 *
 * `null` stays `null`: a recipe without a computed coverage must not display
 * as 0%, because that would claim an empty pantry is the reason when nobody
 * counted in the first place.
 */
export function coveragePercent(matchScore: number | null): number | null {
  return matchScore === null ? null : Math.round(matchScore * 100);
}

/**
 * Whether a recipe passes the client-side threshold slider.
 *
 * The slider is deliberately client-only (roadmap H4): the list is already in
 * the browser, re-filtering it costs nothing, and a request per drag tick would
 * answer no question the local rows cannot answer. A recipe passes when its
 * coverage fraction, read as a percentage, reaches the threshold.
 *
 * A recipe with no computed coverage passes no threshold above zero — an
 * unknown coverage cannot be claimed to reach any goal — and only the literal
 * "show everything" position of 0% lets it through.
 */
export function passesThreshold(matchScore: number | null, thresholdPercent: number): boolean {
  return (matchScore ?? 0) * 100 >= thresholdPercent;
}

/** Coverage figures for the grid, or -1 for a recipe nobody computed. */
const scoreOf = (recipe: RecipeSummary): number => recipe.matchScore ?? -1;

/**
 * Orders the grid from best covered to worst, recipes without a computed
 * coverage last.
 *
 * The lax mode of H4 sorts by score, and the server does not promise an order
 * the grid could print as-is, so this is the one sort the page performs — a
 * stable copy, so two recipes tied on coverage keep the order they arrived in
 * instead of being arbitrarily swapped.
 */
export function orderByCoverage(recipes: readonly RecipeSummary[]): RecipeSummary[] {
  return [...recipes].sort((a, b) => scoreOf(b) - scoreOf(a));
}

/**
 * One line of the ingredients list as the detail page renders it: the recipe's
 * own line plus the coverage that measures it, when the payload has one.
 */
export interface IngredientCoverageRow {
  readonly ingredient: RecipeIngredient;
  /**
   * Coverage of this ingredient, or `null` when the payload carries no row for
   * it: a missing row is not a failure of the recipe, and the line is still
   * shown with the pantry side left as "unknown" rather than dropped. An
   * unmatched line (`matched: false`) can only ever land here — it has no
   * catalog id for the backend to measure.
   */
  readonly coverage: IngredientCoverage | null;
}

/**
 * Joins the two parallel lists of a recipe detail by `ingredientId`.
 *
 * Only the ingredients list owns an order (`position`); the coverage list is a
 * lookup by ingredient, so rendering it in its own order would silently jumble
 * the recipe. The join keeps every ingredient line and attaches the coverage
 * that belongs to it — the full list, so a provider line the catalog never
 * understood is rendered too, just without a coverage row.
 *
 * A coverage entry without an ingredient line is dropped — there is nothing to
 * attach it to — and an ingredient that appears more than once in a recipe
 * (the same food used in two steps) receives the one coverage row on both
 * lines, because coverage is measured per ingredient, not per occurrence.
 */
export function pairCoverage(
  ingredients: readonly RecipeIngredient[],
  coverage: readonly IngredientCoverage[],
): IngredientCoverageRow[] {
  const byId = new Map(coverage.map((row) => [row.ingredientId, row]));
  return ingredients.map((ingredient) => ({
    ingredient,
    // An unmatched line has a null id; the coverage keys are never null, so
    // short-circuiting keeps the lookup typed over real catalog ids only.
    coverage: ingredient.ingredientId === null ? null : (byId.get(ingredient.ingredientId) ?? null),
  }));
}

/**
 * The visual modifier of a coverage badge for a score.
 *
 * `full` means the recipe is fully covered, `partial` that it is covered but
 * not completely, and `none` that no coverage was computed — three different
 * colors in the grid and the detail, driven by the same rule so both pages
 * cannot drift apart.
 */
export function coverageClass(
  matchScore: number | null,
): 'coverage-full' | 'coverage-partial' | 'coverage-none' {
  if (matchScore === null) {
    return 'coverage-none';
  }
  return matchScore >= 1 ? 'coverage-full' : 'coverage-partial';
}

/**
 * Renders a quantity without the floating-point noise of a JSON double.
 *
 * The backend counts grams with real division, so a figure arrives as
 * `244.57000000000002` more often than not; three decimals are more than any
 * pantry measurement is worth, and they keep the string finite.
 */
export function formatAmount(value: number): string {
  return String(Math.round(value * 1000) / 1000);
}

/**
 * The pantry's state for a recipe, split into the four facts a pre-flight check
 * cares about.
 *
 * The classification is deliberately derived only from what the detail already
 * carries — no request is made, so the panels that show it cost nothing — and
 * it separates two things the coverage percentage alone conflates:
 *
 * - {@link covered}: matched lines the pantry fully covers (`fullyCovered`);
 * - {@link low}: matched lines that are present but short of the needed amount
 *   (`availableAmount > 0`), a "you have some, not enough";
 * - {@link missing}: matched lines with nothing stored (`availableAmount == 0`),
 *   a true "you don't have it"; the two are told apart because the user fixes
 *   them differently (top up versus buy);
 * - {@link uncatalogued}: recipe lines the catalog never understood
 *   (`matched: false`), which carry no amount to compare and therefore can
 *   never be verified — they exist so the check does not promise a coverage it
 *   cannot measure, the same honesty V14 gave the denominator.
 *
 * `hasGaps` is the summary the callers branch on: true when anything at all
 * stands between the recipe and cooking. The four arrays are always populated
 * (empty, never null) so a consumer can read them without null checks.
 */
export interface RecipeAvailability {
  /** Matched lines the pantry fully covers. */
  readonly covered: IngredientCoverage[];
  /** Matched lines that are present but short of the needed amount. */
  readonly low: IngredientCoverage[];
  /** Matched lines with no stock at all. */
  readonly missing: IngredientCoverage[];
  /** Recipe lines the catalog could not resolve, so nothing can be verified. */
  readonly uncatalogued: RecipeIngredient[];
  /** True when at least one line is low, missing or uncatalogued. */
  readonly hasGaps: boolean;
}

/**
 * Classifies a recipe detail into covered / low / missing / uncatalogued.
 *
 * Pure and synchronous: the four lists come from the detail's own `coverage`
 * and `ingredients`, so the same result is computed on the detail page, in the
 * start-cooking confirmation and in the cooking session's banner without any of
 * them asking the server again. The split between {@link RecipeAvailability.low}
 * and {@link RecipeAvailability.missing} is the one nuance the raw coverage
 * percentage cannot express, which is why the helper exists.
 *
 * Matched lines are read from `coverage` (the only collection that measures
 * them); a matched line the payload happens to omit from `coverage` is not
 * guessed at, it simply is not classified. Unmatched lines are read from
 * `ingredients`, because a line with no catalog id has no coverage row.
 */
export function recipeAvailability(detail: RecipeDetail): RecipeAvailability {
  const covered: IngredientCoverage[] = [];
  const low: IngredientCoverage[] = [];
  const missing: IngredientCoverage[] = [];

  for (const coverage of detail.coverage) {
    if (coverage.fullyCovered) {
      covered.push(coverage);
    } else if (coverage.availableAmount > 0) {
      low.push(coverage);
    } else {
      missing.push(coverage);
    }
  }

  const uncatalogued = detail.ingredients.filter((ingredient) => !ingredient.matched);

  return {
    covered,
    low,
    missing,
    uncatalogued,
    hasGaps: low.length > 0 || missing.length > 0 || uncatalogued.length > 0,
  };
}
