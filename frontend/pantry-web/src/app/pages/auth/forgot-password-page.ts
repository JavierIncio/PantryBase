import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { toErrorResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFrame } from './auth-frame';

/**
 * Public "forgot password" form: asks for the username or email only.
 *
 * The backend always answers `POST /api/auth/password-reset-token` with 204 —
 * even for unknown accounts or send failures — so this page collapses every
 * non-validation outcome into the same generic notice. The OWASP-style golden
 * rule is never to reveal whether an account exists or the email was sent;
 * the only exception is a 400 (blank input), which is a contract violation,
 * not an account leak, and is rendered as a field error.
 */
@Component({
  selector: 'app-forgot-password-page',
  imports: [
    AuthFrame,
    MatButton,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    MatProgressSpinner,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './forgot-password-page.html',
  styleUrl: './forgot-password-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);

  /** The single field of the flow; the backend accepts username OR email. */
  readonly form = this.fb.group({
    loginMethod: ['', Validators.required],
  });

  /** True while the request is in flight (disables the submit button). */
  readonly submitting = signal(false);

  /**
   * True once the request completed with a non-enumerable outcome.
   *
   * Replaces the form with the generic notice and the back-to-login link on
   * success AND on unexpected failures: switching to the same final state in
   * both cases is what keeps the endpoint from leaking account existence.
   */
  readonly sent = signal(false);

  /** Sends the loginMethod; on a validation 400 it stays as a field error. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { loginMethod } = this.form.getRawValue();
    this.submitting.set(true);

    this.auth.requestPasswordReset(loginMethod).subscribe({
      next: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 400) {
          // Blank-body validation: keep the form, show the violation on the
          // field. Any other status is deliberately indistinguishable from
          // success below.
          this.form.controls.loginMethod.setErrors({ rejected: toErrorResponse(error).message });
          this.submitting.set(false);
          return;
        }
        this.sent.set(true);
      },
    });
  }
}
