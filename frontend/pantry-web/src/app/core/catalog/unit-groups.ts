import { IngredientMeasure, Unit, UnitCategory } from './catalog.models';

/**
 * Order of the groups in a unit selector.
 *
 * Mirrors the order the categories are declared in the API, so it reads the same
 * everywhere a unit can be picked. It is a presentation order only: nothing about
 * a conversion is decided by it, and a category the API ever grows is appended
 * after these instead of being hidden.
 */
const CATEGORY_ORDER: readonly UnitCategory[] = ['WEIGHT', 'VOLUME', 'COUNT'];

/** How the three categories are named in the UI. */
const CATEGORY_TITLES: Record<UnitCategory, string> = {
  WEIGHT: 'Weight',
  VOLUME: 'Volume',
  COUNT: 'Count',
};

/** One selectable unit, with the provider's own weight for it when there is one. */
export interface UnitOption {
  readonly code: string;
  readonly label: string;
  /**
   * Grams in one of `code` for the chosen ingredient, or `null` when the
   * provider weighed no such measure. Null and not zero: no weight is not a
   * weightless measure.
   */
  readonly gramPerUnit: number | null;
}

/** The options of one category, ready to be rendered as an `mat-optgroup`. */
export interface UnitGroup {
  readonly category: UnitCategory;
  readonly title: string;
  readonly options: readonly UnitOption[];
}

/**
 * Turns the unit catalog into what a unit selector offers, one group per
 * category.
 *
 * Two rules and nothing else:
 *
 * 1. The units the provider weighed for the chosen food come first inside their
 *    group, each with its weight, because those are the only units that were
 *    really measured for it and therefore the ones the user is reaching for.
 * 2. Every other unit of the catalog is still offered. Conversion does not need
 *    a measured measure: the server falls back to the ingredient's density class
 *    and, failing that, keeps the amount as a volume. Hiding "just in case" would
 *    hide combinations that work.
 *
 * No unit is ever sorted out or hidden by convertibility. Whether a pair of units
 * converts depends on the density of the ingredient, which only the server knows;
 * deciding it here would need a second copy of the density ladder and would
 * disagree with the server the moment either changed. An impossible pair is
 * refused by the API and its message shown, exactly like in the adjustment
 * selector.
 *
 * Ordering inside a group is the catalog's own order (the API answers sorted by
 * code) with the measured ones moved to the front: a stable partition rather than
 * a sort, so nothing is reordered by accident.
 */
export function buildUnitGroups(
  units: readonly Unit[],
  measures: readonly IngredientMeasure[] = [],
): UnitGroup[] {
  const measuredIn = new Map(measures.map((measure) => [measure.unitCode, measure.gramPerUnit]));
  const byCategory = new Map<UnitCategory, UnitOption[]>();

  for (const unit of units) {
    const options = byCategory.get(unit.category) ?? [];
    options.push({
      code: unit.code,
      // A catalog row always has a name; the fallback only guards an empty one,
      // and an unnamed unit is better shown as its code than as nothing.
      label: unit.name || unit.code,
      gramPerUnit: measuredIn.get(unit.code) ?? null,
    });
    byCategory.set(unit.category, options);
  }

  return orderedCategories(byCategory).map((category) => ({
    category,
    title: CATEGORY_TITLES[category] ?? category,
    options: measuredFirst(byCategory.get(category) ?? []),
  }));
}

/** The declared category order, plus any category this version does not know. */
function orderedCategories(byCategory: Map<UnitCategory, UnitOption[]>): UnitCategory[] {
  const known = CATEGORY_ORDER.filter((category) => byCategory.has(category));
  const unknown = [...byCategory.keys()].filter((category) => !known.includes(category));
  return [...known, ...unknown];
}

/** Stable partition: the measured options first, both halves in catalog order. */
function measuredFirst(options: readonly UnitOption[]): readonly UnitOption[] {
  const measured: UnitOption[] = [];
  const rest: UnitOption[] = [];
  for (const option of options) {
    (option.gramPerUnit === null ? rest : measured).push(option);
  }
  return [...measured, ...rest];
}
