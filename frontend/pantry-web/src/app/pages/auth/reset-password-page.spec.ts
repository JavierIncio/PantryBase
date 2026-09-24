import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ResetPasswordPage } from './reset-password-page';
import { AuthService } from '../../core/auth/auth.service';

/** Fake query parameter map so the reset token can be simulated per test. */
const queryParamMap = (token: string | null) => ({
  get: (key: string) => (key === 'token' ? token : null),
});

/** Backend error body wrapped as an HttpErrorResponse. */
const failedRequest = (message: string) =>
  throwError(
    () =>
      new HttpErrorResponse({
        status: 400,
        statusText: 'Bad Request',
        error: {
          timestamp: '2026-01-01T00:00:00Z',
          status: 400,
          error: 'Bad Request',
          message,
          path: '/api/auth/password-reset',
        },
      }),
  );

describe('ResetPasswordPage', () => {
  let authStub: { resetPassword: ReturnType<typeof vi.fn> };

  const configure = (token: string | null = 'reset-token-1') => {
    authStub = { resetPassword: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ResetPasswordPage],
      providers: [
        { provide: AuthService, useValue: authStub },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: queryParamMap(token) } },
        },
      ],
    });
  };

  const submit = (fixture: ComponentFixture<ResetPasswordPage>) =>
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

  it('shows a direct error state instead of a form when the URL has no token', async () => {
    await configure(null);
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(fixture.componentInstance.missingToken).toBe(true);
    expect(host.querySelector('form')).toBeNull();
    expect(host.textContent).toContain('El enlace de restablecimiento no es válido');
    expect(host.querySelector('a[href="/login"]')).toBeTruthy();
  });

  it('consumes the token with the new password and redirects to the login on success', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.resetPassword.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(ResetPasswordPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      newPassword: 'newpass42',
      confirmPassword: 'newpass42',
    });
    submit(fixture);

    expect(authStub.resetPassword).toHaveBeenCalledWith('reset-token-1', 'newpass42');
    // The success flag travels as ephemeral router state, never in the URL.
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login', {
      state: { resetSuccess: true },
    });
  });

  it('does not call the API while the passwords differ', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      newPassword: 'newpass42',
      confirmPassword: 'nope',
    });
    submit(fixture);
    fixture.detectChanges();

    expect(authStub.resetPassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Las contraseñas no coinciden');
  });

  it('rejects a password shorter than 8 characters without calling the API', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ newPassword: 'short', confirmPassword: 'short' });
    submit(fixture);
    fixture.detectChanges();

    expect(authStub.resetPassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Usa al menos 8 caracteres.');
  });

  it('surfaces an invalid or expired token 400 on the form', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.resetPassword.mockReturnValue(failedRequest('Invalid or expired token'));
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      newPassword: 'newpass42',
      confirmPassword: 'newpass42',
    });
    submit(fixture);
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.auth-error') as HTMLElement;
    expect(alert).toBeTruthy();
    expect(alert.textContent).toContain('Invalid or expired token');
    // The user can correct the value or request a new link: the form stays.
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
  });

  it('offers a back link to the login page from the form state', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/login"]') as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(link.textContent).toContain('Volver al inicio de sesión');
  });
});
