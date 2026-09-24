import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { finalize, Observable, shareReplay, switchMap, tap } from 'rxjs';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  PasswordResetRequest,
  PasswordResetTokenRequest,
  RegisterRequest,
  UserResponse,
} from './auth.models';
import { SessionState } from './session.state';

/**
 * Talks to the auth and user endpoints of the API and keeps the
 * {@link SessionState} in sync with the tokens and the profile.
 *
 * `login` and `register` chain `/api/users/me` right after the token exchange
 * so the session is complete (token + profile) before the calling page
 * navigates away.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionState);

  /**
   * Shared observable of the in-flight refresh call.
   *
   * Concurrent callers (e.g. several requests hitting a 401 at the same time)
   * subscribe to the same HTTP request instead of firing one refresh each; the
   * pointer is cleared once the call terminates so a later expiration triggers
   * a fresh refresh.
   */
  private refreshInFlight: Observable<AuthResponse> | null = null;

  /** Authenticates with username or email, stores the tokens and loads the profile. */
  login(request: LoginRequest): Observable<UserResponse> {
    return this.authAndLoadProfile(this.http.post<AuthResponse>('/api/auth/login', request));
  }

  /** Creates the account, stores the tokens and loads the profile. */
  register(request: RegisterRequest): Observable<UserResponse> {
    return this.authAndLoadProfile(this.http.post<AuthResponse>('/api/auth/register', request));
  }

  /**
   * Single-flight refresh of the access token from the httpOnly cookie.
   *
   * The body is empty on purpose: the backend reads the `refresh_token` cookie
   * (path `/api/auth`) that the browser sends along with the request.
   */
  refreshTokens(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>('/api/auth/refresh', {}).pipe(
        tap((tokens) => this.session.login(tokens)),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay(1),
      );
    }
    return this.refreshInFlight;
  }

  /** Revokes the refresh token server-side and clears the in-memory session. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this.session.clear()));
  }

  /** Fetches the profile of the current user. */
  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/users/me');
  }

  /**
   * Requests a password-reset email for the given username or email.
   *
   * The endpoint answers 204 unconditionally (no account fingerprinting), so
   * the caller must present the same generic outcome to the user on success
   * and on failure alike — see {@link ForgotPasswordPage}.
   */
  requestPasswordReset(loginMethod: string): Observable<void> {
    const body: PasswordResetTokenRequest = { loginMethod };
    return this.http.post<void>('/api/auth/password-reset-token', body);
  }

  /**
   * Consumes a password-reset token with the new password.
   *
   * A 400 means the token is invalid or expired — the caller surfaces that
   * error message on the form. The token itself is a query parameter of the
   * emailed link and must never be persisted or logged.
   */
  resetPassword(token: string, newPassword: string): Observable<void> {
    const body: PasswordResetRequest = { token, newPassword };
    return this.http.post<void>('/api/auth/password-reset', body);
  }

  /**
   * Changes the current password (or establishes the first one).
   *
   * `currentPassword` may be empty only for OAuth-created accounts that never
   * set a password; the backend answers 409 when one exists without a
   * password (empty sent) and 400 on a mismatch, both surfaced as card-level
   * errors by the profile page.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    const body: ChangePasswordRequest = { currentPassword, newPassword };
    return this.http.put<void>('/api/auth/password', body);
  }

  /**
   * Chains `/api/users/me` after a successful token exchange.
   *
   * Shared by `login` and `register`: once the tokens are stored, the profile
   * request runs through the JWT interceptor and completes the session.
   */
  private authAndLoadProfile(exchange: Observable<AuthResponse>): Observable<UserResponse> {
    return exchange.pipe(
      tap((tokens) => this.session.login(tokens)),
      switchMap(() => this.me()),
      tap((user) => this.session.restore(user)),
    );
  }
}
