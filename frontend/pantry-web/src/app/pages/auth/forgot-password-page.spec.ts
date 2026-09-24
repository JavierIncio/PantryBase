import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ForgotPasswordPage } from './forgot-password-page';
import { AuthService } from '../../core/auth/auth.service';

/** Backend validation error body wrapped as an HttpErrorResponse. */
const failedRequest = (status: number, message: string) =>
  throwError(
    () =>
      new HttpErrorResponse({
        status,
        statusText: status === 500 ? 'Server Error' : 'Bad Request',
        error: {
          timestamp: '2026-01-01T00:00:00Z',
          status,
          error: 'Error',
          message,
          path: '/api/auth/password-reset-token',
        },
      }),
  );

describe('ForgotPasswordPage', () => {
  let authStub: { requestPasswordReset: ReturnType<typeof vi.fn> };

  const configure = () => {
    authStub = { requestPasswordReset: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ForgotPasswordPage],
      providers: [
        { provide: AuthService, useValue: authStub },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  };

  const submit = (fixture: ComponentFixture<ForgotPasswordPage>) =>
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

  it('requires the loginMethod field before calling the API', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    submit(fixture);
    fixture.detectChanges();

    expect(authStub.requestPasswordReset).not.toHaveBeenCalled();
    expect(host.textContent).toContain('Introduce tu email o nombre de usuario.');
  });

  it('sends the loginMethod and replaces the form with the generic notice on 204', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.requestPasswordReset.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada' });
    submit(fixture);
    fixture.detectChanges();

    expect(authStub.requestPasswordReset).toHaveBeenCalledWith('ada');
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.auth-generic')?.textContent).toContain(
      'Si existe una cuenta con ese email o username, recibirás un enlace de restablecimiento.',
    );
    // The form is gone; only the notice and the back link remain.
    expect(host.querySelector('form')).toBeNull();
  });

  it('shows the same generic notice when the send fails, without revealing the failure', async () => {
    await configure();
    await TestBed.compileComponents();

    // A 5xx/network failure must be indistinguishable from an account that
    // does not exist: no error copy, no field error — just the generic notice.
    authStub.requestPasswordReset.mockReturnValue(failedRequest(500, 'Server exploded'));
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada@example.com' });
    submit(fixture);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).not.toContain('Server exploded');
    expect(host.querySelector('.auth-generic')).toBeTruthy();
    expect(host.querySelector('form')).toBeNull();
  });

  it('keeps the form and shows a validation 400 as a field error', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.requestPasswordReset.mockReturnValue(
      failedRequest(400, 'loginMethod must not be blank'),
    );
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();

    // Whitespace-only input passes the required validator client-side but is
    // blank for the backend, producing the contract-level 400.
    fixture.componentInstance.form.setValue({ loginMethod: '   ' });
    submit(fixture);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.form.controls.loginMethod.hasError('rejected')).toBe(true);
    expect(host.querySelector('mat-error')?.textContent).toContain('loginMethod must not be blank');
    // A validation rejection is not an account leak: the form stays editable.
    expect(host.querySelector('form')).toBeTruthy();
    expect(host.querySelector('.auth-generic')).toBeNull();
  });

  it('offers a back link to the login page in both states', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.requestPasswordReset.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    const before = host.querySelector('a[href="/login"]') as HTMLAnchorElement;
    expect(before).toBeTruthy();

    fixture.componentInstance.form.setValue({ loginMethod: 'ada' });
    submit(fixture);
    fixture.detectChanges();

    const after = fixture.nativeElement.querySelector('a[href="/login"]') as HTMLAnchorElement;
    expect(after).toBeTruthy();
    expect(after.textContent).toContain('Volver al inicio de sesión');
  });
});
