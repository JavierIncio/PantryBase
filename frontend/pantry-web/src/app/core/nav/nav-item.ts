/**
 * Describes a single entry of the main application navigation,
 * rendered by the shell navbar.
 */
export interface NavItem {
  /** Absolute route path the entry links to. */
  readonly route: string;
  /** Human-readable label shown in the navigation. */
  readonly label: string;
  /** Material icon ligature rendered next to the label. */
  readonly icon: string;
}

/**
 * Navigation entries for the main product areas defined in the roadmap.
 *
 * Cooking and History were merged into a single `/history` area, so there is no
 * separate `/cooking` entry; the session detail (`/cooking/:id`) is still
 * reachable from the rows inside History.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { route: '/pantry', label: 'Pantry', icon: 'kitchen' },
  { route: '/recipes', label: 'Recipes', icon: 'menu_book' },
  { route: '/saved', label: 'My cookbook', icon: 'bookmarks' },
  { route: '/history', label: 'History', icon: 'history' },
];
