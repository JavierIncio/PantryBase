/**
 * Single source of truth for the user-selectable themes.
 *
 * The ids are part of the API contract: they are the lowercase values of
 * `UserPreferences.themeId` (`GET/PUT /api/users/preferences`) and the value
 * written to the `data-theme` attribute on `<html>`, which `styles.scss` uses
 * to scope one compiled Material 3 theme per palette.
 */
export type ThemeId = 'blue' | 'green' | 'amber' | 'rose' | 'neutral';

/** Theme ids in display order; also the validation allow-list. */
export const THEMES: readonly ThemeId[] = ['blue', 'green', 'amber', 'rose', 'neutral'];

/** Theme applied when none is cached or the stored/returned id is unknown. */
export const DEFAULT_THEME: ThemeId = 'blue';

/** Human-readable label per theme, used by the preference selector. */
export const THEME_LABELS: Readonly<Record<ThemeId, string>> = {
  blue: 'Blue',
  green: 'Green',
  amber: 'Amber',
  rose: 'Rose',
  neutral: 'Neutral',
};
