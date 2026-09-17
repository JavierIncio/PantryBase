/**
 * Describes a single entry of the main application navigation,
 * rendered by the shell drawer.
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
 * Placeholder navigation entries for the main product areas defined
 * in the roadmap (milestones H1-H7).
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { route: '/pantry', label: 'Pantry', icon: 'kitchen' },
  { route: '/recipes', label: 'Recipes', icon: 'menu_book' },
  { route: '/cooking', label: 'Cooking', icon: 'soup_kitchen' },
  { route: '/social', label: 'Social', icon: 'people' },
  { route: '/profile', label: 'Profile', icon: 'person' },
];
