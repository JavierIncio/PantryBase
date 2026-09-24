import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { toErrorResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFrame } from './auth-frame';
import {
  createPasswordMatchValidator,
  PasswordMismatchErrorStateMatcher,
} from './password-match.validator';

/**
 * Public "reset password" page reached from the emailed link.
 *
 * The backend generates `{base}/auth/reset-password?token=<hex>`, so the token
 * is read from the query parameter of this exact route and passed straight to
 * `POST /api/auth/password-reset` — it is never stored in state or storage,
 * logged, or echoed back to the user. Without a token the page renders an
 * error state instead of a dead form.
 */
@Component({
  selector: 'app-reset-password-page',
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
  templateUrl: './reset-password-page.html',
  styleUrl: './reset-password-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /**
   * Reset token from the emailed link (`?token=<hex>`).
   *
   * Kept as a private field only: the form reads it for the single submit and
   * nothing else ever sees it.
   */
  private readonly token = this.route.snapshot.queryParamMap.get('token');

  /** True when the URL carries no token: the link itself is unusable. */
  readonly missingToken = this.token === null;

  /** Reactive form group of the page, exposed for template binding and tests. */
  readonly form = this.fb.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: createPasswordMatchValidator('newPassword') },
  );

  /** True while the reset request is in flight (disables the submit button). */
  readonly submitting = signal(false);

  /** Form-level error message received from the API (invalid/expired token), or null. */
  readonly errorMessage = signal<string | null>(null);

  /**
   * Error-state matcher for the confirm field (bound in the template).
   *
   * Same bridging rationale as the register page: Material 22 only projects
   * `mat-error` when the field's own control is errored, and the group-level
   * password mismatch never invalidates the control itself. See
   * {@link PasswordMismatchErrorStateMatcher}.
   */
  readonly passwordMismatchErrorStateMatcher = new PasswordMismatchErrorStateMatcher();

  /** Consumes the token with the new password and redirects to the login. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.token) {
      return;
    }

    const { newPassword } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.resetPassword(this.token, newPassword).subscribe({
      next: () =>
        this.router.navigateByUrl('/login', {
          // Ephemeral: the login page reads it for a single welcome-back
          // banner and it never reaches the URL or any storage.
          state: { resetSuccess: true },
        }),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          toErrorResponse(error).message || 'El enlace no es válido o ha caducado.',
        );
      },
    });
  }
}
