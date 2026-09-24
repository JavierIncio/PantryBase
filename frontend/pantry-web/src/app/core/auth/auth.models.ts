import { HttpErrorResponse } from '@angular/common/http';

/**
 * Auth-related data models mirroring the backend REST contract.
 *
 * These are hand-written DTOs (milestone H1) to be replaced by the generated
 * OpenAPI client from springdoc (`/v3/api-docs`) once wired into the build;
 * they intentionally match the records in
 * `backend/pantry-api/.../api/{auth,user,common}` one-to-one.
 */

/** Roles a user can hold, matching the `Role` enum of the API. */
export type UserRole = 'ADMIN' | 'USER';

/** Body of `POST /api/auth/login`; `loginMethod` accepts username OR email. */
export interface LoginRequest {
  readonly loginMethod: string;
  readonly password: string;
}

/** Body of `POST /api/auth/register`; the profile fields are optional upstream. */
export interface RegisterRequest {
  readonly username: string;
  readonly email: string;
  readonly password: string;
  readonly firstName?: string;
  readonly lastName?: string;
}

/**
 * Body of every successful authentication response (login/register/refresh).
 *
 * The `refreshToken` field is deliberately ignored by the client: the refresh
 * token is stored in the httpOnly `refresh_token` cookie (path `/api/auth`) by
 * the backend, and the browser sends it implicitly on `/refresh` and `/logout`.
 */
export interface AuthResponse {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly tokenType: string;
  readonly expiresIn: number;
}

/**
 * Body of `POST /api/auth/password-reset-token`.
 *
 * `loginMethod` accepts the username OR the email. The endpoint answers a
 * uniform 204 whether or not the account exists (anti-enumeration), so this
 * request is the only thing the forgot-password page ever sends.
 */
export interface PasswordResetTokenRequest {
  readonly loginMethod: string;
}

/**
 * Body of `POST /api/auth/password-reset`.
 *
 * The token comes from the emailed link (`{base}/auth/reset-password?token=`),
 * never from user input or stored state. A 400 means the token is invalid or
 * expired; the new password must be 8–72 characters.
 */
export interface PasswordResetRequest {
  readonly token: string;
  readonly newPassword: string;
}

/**
 * Body of `PUT /api/auth/password` (authenticated).
 *
 * `currentPassword` may be empty for password-less OAuth users establishing
 * their first password; sending a non-empty value when the account has no
 * password yields a 409, and a wrong one a 400.
 */
export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

/** Profile returned by `GET /api/users/me`. */
export interface UserResponse {
  readonly id: number;
  readonly username: string;
  readonly email: string;
  readonly firstName: string | null;
  readonly lastName: string | null;
  readonly roles: UserRole[];
}

/** Standard error body produced by the API (`ErrorResponse` record upstream). */
export interface ErrorResponse {
  readonly timestamp: string;
  readonly status: number;
  readonly error: string;
  readonly message: string;
  readonly path: string;
}

/**
 * Normalizes any request failure into an {@link ErrorResponse}.
 *
 * The backend answers failures with the JSON contract above; when that body is
 * missing (proxy down, network error, ...) a synthetic response is derived from
 * the {@link HttpErrorResponse} so every consumer handles exactly one shape.
 */
export function toErrorResponse(error: HttpErrorResponse): ErrorResponse {
  const body = error.error as Partial<ErrorResponse> | null;
  if (body && typeof body.message === 'string') {
    return body as ErrorResponse;
  }
  return {
    timestamp: new Date().toISOString(),
    status: error.status,
    error: error.statusText,
    message:
      error.status === 0 ? 'Unable to reach the server. Please try again later.' : error.message,
    path: '',
  };
}
