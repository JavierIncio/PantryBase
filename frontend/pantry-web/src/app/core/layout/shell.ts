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
import { map } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatListItem, MatListItemIcon, MatNavList } from '@angular/material/list';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NAV_ITEMS } from '../nav/nav-item';

/**
 * Application shell: a top toolbar plus a responsive navigation drawer that
 * hosts the child routes through a {@link RouterOutlet}. The drawer stays fixed
 * (`side`) on desktop-sized viewports and overlays the content (`over`) on
 * handset-sized ones.
 */
@Component({
  selector: 'app-shell',
  imports: [
    MatIcon,
    MatIconButton,
    MatListItem,
    MatListItemIcon,
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

  protected readonly navItems = NAV_ITEMS;

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
}
