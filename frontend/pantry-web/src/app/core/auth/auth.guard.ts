import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { map, catchError, switchMap } from 'rxjs';
import { of } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionState } from './session.state';

/**
 * Protects the application shell (and every feature page under it).
 *
 * A signed-in user passes through immediately. An anonymous one triggers
 * decision D2 — session restore on cold start: the refresh cookie is the only
 * client-side credential, so `POST /api/auth/refresh` + `GET /api/users/me`
 * try to rebuild the session before giving up. On failure the guard bounces to
 * the login screen carrying the attempted URL as `returnUrl` so the user lands
 * back where they wanted to go after signing in.
 */
export const authGuard: CanMatchFn = (_route, segments) => {
  const session = inject(SessionState);
  const auth = inject(AuthService);
  const router = inject(Router);

  if (session.isAuthenticated()) {
    return true;
  }

  const attemptedUrl = `/${segments.map((s) => s.path).join('/')}`;
  const loginUrl = router.createUrlTree(['/login'], {
    queryParams: { returnUrl: attemptedUrl },
  });

  return auth.refreshTokens().pipe(
    switchMap(() => auth.me()),
    map((user) => {
      session.restore(user);
      return true;
    }),
    catchError(() => {
      router.navigateByUrl(loginUrl);
      return of(false);
    }),
  );
};
