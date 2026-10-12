import { UnitCategory } from '../catalog/catalog.models';

/**
 * Pantry inventory data models exposed by the `/api/pantry` endpoints
 * (roadmap H3).
 *
 * Hand-written DTOs mirroring the records in
 * `backend/pantry-api/.../pantry/dto` one to one, like the auth and profile
 * models; like them they are stand-ins to be replaced by the generated OpenAPI
 * client once the springdoc spec is wired into the build.
 *
 * Response records drop the `Response` suffix the backend uses
 * (`PantryItemResponse` becomes {@link PantryItem}, `PantrySummaryResponse`
 * becomes {@link PantrySummary}), the same way `profile.models.ts` names
 * `UserPreferencesResponse`.
 */

/**
 * One line of the signed-in user's inventory.
 *
 * `unitCode` is a catalog code (`GRAM`, `ML`, `UNIT`, ...), not a display label:
 * the backend stores every quantity in the unit the ingredient resolves to and
 * that choice is immutable, so the same code always denotes the same thing and
 * the view is the only place that turns it into text.
 *
 * `version` counts writes and is informational here: nothing asks the client to
 * send it back, the server resolves concurrent writers itself.
 */
export interface PantryItem {
  readonly id: number;
  /** FoodData Central id of the ingredient behind this line. */
  readonly fdcId: number;
  readonly name: string;
  /** Provider category; the list is already grouped and sorted by it. */
  readonly category: string;
  /** Stock on hand, always a non-negative number in `unitCode`. */
  readonly quantity: number;
  readonly unitCode: string;
  /** Free text, e.g. a shelf; null when the user never said. */
  readonly location: string | null;
  /** Free text; null when the user never said. */
  readonly note: string | null;
  readonly version: number;
  readonly updatedAt: string;
}

/**
 * Body of `POST /api/pantry` — adds a line.
 *
 * `fdcId` and not the catalog's internal `id`, and that is the whole point of
 * the field: the client only ever sees provider ids in the catalog, and an
 * endpoint that took an internal id would tie the pantry to a numbering it has no
 * reason to know. Reading `GET /api/catalog/ingredients/{fdcId}` first is not
 * optional, because it is what puts the food in the local catalog this request
 * resolves against (without it the endpoint answers 404).
 *
 * `amount` must be greater than zero and is free in any unit of the catalog: the
 * backend normalizes it to the unit the line will keep (`GRAM`, `ML` or `UNIT`)
 * and reports the stored figures back, so the caller must show what came back
 * rather than what it sent.
 */
export interface CreatePantryItemRequest {
  readonly fdcId: number;
  readonly amount: number;
  readonly unitCode: string;
}

/**
 * Body of `PUT /api/pantry/{id}` — replacement semantics for both fields.
 *
 * The endpoint replaces the two editable attributes of the line, so an omitted
 * field is not "leave it" but "there is none": the client always sends both and
 * encodes an emptied input as `null`. There is deliberately no quantity here —
 * moving stock is an adjustment and only an adjustment leaves a ledger entry.
 */
export interface UpdatePantryItemRequest {
  readonly location: string | null;
  readonly note: string | null;
}

/**
 * Body of `POST /api/pantry/{id}/adjustments`.
 *
 * `amount` is signed (positive adds, negative consumes) and must not be zero;
 * `unitCode` is free and the amount is converted into the unit the line stores.
 */
export interface AdjustPantryItemRequest {
  readonly amount: number;
  readonly unitCode: string;
}

/**
 * What caused a movement, matching the `ConsumptionOrigin` enum.
 *
 * `MANUAL` is a signed adjustment typed by the user; `COOKING_SESSION` is the
 * deduction a completed cooking session performed (H5). The two travel together
 * in one ledger because the pantry only has one kind of movement — what changes
 * is who asked for it.
 */
export type ConsumptionOrigin = 'MANUAL' | 'COOKING_SESSION';

/**
 * One movement of stock from an item's ledger.
 *
 * Carries no unit on purpose: a line's unit is immutable, so every entry of a
 * given line is expressed in it and repeating it could only drift.
 */
export interface ConsumptionEntry {
  readonly id: number;
  /** Signed movement in the line's unit; the sum of them is the quantity. */
  readonly deltaQuantity: number;
  readonly origin: ConsumptionOrigin;
  readonly createdAt: string;
}

/**
 * How many lines the inventory holds, counted by the server in
 * `GET /api/pantry/summary`.
 *
 * `totalItems` is the sum of {@link byCategory} by construction and not a second
 * count of the same thing: two queries for one number can disagree and the client
 * would have no way to tell which one to believe. It is therefore printed as the
 * server states it and never recomputed from the lines already on screen.
 *
 * `zeroItems` counts lines at exactly zero and those lines are inside
 * `totalItems`. Running out of the last of something is an ordinary event the
 * schema allows on purpose, so a total that quietly dropped them would report
 * fewer things than the list shows; publishing the figure hands the decision to
 * the user instead of hiding it in an omission.
 *
 * Both breakdowns are empty when the pantry is, which is an answer and not a
 * failure.
 */
export interface PantrySummary {
  /** Lines in the pantry, the ones at zero included. */
  readonly totalItems: number;
  /** How many of {@link totalItems} are at exactly zero. */
  readonly zeroItems: number;
  /**
   * Lines per ingredient category, largest first.
   *
   * The order is the server's presentation decision and is never re-sorted by
   * the client: a second sort here could only disagree with the one the API
   * already applied.
   */
  readonly byCategory: CategoryShare[];
  /** Lines per kind of storage unit, in the server's order as well. */
  readonly byUnitType: UnitShare[];
}

/** How many lines fall under one ingredient category. */
export interface CategoryShare {
  /**
   * The provider's category, and never `null`: a food that arrived without one is
   * reported by the server under a fallback name, so a view never has to invent
   * a heading — and two views would invent different ones.
   */
  readonly category: string;
  readonly items: number;
}

/**
 * How many lines are kept in each kind of unit.
 *
 * `category` is the catalog's own {@link UnitCategory} rather than a set of unit
 * codes, so the reader does not have to hold the mapping from `GRAM`/`KG`/`LB` to
 * "weight" in order to render the split.
 */
export interface UnitShare {
  readonly category: UnitCategory;
  readonly items: number;
}
