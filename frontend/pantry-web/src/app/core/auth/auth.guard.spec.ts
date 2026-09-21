import { Component } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  NavigationEnd,
  provideRouter,
  Router,
  UrlSegment,
  type GuardResult,
  type MaybeAsync,
  type PartialMatchRouteSnapshot,
  type Route,
} from '@angular/router';
import { filter, firstValueFrom, Observable } from 'rxjs';
import { authGuard } from './auth.guard';
import { jwtInterceptor } from './jwt.interceptor';
import { SessionState } from './session.state';
import { UserResponse } from './auth.models';

/** Minimal target for the '/login' navigation performed by the guard. */
@Component({ template: '' })
class LoginStub {}

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: null,
  lastName: null,
  roles: ['USER'],
};

const TOKENS = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 900,
};

/**
 * The guard outcome: `true` synchronously for an active session, otherwise an
 * `Observable<boolean>` resolving the restore attempt.
 */
type GuardOutcome = MaybeAsync<GuardResult>;

// Angular 22's CanMatchFn takes `(route, segments, currentSnapshot)`; the guard
// ignores the snapshot, so an undefined value is enough for the tests.
const snapshot = undefined as unknown as PartialMatchRouteSnapshot;

// Run the guard inside the TestBed injection context with an empty path match.
const runGuard = (segments: UrlSegment[] = []): GuardOutcome =>
  TestBed.runInInjectionContext(() => authGuard({} as Route, segments, snapshot));

// Helper to normalize the guard outcome into the observable case before testing.
const expectObservable = (result: GuardOutcome): Observable<boolean> => {
  expect(typeof result).not.toBe('boolean');
  return result as Observable<boolean>;
};

describe('authGuard', () => {
  let http: HttpTestingController;
  let session: SessionState;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: LoginStub }]),
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionState);
  });

  afterEach(() => {
    http.verify();
  });

  it('allows navigation when a session is already active', () => {
    session.setAccessToken('access-1');
    session.restore(USER);

    expect(runGuard()).toBe(true);
  });

  it('restores the session from the refresh cookie and allows navigation', async () => {
    const allowed = firstValueFrom(expectObservable(runGuard()));

    const refreshReq = http.expectOne('/api/auth/refresh');
    refreshReq.flush(TOKENS);

    const meReq = http.expectOne('/api/users/me');
    expect(meReq.request.headers.get('Authorization')).toBe('Bearer access-1');
    meReq.flush(USER);

    await expect(allowed).resolves.toBe(true);
    expect(session.isAuthenticated()).toBe(true);
    expect(session.user()).toEqual(USER);
  });

  it('redirects to /login with a returnUrl when the session cannot be restored', async () => {
    const router = TestBed.inject(Router);
    const navigated = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );

    const allowed = firstValueFrom(expectObservable(runGuard()));

    http.expectOne('/api/auth/refresh').flush(
      {
        timestamp: '2026-01-01T00:00:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Unauthorized',
        path: '/api/auth/refresh',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(allowed).resolves.toBe(false);
    await navigated;

    expect(router.url).toContain('/login');
    expect(router.url).toContain('returnUrl');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('remembers the attempted destination in the returnUrl query param', async () => {
    const router = TestBed.inject(Router);
    const navigated = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );

    // Simulate matching the shell route for the /recipes path.
    const guarded = TestBed.runInInjectionContext(() =>
      authGuard({} as Route, [{ path: 'recipes' }] as UrlSegment[], snapshot),
    );
    const pending = firstValueFrom(expectObservable(guarded));

    http.expectOne('/api/auth/refresh').flush(
      {
        timestamp: '2026-01-01T00:00:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Unauthorized',
        path: '/api/auth/refresh',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(pending).resolves.toBe(false);
    await navigated;

    expect(router.url).toContain('/login');
    expect(router.url).toContain('returnUrl=%2Frecipes');
  });
});
