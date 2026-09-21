import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OAuthRedirectPage, readAccessTokenFromHash } from './oauth-redirect-page';
import { jwtInterceptor } from '../../core/auth/jwt.interceptor';
import { SessionState } from '../../core/auth/session.state';
import { UserResponse } from '../../core/auth/auth.models';

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: null,
  lastName: null,
  roles: ['USER'],
};

describe('OAuthRedirectPage', () => {
  let http: HttpTestingController;
  let session: SessionState;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OAuthRedirectPage],
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionState);
  });

  afterEach(() => {
    http.verify();
  });

  it('parses the access token from the URL fragment', () => {
    expect(readAccessTokenFromHash('#accessToken=token-123')).toBe('token-123');
    expect(readAccessTokenFromHash('#state=abc&accessToken=token-123&scope=x')).toBe('token-123');
    expect(readAccessTokenFromHash('#state=abc&scope=x')).toBeNull();
    expect(readAccessTokenFromHash('')).toBeNull();
  });

  it('restores the session from the fragment token and navigates to /pantry', async () => {
    window.location.hash = '#accessToken=fragment-token';
    const fixture = TestBed.createComponent(OAuthRedirectPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const meReq = http.expectOne('/api/users/me');
    // The interceptor attaches the fragment token as a Bearer credential.
    expect(meReq.request.headers.get('Authorization')).toBe('Bearer fragment-token');
    meReq.flush(USER);

    expect(session.accessToken()).toBe('fragment-token');
    expect(session.user()).toEqual(USER);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pantry');
    // The token is scrubbed from the address bar on the way out.
    expect(window.location.hash).toBe('');
  });

  it('sends the user to /login when the fragment has no token', () => {
    window.location.hash = '#state=abc';
    const fixture = TestBed.createComponent(OAuthRedirectPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(session.isAuthenticated()).toBe(false);
  });

  it('clears the session and returns to /login when the token cannot be used', async () => {
    window.location.hash = '#accessToken=expired-token';
    const fixture = TestBed.createComponent(OAuthRedirectPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    http.expectOne('/api/users/me').flush(
      {
        timestamp: '2026-01-01T00:00:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Unauthorized',
        path: '/api/users/me',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    // The JWT interceptor attempts the refresh cookie before giving up; with no
    // valid cookie the refresh fails too and the interceptor clears the session.
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

    expect(session.isAuthenticated()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
