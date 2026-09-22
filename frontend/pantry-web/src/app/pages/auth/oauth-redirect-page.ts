import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { SessionState } from '../../core/auth/session.state';

/**
 * Pulls the access token out of the URL fragment.
 *
 * The backend redirects the browser after a successful Google sign-in to
 * `${app.security.oauth2.redirect-uri}#accessToken=...` (see application.yml);
 * the fragment never reaches the server, so only this page can read it.
 * The token may appear as the first fragment parameter or after others, hence
 * the `[#&]` separator class.
 */
export function readAccessTokenFromHash(hash: string): string | null {
  const match = /[#&]accessToken=([^&]+)/.exec(hash);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * OAuth2 callback page rendered at `/auth/callback`.
 *
 * Reads the access token from the fragment, pairs it with the profile fetched
 * from `/api/users/me` and redirects to the pantry. This page has no UI: it
 * runs while the redirect happens, and it strips the token fragment from the
 * address bar before leaving. Without a token the user is sent to the login
 * page (no session to restore).
 */
@Component({
  selector: 'app-oauth-redirect-page',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OAuthRedirectPage implements OnInit {
  private readonly session = inject(SessionState);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const accessToken = readAccessTokenFromHash(window.location.hash);
    if (!accessToken) {
      void this.router.navigateByUrl('/login');
      return;
    }

    this.session.setAccessToken(accessToken);
    this.auth.me().subscribe({
      next: (user) => {
        this.session.restore(user);
        this.cleanFragment();
        void this.router.navigateByUrl('/pantry');
      },
      error: (error: HttpErrorResponse) => {
        // The token in the fragment is unusable; drop it and start over.
        this.cleanFragment();
        void this.router.navigateByUrl('/login');
      },
    });
  }

  /**
   * Removes the access token from the address bar.
   *
   * The fragment never leaves the browser, but leaving a signed-in credential
   * in the URL invites shoulder-surfing and accidental copy-paste leaks.
   */
  private cleanFragment(): void {
    window.history.replaceState(null, '', window.location.pathname);
  }
}
