import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionState } from './session.state';

/**
 * Endpoints that never receive a Bearer header and never trigger the 401
 * refresh: the token exchange and the password-reset calls are public by
 * design, so even a browser with a live in-memory session must not sign them
 * (the reset endpoints are deliberately anonymous for anti-enumeration).
 */
const AUTH_ENDPOINT_RE =
  /\/api\/auth\/(login|register|refresh|password-reset-token|password-reset)$/;

/**
 * Attaches the access token to outgoing requests and recovers from 401s.
 *
 * On an unauthorized response (except on auth endpoints) the interceptor
 * refreshes the token once through {@link AuthService#refreshTokens} — which is
 * single-flight, so concurrent 401s share a single HTTP call — and replays the
 * original request with the fresh token. If the refresh fails there is nothing
 * to revoke locally (the refresh token lives in an httpOnly cookie that JS
 * cannot read or clear), so the in-memory session is dropped and the user is
 * sent to the login page.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionState);
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = session.accessToken();
  const isAuthEndpoint = AUTH_ENDPOINT_RE.test(req.url);
  const outgoing =
    token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(outgoing).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthEndpoint) {
        return throwError(() => error);
      }

      // Replay the original request exactly once, after a successful refresh.
      return auth.refreshTokens().pipe(
        switchMap(() =>
          next(
            req.clone({
              setHeaders: { Authorization: `Bearer ${session.accessToken() ?? ''}` },
            }),
          ),
        ),
        catchError((retryError: HttpErrorResponse) => {
          session.clear();
          router.navigateByUrl('/login');
          return throwError(() => retryError);
        }),
      );
    }),
  );
};
