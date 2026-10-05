import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdjustPantryItemRequest,
  ConsumptionEntry,
  CreatePantryItemRequest,
  PantryItem,
  PantrySummary,
  UpdatePantryItemRequest,
} from './pantry.models';

/**
 * Reads and writes the signed-in user's pantry under `/api/pantry`.
 *
 * Lives outside the page so the component stays presentational and each
 * endpoint is typed in exactly one place. There is no read-by-id because the
 * list already carries every field a line is displayed with, and the owner is
 * taken from the authentication principal on the server, so no request of this
 * service can be aimed at another user's pantry.
 *
 * Authentication is handled by the JWT interceptor, so this service never
 * touches headers itself.
 */
@Injectable({ providedIn: 'root' })
export class PantryService {
  private readonly http = inject(HttpClient);

  /** Whole pantry, already ordered by category and then by name. */
  listItems(): Observable<PantryItem[]> {
    return this.http.get<PantryItem[]>('/api/pantry');
  }

  /**
   * Adds a line and returns it as stored.
   *
   * The request is addressed by the provider's `fdcId` and the food has to be in
   * the local catalog already (the catalog's detail read is what puts it there),
   * otherwise the endpoint answers 404. A 409 means the ingredient is already in
   * the pantry: an add is never folded into the existing quantity, because that
   * would leave no trace of the second addition, and the adjustment endpoint is
   * what records a movement.
   *
   * The returned quantity and unit are the authoritative ones — the amount is
   * normalized to the unit the line keeps, so "two cups" may well come back as
   * grams.
   */
  createItem(request: CreatePantryItemRequest): Observable<PantryItem> {
    return this.http.post<PantryItem>('/api/pantry', request);
  }

  /**
   * Replaces the location and note of a line.
   *
   * Returns the stored line rather than a 204 so the caller can swap it into
   * its list without a second request; the quantity it reports back is
   * authoritative even though the payload cannot touch it.
   */
  updateItem(itemId: number, request: UpdatePantryItemRequest): Observable<PantryItem> {
    return this.http.put<PantryItem>(`/api/pantry/${itemId}`, request);
  }

  /** Removes a line and, with it, the ledger entries that explained it. */
  deleteItem(itemId: number): Observable<void> {
    return this.http.delete<void>(`/api/pantry/${itemId}`);
  }

  /**
   * Moves the quantity of a line by a signed amount and returns the new line.
   *
   * A sub-resource rather than a field of the item because it writes a ledger
   * entry: any other way of changing stock would leave the audit trail optional.
   */
  adjustQuantity(itemId: number, request: AdjustPantryItemRequest): Observable<PantryItem> {
    return this.http.post<PantryItem>(`/api/pantry/${itemId}/adjustments`, request);
  }

  /** Movements that explain a line's quantity, oldest first. */
  listEntries(itemId: number): Observable<ConsumptionEntry[]> {
    return this.http.get<ConsumptionEntry[]>(`/api/pantry/${itemId}/entries`);
  }

  /**
   * Size of the whole inventory, as the server counts it.
   *
   * A separate endpoint from {@link listItems} even though it counts the same
   * lines: this is one aggregate query on the server side instead of one row per
   * line, so a pantry of any size costs here exactly what its totals are worth.
   * Neither answer replaces the other — the list is what the user manipulates and
   * the summary is what a header says about it — so a view that wants both asks
   * for both, in parallel, and can fail one without losing the other.
   *
   * The breakdowns travel with the totals so that no view has to fold a list it
   * did not fetch into an aggregate, and the client keeps both of them as the
   * server ordered them.
   */
  getSummary(): Observable<PantrySummary> {
    return this.http.get<PantrySummary>('/api/pantry/summary');
  }
}
