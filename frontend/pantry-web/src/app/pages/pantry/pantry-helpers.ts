import { PantryItem } from '../../core/pantry/pantry.models';

/** The two layouts the pantry can render its lines in. */
export type PantryViewMode = 'list' | 'grid';

/**
 * Which of a row's inline editors is expanded; at most one, never two.
 *
 * Shared between the page (which owns the open-panel state) and the row
 * component (which emits the request to open one), so both speak the same
 * vocabulary instead of the child knowing the page's panel markup.
 */
export type PanelKind = 'edit' | 'adjust' | 'history';

/**
 * The client-side filters a list read once can answer without a new request.
 *
 * The whole filtering lives in one shape so the pure function that applies it
 * stays easy to test and the toolbar binds to plain fields instead of reaching
 * into the items themselves.
 */
export interface PantryFilter {
  /** Matched case-insensitively against the line's name; empty means no filter. */
  readonly query: string;
  /** The category to keep, or `null` when every category is shown. */
  readonly category: string | null;
  /** True to keep only rows at zero or at or below {@link LOW_STOCK_THRESHOLD}. */
  readonly lowStockOnly: boolean;
}

/**
 * A row at or below this many of its own unit counts as "low stock" for the
 * low/empty filter.
 *
 * The threshold lives next to the filtering logic so it is found with the rule
 * it tunes and replaced easily; it is deliberately expressed in the line's own
 * unit, because the pantry stores grams, milliliters and counts side by side
 * and a single absolute threshold would treat 10 g and 10 units as the same
 * number. Ten is an arm's-length reseed point: a line of yogurt (500 g) has to
 * fall to 2 % of itself before it warns, while a dozen eggs at 5 warns early.
 */
export const LOW_STOCK_THRESHOLD = 10;

/** One category block of the list, in the order the API returned it. */
export interface PantrySection {
  readonly category: string;
  readonly items: PantryItem[];
}

const matchesQuery = (name: string, query: string): boolean =>
  query === '' ? true : name.toLocaleLowerCase().includes(query.toLocaleLowerCase());

const matchesCategory = (category: string, selected: string | null): boolean =>
  selected === null ? true : category === selected;

const isLowOrEmpty = (quantity: number): boolean =>
  quantity === 0 || quantity <= LOW_STOCK_THRESHOLD;

/**
 * Applies the toolbar filters to a pantry list.
 *
 * Pure and synchronous on purpose: the pantry is already on screen (it was read
 * once), the list is household-sized, and every filter is a predicate over the
 * fields each row already carries, so a request per keystroke would answer no
 * question the client cannot. The server's order survives: filtering never
 * re-sorts, so the collation the API chose remains the one shown.
 */
export function filterPantryItems(
  items: readonly PantryItem[],
  filter: PantryFilter,
): PantryItem[] {
  const query = filter.query.trim();
  return items.filter(
    (item) =>
      matchesQuery(item.name, query) &&
      matchesCategory(item.category, filter.category) &&
      (!filter.lowStockOnly || isLowOrEmpty(item.quantity)),
  );
}

/**
 * Groups a flat list into consecutive category blocks.
 *
 * A single pass and no sorting: the API already answers by category and then by
 * name, so sorting here could only disagree with the server's collation (and
 * would cost a second copy of the list on every write).
 */
export function groupByCategory(items: readonly PantryItem[]): PantrySection[] {
  const sections: PantrySection[] = [];
  for (const item of items) {
    const last = sections[sections.length - 1];
    if (last && last.category === item.category) {
      last.items.push(item);
    } else {
      sections.push({ category: item.category, items: [item] });
    }
  }
  return sections;
}

export type UnitStep = readonly [string, number];

const DEFAULT_STEP = 1;

/**
 * Maps a unit code to a sensible increment/decrement step for inline quantity
 * editing.
 *
 * Rationale: the quantity is changed in the same unit the line keeps, and thin
 * units want fine steps while bulk units want coarser ones so a stray click
 * cannot move an entire stock by accident. The values are:
 * - COUNT (`UNIT`): 1 step at a time, the user counts single units.
 * - `GRAM`/`ML`: 10, a fine-grained value still useful for typical pantry rows
 *   (a tub of yogurt is 500 g; ten clicks cover it).
 * - `KG`/`L`: 50, so a half-kilo movement is one click and a stray click cannot
 *   wipe out a shelf.
 * - spoons (`TEASPOON`/`TABLESPOON`): 1, cooking measures are used precisely and
 *   a 10-spoon leap would be meaningless.
 * - anything else: 1, a conservative default that never jumps more than one
 *   atomic unit.
 *
 * The mapping is pure and deliberately explicit, never inferred from the name,
 * so it is deterministic, unit-tested, and cannot surprise on an unknown code.
 * It never guesses cross-category conversion: whether two units convert depends
 * on the ingredient density, which only the API knows.
 */
export function stepForUnit(unitCode: string): number {
  const normalized = unitCode.toUpperCase();
  switch (normalized) {
    case 'UNIT':
      return 1;
    case 'GRAM':
    case 'G':
    case 'ML':
      return 10;
    case 'KG':
    case 'KILOGRAM':
    case 'L':
    case 'LITER':
      return 50;
    case 'TSP':
    case 'TEASPOON':
    case 'TBSP':
    case 'TABLESPOON':
      return 1;
    default:
      return DEFAULT_STEP;
  }
}

/**
 * Computes the signed delta that moves `current` to `target`.
 *
 * Used for inline text entry: typing `250` while the row holds `244` means "I
 * want to reach 250", so the adjustment posted is `250 - 244` in the line's
 * unit. The backend's adjustments endpoint accepts only a signed delta and
 * writes it into the ledger, so the absolute target is never sent: a round trip
 * that carried the target would silently bypass the audit trail the model was
 * built around.
 *
 * A non-finite input yields `0`: there is nothing to adjust, and `0` is refused
 * by the API (400), which surfaces the malformed value instead of hiding it.
 */
export function deltaToTarget(current: number, target: number): number {
  if (!Number.isFinite(current) || !Number.isFinite(target)) {
    return 0;
  }
  return target - current;
}
