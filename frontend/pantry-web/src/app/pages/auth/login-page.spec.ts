import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginPage } from './login-page';
import { AuthService } from '../../core/auth/auth.service';
import { UserResponse } from '../../core/auth/auth.models';

const USER: UserResponse = {
  id: 1,
  username: 'ada',
  email: 'ada@example.com',
  firstName: null,
  lastName: null,
  roles: ['USER'],
};

/** Fake query parameter map so the guard redirect can be simulated. */
const queryParamMap = (entries: Array<[string, string]>) => ({
  get: (key: string) => entries.find(([k]) => k === key)?.[1] ?? null,
});

describe('LoginPage', () => {
  let authStub: { login: ReturnType<typeof vi.fn> };

  const configure = (returnUrl: string | null = null) => {
    authStub = { login: vi.fn() };
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        { provide: AuthService, useValue: authStub },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // Last provider for ActivatedRoute wins the router's own snapshot
        // provider, so the guard redirect can be simulated per test.
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: queryParamMap(returnUrl ? [['returnUrl', returnUrl]] : []) },
          },
        },
      ],
    });
  };

  it('submits valid credentials and navigates to the default destination', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.login.mockReturnValue(of(USER));
    const fixture = TestBed.createComponent(LoginPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada', password: 'secret' });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(authStub.login).toHaveBeenCalledWith({ loginMethod: 'ada', password: 'secret' });
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pantry');
  });

  it('navigates to the returnUrl recorded by the guard when one is present', async () => {
    await configure('/recipes');
    await TestBed.compileComponents();

    authStub.login.mockReturnValue(of(USER));
    const fixture = TestBed.createComponent(LoginPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada', password: 'secret' });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/recipes');
  });

  it('ignores a protocol-relative returnUrl to avoid open redirects', async () => {
    await configure('//evil.example.com');
    await TestBed.compileComponents();

    authStub.login.mockReturnValue(of(USER));
    const fixture = TestBed.createComponent(LoginPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada', password: 'secret' });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/pantry');
  });

  it('shows the backend ErrorResponse message when login fails with 401', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              timestamp: '2026-01-01T00:00:00Z',
              status: 401,
              error: 'Unauthorized',
              message: 'Invalid credentials',
              path: '/api/auth/login',
            },
          }),
      ),
    );
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada', password: 'wrong' });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.auth-error') as HTMLElement;
    expect(alert).toBeTruthy();
    expect(alert.textContent).toContain('Invalid credentials');
  });

  it('does not call the service while the form is invalid', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(authStub.login).not.toHaveBeenCalled();
  });
});
