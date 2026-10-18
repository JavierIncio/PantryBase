import { TestBed } from '@angular/core/testing';
import { DEFAULT_THEME, THEMES } from './theme.types';
import { ThemeService } from './theme.service';

const dataset = () => (document.documentElement.dataset as DOMStringMap)['theme'];

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
    window.localStorage.clear();
  });

  afterEach(() => {
    delete (document.documentElement.dataset as DOMStringMap)['theme'];
  });

  it('applies the theme to the document root and caches it for later restores', () => {
    service.apply('amber');

    expect(dataset()).toBe('amber');
    expect(window.localStorage.getItem('pantry.themeId')).toBe('amber');
  });

  it('falls back to the default theme for unknown ids instead of breaking the UI', () => {
    service.apply('neon' as never);

    expect(dataset()).toBe(DEFAULT_THEME);
    expect(window.localStorage.getItem('pantry.themeId')).toBe(DEFAULT_THEME);
  });

  it('restores the cached theme after a reload', () => {
    service.apply('rose');

    expect(service.restoreCached()).toBe('rose');
  });

  it('returns null when nothing was cached or the stored value is not a known theme', () => {
    expect(service.restoreCached()).toBeNull();

    window.localStorage.setItem('pantry.themeId', 'magenta');
    expect(service.restoreCached()).toBeNull();
  });

  it('keeps applying the theme when localStorage is unavailable (private mode)', () => {
    vi.spyOn(window.localStorage, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    expect(() => service.apply('green')).not.toThrow();
    expect(dataset()).toBe('green');
  });

  it('supports every theme the backend contract declares', () => {
    for (const theme of THEMES) {
      service.apply(theme);
      expect(dataset()).toBe(theme);
    }
  });

  it('resets the theme to the default and clears the cached preference', () => {
    service.apply('amber');
    expect(window.localStorage.getItem('pantry.themeId')).toBe('amber');

    service.reset();

    expect(dataset()).toBe(DEFAULT_THEME);
    expect(window.localStorage.getItem('pantry.themeId')).toBeNull();
  });
});
