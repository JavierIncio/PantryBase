import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { App } from './app';
import { SessionState } from './core/auth/session.state';
import { ProfileService } from './core/user/profile.service';
import { UserPreferences } from './core/user/profile.models';

const dataset = () => (document.documentElement.dataset as DOMStringMap)['theme'];

const PREFERENCES: UserPreferences = {
  filterMode: 'LAX',
  coverageThreshold: 80,
  diet: 'BALANCED',
  themeId: 'green',
};

describe('App', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => {
    delete (document.documentElement.dataset as DOMStringMap)['theme'];
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('restores the cached theme on startup so the first paint never flashes', () => {
    window.localStorage.setItem('pantry.themeId', 'rose');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(dataset()).toBe('rose');
  });

  it('applies the default theme when nothing is cached', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(dataset()).toBe('blue');
  });

  it('syncs the theme from the user preferences once a session exists', async () => {
    const getPreferences = vi.fn(() => of(PREFERENCES));
    TestBed.overrideProvider(ProfileService, { useValue: { getPreferences } });
    TestBed.inject(SessionState).setAccessToken('token-123');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getPreferences).toHaveBeenCalled();
    expect(dataset()).toBe('green');
  });

  it('keeps the cached theme when the preferences fetch fails', async () => {
    window.localStorage.setItem('pantry.themeId', 'amber');
    TestBed.overrideProvider(ProfileService, {
      useValue: { getPreferences: vi.fn(() => throwError(() => new Error('backend down'))) },
    });
    TestBed.inject(SessionState).setAccessToken('token-456');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // The catchError path swallows the failure and the cached theme survives.
    expect(dataset()).toBe('amber');
  });
});
