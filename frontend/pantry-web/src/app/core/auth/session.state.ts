import { Injectable, computed, signal } from '@angular/core';
import { AuthResponse, UserResponse } from './auth.models';

/**
 * In-memory session state backed by Angular signals.
 *
 * Kept outside the HTTP layer so components, guards and interceptors react to
 * authentication changes without coupling to the transport. `isAuthenticated`
 * is derived from the access token only: the profile may still be loading when
 * a session is being restored, but the app must already treat the user as
 * signed in.
 */
@Injectable({ providedIn: 'root' })
export class SessionState {
  /** Current access token (JWT) sent as `Authorization: Bearer <token>`. */
  readonly accessToken = signal<string | null>(null);

  /** Profile of the authenticated user, fetched from `/api/users/me`. */
  readonly user = signal<UserResponse | null>(null);

  /** True as soon as an access token is available. */
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  /**
   * Stores the tokens issued by login/register/refresh.
   *
   * The profile is reset on purpose: a token must always be paired with the
   * profile returned by `/api/users/me` (or the OAuth2 callback) via
   * `restore()`, so a stale profile is never shown after a new login.
   */
  login(tokens: AuthResponse): void {
    this.accessToken.set(tokens.accessToken);
    this.user.set(null);
  }

  /**
   * Stores a raw access token.
   *
   * The Google OAuth2 flow delivers the token in the URL fragment
   * (`#accessToken=...`) instead of an {@link AuthResponse} body, so the
   * callback page needs a plain setter in addition to `login()`.
   */
  setAccessToken(token: string | null): void {
    this.accessToken.set(token);
  }

  /** Stores the authenticated user profile. */
  restore(user: UserResponse): void {
    this.user.set(user);
  }

  /** Drops the entire in-memory session (token and profile). */
  clear(): void {
    this.accessToken.set(null);
    this.user.set(null);
  }
}
