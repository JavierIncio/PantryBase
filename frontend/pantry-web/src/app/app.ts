import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { toObservable, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { EMPTY, catchError, distinctUntilChanged, filter, switchMap } from 'rxjs';
import { DEFAULT_THEME } from './core/theme/theme.types';
import { ThemeService } from './core/theme/theme.service';
import { ProfileService } from './core/user/profile.service';
import { SessionState } from './core/auth/session.state';

/**
 * Root application component: hosts routed content and owns startup theming.
 *
 * The theme is applied twice with different sources of truth, in this order:
 * 1. the cached value (or the default) in `ngOnInit`, so the first paint never
 *    flashes the blue theme for a user who chose another palette (FOUC-free);
 * 2. the backend preference as soon as a session exists, so a fresh browser or
 *    a cleared storage still converges to the account's stored theme.
 *
 * Watching the `isAuthenticated` signal (instead of the profile) starts the
 * preference fetch right when the token is available; `switchMap` cancels a
 * pending request if the session changes again (logout/login).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  styleUrl: './app.scss',
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly themeService = inject(ThemeService);
  private readonly profileService = inject(ProfileService);
  private readonly sessionState = inject(SessionState);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    toObservable(this.sessionState.isAuthenticated)
      .pipe(
        distinctUntilChanged(),
        filter((authenticated) => authenticated),
        switchMap(
          () => this.profileService.getPreferences().pipe(catchError(() => EMPTY)), // keep cached/default theme on failure
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((prefs) => this.themeService.apply(prefs.themeId));
  }

  ngOnInit(): void {
    this.themeService.apply(this.themeService.restoreCached() ?? DEFAULT_THEME);
  }
}
