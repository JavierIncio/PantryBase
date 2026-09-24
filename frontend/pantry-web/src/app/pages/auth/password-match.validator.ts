import { AbstractControl, ValidationErrors } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';

/**
 * Cross-field validator that couples `password` and `confirmPassword`.
 *
 * Runs at the form-group level on purpose: it reports the `passwordMismatch`
 * error on the group instead of on either field, so each control keeps its own
 * single-field validators (required, length) untouched. The template surfaces
 * the mismatch on the confirm field once it has been touched, matching the
 * page's error-display convention.
 */
export function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value as string | undefined;
  const confirmPassword = group.get('confirmPassword')?.value as string | undefined;
  return password === confirmPassword ? null : { passwordMismatch: true };
}

/**
 * ErrorStateMatcher that reports the confirm field as error-prone both when it
 * has its own validation errors and when the group reports `passwordMismatch`.
 *
 * Material 22 only renders {@link MatError} content when the form-field's own
 * control is in an error state (`_getSubscriptMessageType()` returns 'error'),
 * and a group-level cross-field error never invalidates the control itself.
 * This matcher bridges the two: it keeps the default error-state semantics
 * and additionally reports the field as errored on a group mismatch, so the
 * message renders exactly when the template condition holds.
 */
export class PasswordMismatchErrorStateMatcher implements ErrorStateMatcher {
  /** True when {@link control} has been touched and is invalid or mismatched. */
  isErrorState(control: AbstractControl | null): boolean {
    return (
      !!control &&
      control.touched &&
      (control.invalid || !!control.parent?.hasError('passwordMismatch'))
    );
  }
}
