import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdjustPantryItemRequest,
  ConsumptionEntry,
  PantryItem,
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
}
