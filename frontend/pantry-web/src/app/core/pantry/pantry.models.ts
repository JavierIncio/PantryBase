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
 * (`PantryItemResponse` becomes {@link PantryItem}), the same way
 * `profile.models.ts` names `UserPreferencesResponse`.
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
 * H3 only ever writes `MANUAL`; the cooking-session origin arrives in H5, and
 * this union has to be widened then — a compiler error is the intended reminder.
 */
export type ConsumptionOrigin = 'MANUAL';

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
