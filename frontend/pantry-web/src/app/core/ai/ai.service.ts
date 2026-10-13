import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Recommendation } from './ai.models';

/**
 * Calls the LLM recommender under `/api/ai` (roadmap H7).
 *
 * Lives outside the pages so the components stay presentational and the
 * endpoint is typed in exactly one place, following {@link ProfileService}.
 * Authentication is handled by the JWT interceptor, so this service never
 * touches headers itself.
 */
@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);

  /**
   * Asks the model for recipe suggestions based on the user's pantry.
   *
   * The request deliberately carries no body: the pantry, the catalog and the
   * user's `useAi` preference are all read server-side from the authenticated
   * principal — nothing about the prompt travels from the client.
   *
   * The endpoint has no "AI disabled" error: when the user turned `useAi` off
   * the backend answers 200 with an empty array, so a caller must treat `[]` as
   * a real, expected answer (nothing to suggest) rather than a failure. The UI
   * hides the entry point in that case, but the contract still holds for any
   * caller that asks anyway.
   */
  recommend(): Observable<Recommendation[]> {
    return this.http.post<Recommendation[]>('/api/ai/recommend', null);
  }
}
