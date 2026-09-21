import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';

/**
 * Centered card frame used by the login and registration pages.
 *
 * Both screens live outside the application shell (no toolbar or navigation)
 * but share the same visual layout; the frame also renders the form-level
 * error message derived from `ErrorResponse.message`, so each page only owns
 * its fields and submit logic.
 */
@Component({
  selector: 'app-auth-frame',
  imports: [MatCard, MatCardContent, MatCardHeader, MatCardSubtitle, MatCardTitle, MatIcon],
  styleUrl: './auth-frame.scss',
  templateUrl: './auth-frame.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthFrame {
  /** Card heading, e.g. "Welcome back". */
  readonly title = input.required<string>();

  /** Card subheading with a short contextual hint. */
  readonly subtitle = input('');

  /** Form-level error message taken from the API `ErrorResponse`, or null. */
  readonly errorMessage = input<string | null>(null);
}
