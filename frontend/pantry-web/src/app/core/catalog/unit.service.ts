import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { toErrorResponse } from '../auth/auth.models';
import { Unit } from './catalog.models';

/**
 * Owner of the unit catalog read from `GET /api/units`, and the single place a
 * code becomes a label.
 *
 * The catalog is a fixed table of twelve rows that the backend seeds once and
 * never changes at runtime, and several screens need its labels (the pantry rows,
 * the adjustment selector, the quick-add unit selector). Loading it in each of
 * them would mean the same twelve rows fetched over and over and, worse, a page
 * whose labels disagree with a dialog opened from it; so the catalog is fetched
 * once per session and published as a signal every consumer reads.
 *
 * {@link label} keeps one invariant for the whole app: a code the catalog does
 * not know renders as itself. A raw `FLOZ` in the middle of a row is a fact the
 * user can act on, whereas a blank cell would look like an empty quantity.
 *
 * A failed load is deliberately not fatal and not retried on its own: the codes
 * are still there to be sent, only the labels are missing, so consumers keep
 * working with the fallback and may offer the user a retry.
 */
@Injectable({ providedIn: 'root' })
export class UnitService {
  private readonly http = inject(HttpClient);

  /** The catalog as answered, `null` until it has loaded and after a failure. */
  private readonly catalog = signal<readonly Unit[] | null>(null);

  /** Label lookup, so rendering a row never walks the array. */
  private readonly labels = computed(
    () => new Map((this.catalog() ?? []).map((unit) => [unit.code, unit.name])),
  );

  /** The catalog units, or `null` while it has never been loaded. */
  readonly units = this.catalog.asReadonly();

  /** True while the one load of the catalog is in flight. */
  readonly loading = signal(false);

  /** Message of a failed load; the codes stay usable while it is set. */
  readonly loadError = signal<string | null>(null);

  /**
   * Loads the catalog once per session; a second call while it is loaded or in
   * flight is a no-op.
   *
   * Idempotent on purpose: the pages that need it call it from their lifecycle
   * hook without knowing whether somebody already did, and a repeat would be a
   * request whose answer cannot have changed.
   *
   * A failed load is the exception, and stays retryable: the signal holds `null`
   * while the message is set, so a later call tries again. It is not cached as a
   * failure because the catalog is a seeded table of a running application, and
   * the usual reason it did not answer is that the request did not get through.
   */
  load(): void {
    if (this.catalog() !== null || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);

    this.http.get<Unit[]>('/api/units').subscribe({
      next: (units) => {
        this.catalog.set(units);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(toErrorResponse(error).message || 'Unable to load the unit catalog.');
      },
    });
  }

  /** Display name of a unit code, falling through to the code itself. */
  label(code: string): string {
    return this.labels().get(code) ?? code;
  }
}
