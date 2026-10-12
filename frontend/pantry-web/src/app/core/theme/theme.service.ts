import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { DEFAULT_THEME, ThemeId, THEMES } from './theme.types';

/**
 * Applies the active theme to the document and remembers it across sessions.
 *
 * A theme is the value of the `data-theme` attribute on `<html>`; the compiled
 * CSS in `styles.scss` scopes one Material 3 token set per value. Persistence
 * is a best-effort write to `localStorage` so a reload renders with the right
 * palette BEFORE any profile request completes (FOUC-free startup); the backend
 * preference remains the source of truth once loaded.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private static readonly STORAGE_KEY = 'pantry.themeId';
  private readonly document = inject(DOCUMENT);

  /** Switches the document theme and caches it. Unknown ids fall back to the default. */
  apply(theme: ThemeId): void {
    const themeId = this.normalize(theme);
    (this.document.documentElement.dataset as DOMStringMap)['theme'] = themeId;
    try {
      this.document.defaultView?.localStorage?.setItem(ThemeService.STORAGE_KEY, themeId);
    } catch {
      // Storage can be unavailable (private mode); the DOM switch still works.
    }
  }

  /**
   * Returns the theme cached by a previous `apply()`, or `null` when none is
   * stored or the stored value is not a known theme (e.g. after a downgrade).
   */
  restoreCached(): ThemeId | null {
    try {
      const stored = this.document.defaultView?.localStorage?.getItem(ThemeService.STORAGE_KEY);
      return stored && this.isValid(stored) ? (stored as ThemeId) : null;
    } catch {
      return null;
    }
  }

  /** Coerces any input value into a known theme, defaulting unknown ones. */
  private normalize(theme: unknown): ThemeId {
    return this.isValid(theme) ? (theme as ThemeId) : DEFAULT_THEME;
  }

  /** Type guard: true only for the exact lowercase ids of the backend contract. */
  private isValid(theme: unknown): theme is ThemeId {
    return typeof theme === 'string' && (THEMES as readonly string[]).includes(theme);
  }
}
