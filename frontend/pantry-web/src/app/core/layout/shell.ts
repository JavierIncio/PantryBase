import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { take } from 'rxjs';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatToolbar } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { SessionState } from '../auth/session.state';
import { NAV_ITEMS } from '../nav/nav-item';

/**
 * Application shell: a single top toolbar that carries the brand, the primary
 * navigation links and the signed-in user menu, with the routed page rendered
 * below it.
 *
 * The toolbar replaces the former side drawer so the navigation is always one
 * tap away without stealing horizontal room from the content; the links simply
 * wrap on narrow viewports, which removes the need to track a breakpoint.
 */
@Component({
  selector: 'app-shell',
  imports: [
    MatButton,
    MatIcon,
    MatMenu,
    MatMenuItem,
    MatMenuTrigger,
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
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionState);
  private readonly router = inject(Router);

  protected readonly navItems = NAV_ITEMS;

  /** Toolbar identity label: the signed-in username, or a neutral fallback. */
  protected readonly userName = computed(() => this.session.user()?.username ?? 'Account');

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
