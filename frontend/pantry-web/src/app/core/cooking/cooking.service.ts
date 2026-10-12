import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CookingSession, CookingSessionStatus, UpdateCookingStateRequest } from './cooking.models';

/**
 * Reads and writes the signed-in user's cooking sessions under `/api/cooking`.
 *
 * Lives outside the pages so the components stay presentational and each
 * endpoint is typed in exactly one place, following {@link RecipesService}.
 * The owner is taken from the authentication principal on the server, so no
 * request of this service can name another user's session, and the JWT
 * interceptor handles authentication — this service never touches headers.
 */
@Injectable({ providedIn: 'root' })
export class CookingService {
  private readonly http = inject(HttpClient);

  /**
   * Starts a session for a recipe.
   *
   * The body carries the recipe id alone: the owner and the `startedAt` stamp
   * are the server's to set. A 404 means the recipe does not exist for this
   * user, which the detail page surfaces without leaving the recipe.
   */
  createSession(recipeId: number): Observable<CookingSession> {
    return this.http.post<CookingSession>('/api/cooking/sessions', { recipeId });
  }

  /** The user's sessions, most recent first, as the API orders them. */
  listSessions(): Observable<CookingSession[]> {
    return this.http.get<CookingSession[]>('/api/cooking/sessions');
  }

  /**
   * One session by id, reloaded from the server.
   *
   * The page reads this on entry so a refresh or a bookmark lands on the real
   * status instead of a client guess; a 404 is a session that is not (or is no
   * longer) the user's.
   */
  getSession(sessionId: number): Observable<CookingSession> {
    return this.http.get<CookingSession>(`/api/cooking/sessions/${sessionId}`);
  }

  /**
   * Moves a session to another state of the state machine.
   *
   * `acceptShortage` is sent **only** when it is literally `true`: an absent
   * field asks the backend to verify the pantry, and sending `false` would be
   * the same request written a different way. Two kinds of 409 can come back —
   * an illegal transition (`ErrorResponse`) or a shortage
   * (`CompletionDeduction`) — and telling them apart is the caller's job (see
   * {@link asCompletionDeduction}).
   */
  updateState(
    sessionId: number,
    status: CookingSessionStatus,
    acceptShortage?: boolean,
  ): Observable<CookingSession> {
    const body: UpdateCookingStateRequest =
      acceptShortage === true ? { status, acceptShortage: true } : { status };
    return this.http.put<CookingSession>(`/api/cooking/sessions/${sessionId}/state`, body);
  }
}
