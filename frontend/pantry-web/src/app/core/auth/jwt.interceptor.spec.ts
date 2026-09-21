import { Component } from '@angular/core';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, NavigationEnd } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { jwtInterceptor } from './jwt.interceptor';
import { SessionState } from './session.state';

/** Minimal target for the '/login' navigation performed by the interceptor. */
@Component({ template: '' })
class LoginStub {}

const TOKENS = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const UNAUTHORIZED = {
  timestamp: '2026-01-01T00:00:00Z',
  status: 401,
  error: 'Unauthorized',
  message: 'Invalid credentials',
  path: '/api/data',
};

describe('jwtInterceptor', () => {
  let client: HttpClient;
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

    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionState);
  });

  afterEach(() => {
    http.verify();
  });

  it('adds the Bearer header when a token is available', async () => {
    session.setAccessToken('access-1');

    const result = firstValueFrom(client.get<{ ok: boolean }>('/api/data'));
    const req = http.expectOne('/api/data');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({ ok: true });

    await expect(result).resolves.toEqual({ ok: true });
  });

  it('does not add a Bearer header when the session has no token', async () => {
    const result = firstValueFrom(client.get('/api/data'));
    const req = http.expectOne('/api/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ ok: true });
    await result;
  });

  it('never attaches the Bearer header to the auth token endpoints', async () => {
    session.setAccessToken('access-1');

    const login = firstValueFrom(client.post('/api/auth/login', {}));
    const loginReq = http.expectOne('/api/auth/login');
    expect(loginReq.request.headers.has('Authorization')).toBe(false);
    loginReq.flush(TOKENS);
    await login;
  });

  it('refreshes the token on 401 and replays the original request exactly once', async () => {
    session.setAccessToken('stale');

    const result = firstValueFrom(client.get<{ ok: boolean }>('/api/data'));
    http.expectOne('/api/data').flush(UNAUTHORIZED, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = http.expectOne('/api/auth/refresh');
    expect(refreshReq.request.headers.has('Authorization')).toBe(false);
    refreshReq.flush({ ...TOKENS, accessToken: 'access-2' });

    const retryReq = http.expectOne('/api/data');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer access-2');
    retryReq.flush({ ok: true });

    await expect(result).resolves.toEqual({ ok: true });
    expect(session.accessToken()).toBe('access-2');
  });

  it('single-flights the refresh when several requests hit 401 concurrently', async () => {
    session.setAccessToken('stale');

    const first = firstValueFrom(client.get('/api/data'));
    const second = firstValueFrom(client.get('/api/other'));

    http.match('/api/data')[0].flush(UNAUTHORIZED, { status: 401, statusText: 'Unauthorized' });
    http.match('/api/other')[0].flush(UNAUTHORIZED, { status: 401, statusText: 'Unauthorized' });

    // Exactly one refresh request satisfies both callers.
    const refreshReq = http.expectOne('/api/auth/refresh');
    refreshReq.flush({ ...TOKENS, accessToken: 'access-2' });

    const retryOne = http.expectOne('/api/data');
    expect(retryOne.request.headers.get('Authorization')).toBe('Bearer access-2');
    retryOne.flush({ ok: 1 });
    const retryTwo = http.expectOne('/api/other');
    expect(retryTwo.request.headers.get('Authorization')).toBe('Bearer access-2');
    retryTwo.flush({ ok: 2 });

    await expect(first).resolves.toEqual({ ok: 1 });
    await expect(second).resolves.toEqual({ ok: 2 });
  });

  it('clears the session and redirects to /login when the refresh fails', async () => {
    const router = TestBed.inject(Router);
    const navigated = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );

    session.setAccessToken('stale');
    const result = firstValueFrom(client.get<{ ok: boolean }>('/api/data'));

    http.expectOne('/api/data').flush(UNAUTHORIZED, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne('/api/auth/refresh')
      .flush(
        { ...UNAUTHORIZED, path: '/api/auth/refresh' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    await navigated;

    expect(session.isAuthenticated()).toBe(false);
    expect(session.accessToken()).toBeNull();
    expect(router.url).toContain('/login');
  });

  it('does not trigger a refresh when login itself returns 401', async () => {
    const result = firstValueFrom(
      client.post('/api/auth/login', { loginMethod: 'ada', password: 'wrong' }),
    );

    http
      .expectOne('/api/auth/login')
      .flush(UNAUTHORIZED, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    http.expectNone('/api/auth/refresh');
  });
});
