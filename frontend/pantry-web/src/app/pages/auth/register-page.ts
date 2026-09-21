import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { toErrorResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFrame } from './auth-frame';

/**
 * Public registration form (username, email, password and optional names).
 *
 * Validation mirrors the backend constraints on {@link RegisterRequest}
 * (`@Size`/`@Email` in the API) so the user gets immediate feedback instead of
 * a round-trip for trivial mistakes. On success the user is sent straight to
 * the pantry, same as after login.
 */
@Component({
  selector: 'app-register-page',
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
  templateUrl: './register-page.html',
  styleUrl: './register-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Reactive form group of the page, exposed for template binding and tests. */
  readonly form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    firstName: ['', Validators.maxLength(50)],
    lastName: ['', Validators.maxLength(50)],
  });

  /** True while the registration request is in flight (disables the button). */
  readonly submitting = signal(false);

  /** Form-level error message received from the API, or null. */
  readonly errorMessage = signal<string | null>(null);

  /** Creates the account and navigates to the pantry on success. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { username, email, password, firstName, lastName } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.register({ username, email, password, firstName, lastName }).subscribe({
      next: () => this.router.navigateByUrl('/pantry'),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          toErrorResponse(error).message || 'Unable to create the account. Please try again.',
        );
      },
    });
  }
}
