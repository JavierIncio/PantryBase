/**
 * Cooking-session data models exposed by the `/api/cooking` endpoints
 * (roadmap H5).
 *
 * Hand-written DTOs mirroring the records in
 * `backend/pantry-api/.../cooking/dto` one to one, like the pantry and recipe
 * models; like them they are stand-ins to be replaced by the generated OpenAPI
 * client once the springdoc spec is wired into the build.
 *
 * Response records drop the `Response` suffix the backend uses
 * (`CookingSessionResponse` becomes {@link CookingSession},
 * `CompletionDeductionResponse` becomes {@link CompletionDeduction}).
 */

/** Where a session stands, matching the `CookingSessionStatus` enum of the API. */
export type CookingSessionStatus = 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABANDONED';

/**
 * Whether a status is one the state machine can never leave.
 *
 * `COMPLETED` and `ABANDONED` are terminal: the backend refuses any further
 * transition out of them with a 409, so the page must render those sessions as
 * read-only instead of offering buttons that could only fail. The rule lives
 * here rather than in a template because it is a property of the state machine,
 * not of one screen.
 */
export function isTerminal(status: CookingSessionStatus): boolean {
  return status === 'COMPLETED' || status === 'ABANDONED';
}

/**
 * How one ingredient line of a recipe would be deducted from the pantry.
 *
 * The four figures are the whole story of a line and are deliberately not a
 * single "delta": `needed` is what the recipe asks for, `available` what the
 * pantry holds, `deducted = min(needed, available)` what would actually leave
 * the shelf, and `remaining` what is left afterwards (zero on a short line).
 */
export interface DeductionLine {
  readonly ingredientId: number;
  readonly ingredientName: string;
  readonly unitCode: string;
  readonly needed: number;
  readonly available: number;
  readonly deducted: number;
  readonly remaining: number;
}

/**
 * The plan of a completion, in two different places on the wire.
 *
 * The same shape is the body of the **409** a completion gets when the pantry
 * cannot cover the recipe (`hasShortage: true`, nothing changed and the user
 * must confirm) and the `deduction` of the **200** a successful completion
 * answers with. `hasShortage` therefore says which of the two it is: a 409
 * always carries a shortage, while a completed session may report a fully
 * covered plan (`hasShortage: false`) whose lines are all worth showing as the
 * amount that was consumed.
 */
export interface CompletionDeduction {
  readonly hasShortage: boolean;
  readonly lines: DeductionLine[];
}

/**
 * One cooking session, as `POST/GET /api/cooking/sessions` answers it.
 *
 * `deduction` is only populated on the response of a **completed** session:
 * every other read leaves it null, and null means "no completion plan was
 * computed", never "a plan with no lines".
 */
export interface CookingSession {
  readonly id: number;
  readonly recipeId: number;
  readonly status: CookingSessionStatus;
  readonly startedAt: string;
  /** Set when the session completed or was abandoned; null while it is live. */
  readonly completedAt: string | null;
  /** Plan of the completion, only on the response that completed the session. */
  readonly deduction: CompletionDeduction | null;
}

/**
 * Body of `PUT /api/cooking/sessions/{id}/state`.
 *
 * `acceptShortage` is only meaningful for `status: 'COMPLETED'`: omitting it
 * (or sending false) asks the backend to check the pantry first, and it answers
 * 409 with the plan rather than touch anything when stock is short. `true` is
 * the user's explicit "cook anyway" and is what the shortage dialog re-sends.
 */
export interface UpdateCookingStateRequest {
  readonly status: CookingSessionStatus;
  readonly acceptShortage?: boolean;
}

/**
 * Reads an unknown 409 body as a {@link CompletionDeduction}, or answers `null`.
 *
 * The completion endpoint uses 409 for two unrelated refusals: an illegal state
 * transition carries the standard `ErrorResponse`, while a shortage carries the
 * deduction plan instead. The two are told apart by their shape — the plan is
 * the only body with a boolean `hasShortage` and a `lines` array — so a caller
 * can branch on the status and still route the plan differently from the error.
 */
export function asCompletionDeduction(value: unknown): CompletionDeduction | null {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const candidate = value as { hasShortage?: unknown; lines?: unknown };
  if (typeof candidate.hasShortage !== 'boolean' || !Array.isArray(candidate.lines)) {
    return null;
  }
  return value as CompletionDeduction;
}
