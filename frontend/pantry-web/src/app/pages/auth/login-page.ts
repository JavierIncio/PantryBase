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

/**
 * Public login form (username or email + password).
 *
 * Submits to `POST /api/auth/login` and, on success, navigates to the URL the
 * auth guard recorded in `returnUrl` (defaulting to `/pantry`). Any API error
 * is rendered from `ErrorResponse.message` so the user sees the backend's own
 * wording instead of a generic network error.
 */
@Component({
  selector: 'app-login-page',
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
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Reactive form group of the page, exposed for template binding and tests. */
  readonly form = this.fb.group({
    loginMethod: ['', Validators.required],
    password: ['', Validators.required],
  });

  /** True while the login request is in flight (disables the submit button). */
  readonly submitting = signal(false);

  /** Form-level error message received from the API, or null. */
  readonly errorMessage = signal<string | null>(null);

  /** Destination recorded by the auth guard, sanitized against open redirects. */
  private readonly returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

  /** Sends the credentials and navigates away on success. */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { loginMethod, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.login({ loginMethod, password }).subscribe({
      next: () => this.router.navigateByUrl(this.safeDestination()),
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(
          toErrorResponse(error).message || 'Unable to sign in. Please try again.',
        );
      },
    });
  }

  /**
   * Returns the recorded destination only when it is a same-origin relative
   * path; anything else (including protocol-relative `//host` values) falls
   * back to the pantry so the login flow can never be used as an open redirect.
   */
  private safeDestination(): string {
    if (!this.returnUrl) {
      return '/pantry';
    }
    return this.returnUrl.startsWith('/') && !this.returnUrl.startsWith('//')
      ? this.returnUrl
      : '/pantry';
  }
}
