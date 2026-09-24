import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { RegisterPage } from './register-page';
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

const VALID_VALUES = {
  username: 'ada',
  email: 'ada@example.com',
  password: 'secret42',
  confirmPassword: 'secret42',
  firstName: 'Ada',
  lastName: 'Lovelace',
};

describe('RegisterPage', () => {
  let authStub: { register: ReturnType<typeof vi.fn> };

  const configure = () => {
    authStub = { register: vi.fn() };
    TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [
        { provide: AuthService, useValue: authStub },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  };

  const submit = (fixture: ComponentFixture<RegisterPage>) =>
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

  it('sends the exact RegisterRequest (no confirmPassword) and navigates on success', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.register.mockReturnValue(of(USER));
    const fixture = TestBed.createComponent(RegisterPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue(VALID_VALUES);
    fixture.detectChanges();
    submit(fixture);

    const payload = authStub.register.mock.calls[0][0];
    expect(payload).toEqual({
      username: 'ada',
      email: 'ada@example.com',
      password: 'secret42',
      firstName: 'Ada',
      lastName: 'Lovelace',
    });
    // confirmPassword is a client-only helper field: it must never leak.
    expect(payload).not.toHaveProperty('confirmPassword');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pantry');
  });

  it('keeps the submit button disabled while the passwords differ', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(RegisterPage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector('button[type="submit"]') as HTMLButtonElement;

    fixture.componentInstance.form.setValue({ ...VALID_VALUES, confirmPassword: 'nope' });
    fixture.detectChanges();

    expect(button.disabled).toBe(true);
    // Even a manual form submission must not reach the API while mismatched.
    submit(fixture);
    expect(authStub.register).not.toHaveBeenCalled();
  });

  it('reveals the mismatch error on the confirm field only after interaction', async () => {
    await configure();
    await TestBed.compileComponents();

    const fixture = TestBed.createComponent(RegisterPage);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    fixture.componentInstance.form.setValue({ ...VALID_VALUES, confirmPassword: 'nope' });
    fixture.detectChanges();

    // Untouched field: no mismatch error yet, despite the form being invalid.
    expect(host.textContent).not.toContain('Las contraseñas no coinciden');

    // Submitting marks every field touched, revealing the violation.
    submit(fixture);
    fixture.detectChanges();
    expect(host.textContent).toContain('Las contraseñas no coinciden');

    // Correcting the confirm field clears the mismatch on the next validation.
    fixture.componentInstance.form.controls.confirmPassword.setValue('secret42');
    fixture.detectChanges();
    expect(host.textContent).not.toContain('Las contraseñas no coinciden');
  });

  it('enables the button again once the passwords match and posts the plain payload', async () => {
    await configure();
    await TestBed.compileComponents();

    authStub.register.mockReturnValue(of(USER));
    const fixture = TestBed.createComponent(RegisterPage);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector('button[type="submit"]') as HTMLButtonElement;

    fixture.componentInstance.form.setValue({ ...VALID_VALUES, confirmPassword: 'nope' });
    fixture.detectChanges();
    expect(button.disabled).toBe(true);

    fixture.componentInstance.form.controls.confirmPassword.setValue('secret42');
    fixture.detectChanges();
    expect(button.disabled).toBe(false);

    submit(fixture);
    expect(authStub.register).toHaveBeenCalledTimes(1);
    expect(authStub.register.mock.calls[0][0]).not.toHaveProperty('confirmPassword');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pantry');
  });
});
