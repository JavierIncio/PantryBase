import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UserResponse } from '../auth/auth.models';
import {
  Allergen,
  AllergyExclusions,
  UpdateProfileRequest,
  UserPreferences,
} from './profile.models';

/**
 * Reads and updates the per-user profile settings under `/api/users`:
 * identity (username + names), filtering preferences and the allergen
 * exclusion list.
 *
 * Lives outside the page so the component stays presentational and each
 * endpoint is typed in exactly one place. Authentication is handled by the
 * JWT interceptor, so this service never touches headers itself.
 */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  /** Current preferences; the backend answers with defaults when never saved. */
  getPreferences(): Observable<UserPreferences> {
    return this.http.get<UserPreferences>('/api/users/preferences');
  }

  /** Persists the three preference fields and returns the stored values. */
  updatePreferences(preferences: UserPreferences): Observable<UserPreferences> {
    return this.http.put<UserPreferences>('/api/users/preferences', preferences);
  }

  /** Current exclusion list (empty when the user has none). */
  getAllergyExclusions(): Observable<AllergyExclusions> {
    return this.http.get<AllergyExclusions>('/api/users/allergy-exclusions');
  }

  /**
   * Replaces the whole exclusion list with `codes` (an empty array clears it).
   *
   * The codes must match the catalog exactly: the backend rejects anything
   * unknown — e.g. lowercase `peanut` — with a 400, so callers always send
   * the catalog value as-is and handle that error shape in the UI.
   */
  updateAllergyExclusions(codes: string[]): Observable<AllergyExclusions> {
    return this.http.put<AllergyExclusions>('/api/users/allergy-exclusions', { codes });
  }

  /**
   * Replaces the identity fields (username + names) of the signed-in user.
   *
   * Returns the refreshed profile (200) — never a 204 — so the caller can feed
   * it back to {@link SessionState.restore} and every consumer sees the new
   * values immediately. Null username is ignored upstream (keeps the current
   * one); null names clear them; see {@link UpdateProfileRequest}.
   */
  updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>('/api/users/profile', request);
  }

  /** Full allergen catalog (the 14 EU allergens, sorted by name). */
  getAllergenCatalog(): Observable<Allergen[]> {
    return this.http.get<Allergen[]>('/api/users/allergens');
  }
}
