import { HttpClient, HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { SessionState } from './session.state';
import { UserResponse } from './auth.models';

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: 'Ada',
  lastName: null,
  roles: ['USER'],
};

const TOKENS = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const ERROR_BODY = {
  timestamp: '2026-01-01T00:00:00Z',
  status: 401,
  error: 'Unauthorized',
  message: 'Invalid credentials',
  path: '/api/auth/login',
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let session: SessionState;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionState);
  });

  afterEach(() => {
    http.verify();
  });

  it('login stores the tokens, loads the profile and completes the session', async () => {
    const result = firstValueFrom(service.login({ loginMethod: 'ada', password: 'secret' }));

    const loginReq = http.expectOne('/api/auth/login');
    expect(loginReq.request.method).toBe('POST');
    expect(loginReq.request.body).toEqual({ loginMethod: 'ada', password: 'secret' });
    loginReq.flush(TOKENS);

    const meReq = http.expectOne('/api/users/me');
    meReq.flush(USER);

    await expect(result).resolves.toEqual(USER);
    expect(session.accessToken()).toBe('access-1');
    expect(session.user()).toEqual(USER);
    expect(session.isAuthenticated()).toBe(true);
  });

  it('login surfaces the backend ErrorResponse message on 401', async () => {
    const result = firstValueFrom(service.login({ loginMethod: 'ada', password: 'wrong' }));

    const loginReq = http.expectOne('/api/auth/login');
    loginReq.flush(ERROR_BODY, { status: 401, statusText: 'Unauthorized' });

    await expect(result).rejects.toMatchObject({ status: 401, error: ERROR_BODY });
    expect(session.isAuthenticated()).toBe(false);
  });

  it('register chains the profile request after the token exchange', async () => {
    const result = firstValueFrom(
      service.register({
        username: 'ada',
        email: 'ada@example.com',
        password: 'secret123',
        firstName: 'Ada',
      }),
    );

    const postReq = http.expectOne('/api/auth/register');
    expect(postReq.request.body).toEqual({
      username: 'ada',
      email: 'ada@example.com',
      password: 'secret123',
      firstName: 'Ada',
    });
    postReq.flush(TOKENS);

    http.expectOne('/api/users/me').flush(USER);

    await expect(result).resolves.toEqual(USER);
    expect(session.user()?.email).toBe('ada@example.com');
  });

  it('refreshTokens is single-flight: concurrent callers share one HTTP call', async () => {
    const first = firstValueFrom(service.refreshTokens());
    const second = firstValueFrom(service.refreshTokens());

    // A single request must satisfy both subscribers.
    const refreshReq = http.expectOne('/api/auth/refresh');
    expect(refreshReq.request.method).toBe('POST');
    expect(refreshReq.request.headers.has('Authorization')).toBe(false);
    refreshReq.flush(TOKENS);

    await expect(first).resolves.toEqual(TOKENS);
    await expect(second).resolves.toEqual(TOKENS);
    expect(session.accessToken()).toBe('access-1');
  });

  it('refreshTokens issues a fresh call after the previous one completed', async () => {
    let call = firstValueFrom(service.refreshTokens());
    http.expectOne('/api/auth/refresh').flush(TOKENS);
    await call;

    call = firstValueFrom(service.refreshTokens());
    http.expectOne('/api/auth/refresh').flush({ ...TOKENS, accessToken: 'access-2' });
    await call;

    expect(session.accessToken()).toBe('access-2');
  });

  it('logout clears the session on 204', async () => {
    session.setAccessToken('access-1');
    session.restore(USER);

    const result = firstValueFrom(service.logout());
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });

    await result;
    expect(session.isAuthenticated()).toBe(false);
    expect(session.user()).toBeNull();
  });

  it('me returns the profile of the current user', async () => {
    const result = firstValueFrom(service.me());
    http.expectOne('/api/users/me').flush(USER);
    await expect(result).resolves.toEqual(USER);
  });

  it('me surfaces the ErrorResponse body on failure', async () => {
    const result = firstValueFrom(service.me());
    http.expectOne('/api/users/me').flush(
      {
        ...ERROR_BODY,
        status: 500,
        error: 'Server error',
        message: 'Internal error',
        path: '/api/users/me',
      },
      {
        status: 500,
        statusText: 'Server Error',
      },
    );

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
  });
});
