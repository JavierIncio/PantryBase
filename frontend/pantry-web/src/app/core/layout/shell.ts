import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map, take } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatListItem, MatListItemIcon, MatNavList } from '@angular/material/list';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { SessionState } from '../auth/session.state';
import { NAV_ITEMS } from '../nav/nav-item';

/**
 * Application shell: a top toolbar plus a responsive navigation drawer that
 * hosts the child routes through a {@link RouterOutlet}. The drawer stays fixed
 * (`side`) on desktop-sized viewports and overlays the content (`over`) on
 * handset-sized ones.
 *
 * The toolbar also carries the signed-in user area (name + menu) so the logout
 * action is reachable from every routed page without an extra route.
 */
@Component({
  selector: 'app-shell',
  imports: [
    MatIcon,
    MatIconButton,
    MatListItem,
    MatListItemIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
    MatNavList,
    MatSidenav,
    MatSidenavContainer,
    MatSidenavContent,
    MatToolbar,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  styleUrl: './shell.scss',
  templateUrl: './shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionState);
  private readonly router = inject(Router);

  protected readonly navItems = NAV_ITEMS;

  /** Toolbar identity label: the signed-in username, or a neutral fallback. */
  protected readonly userName = computed(() => this.session.user()?.username ?? 'Cuenta');

  /** True while the viewport width stays at or above the desktop breakpoint. */
  protected readonly isDesktop = toSignal(
    this.breakpointObserver.observe('(min-width: 840px)').pipe(map((result) => result.matches)),
    { initialValue: true },
  );

  /** Current drawer open state, kept in sync with the active breakpoint. */
  protected readonly sidenavOpened = signal(false);

  /** Drawer mode: persistent `side` on desktop, overlaying `over` on handset. */
  protected readonly sidenavMode = computed<'side' | 'over'>(() =>
    this.isDesktop() ? 'side' : 'over',
  );

  constructor() {
    // Default the drawer to open on desktop and closed on handset on breakpoint changes.
    effect(() => {
      this.sidenavOpened.set(this.isDesktop());
    });
  }

  /** Toggles the drawer, e.g. from the toolbar hamburger button. */
  protected toggleSidenav(): void {
    this.sidenavOpened.update((opened) => !opened);
  }

  /**
   * Signs the user out and lands on the login screen.
   *
   * The logout endpoint is idempotent and public, so a network failure must
   * not strand the user in a client-side "authenticated" state: on error the
   * in-memory session is cleared here before navigating anyway. On success the
   * session is already dropped by {@link AuthService.logout} (it applies the
   * `session.clear()` tap on the response), so this method only navigates.
   */
  protected logout(): void {
    const leave = () => this.router.navigateByUrl('/login');
    this.auth
      .logout()
      .pipe(take(1))
      .subscribe({
        next: () => leave(),
        error: () => {
          this.session.clear();
          leave();
        },
      });
  }
}
