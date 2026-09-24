import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormArray,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ErrorStateMatcher, MatOption } from '@angular/material/core';
import { forkJoin, map, startWith } from 'rxjs';
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ChangePasswordRequest, toErrorResponse, UserResponse } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { SessionState } from '../../core/auth/session.state';
import {
  Allergen,
  Diet,
  FilterMode,
  UpdateProfileRequest,
  UserPreferences,
} from '../../core/user/profile.models';
import { ProfileService } from '../../core/user/profile.service';
import {
  createPasswordMatchValidator,
  PasswordMismatchErrorStateMatcher,
} from '../auth/password-match.validator';

const FILTER_MODE_OPTIONS: ReadonlyArray<{ value: FilterMode; label: string }> = [
  { value: 'STRICT', label: 'Strict' },
  { value: 'LAX', label: 'Lax' },
];

const DIET_OPTIONS: ReadonlyArray<{ value: Diet; label: string }> = [
  { value: 'BALANCED', label: 'Balanced' },
  { value: 'HIGH_FIBER', label: 'High fiber' },
  { value: 'HIGH_PROTEIN', label: 'High protein' },
  { value: 'LOW_CARB', label: 'Low carb' },
  { value: 'LOW_FAT', label: 'Low fat' },
  { value: 'LOW_SODIUM', label: 'Low sodium' },
];

/**
 * ErrorStateMatcher for the username field, bound in the template.
 *
 * Material 22 renders `mat-error` only when the form-field's own control
 * reports an error state, and a server-side `taken` error (set with
 * `setErrors` after a failed save) never flips the control to touched.
 * Keeping the default "invalid + touched" rule for length validations while
 * adding `hasError('taken')` surfaces the backend message even on an
 * untouched field.
 */
class UsernameErrorStateMatcher implements ErrorStateMatcher {
  /** True when the field is invalid after interaction or rejected by the server. */
  isErrorState(control: AbstractControl | null): boolean {
    return !!control && ((control.touched && control.invalid) || control.hasError('taken'));
  }
}

/**
 * Profile page: email header plus the Identity, Preferences and Allergy
 * exclusions cards.
 *
 * Both settings sections load their own data on init so a failing backend call
 * never blocks the other, and each card surfaces an inline error with a manual
 * retry. Saving is explicit (no auto-save): the Save buttons stay disabled
 * until the user edits something, and a successful PUT re-syncs the form with
 * the server values — which also clears the dirty flag without extra
 * bookkeeping.
 *
 * The Preferences card enforces the domain invariant `filterMode == 'STRICT'`
 * ⟺ `coverageThreshold == 100` (see `wireCoverageInvariant`): the fields can
 * never contradict each other, whatever direction the change comes from.
 *
 * The Identity card pre-fills from the session profile and always sends the
 * FULL identity on save (empty names encode to `null` — clear upstream, see
 * {@link UpdateProfileRequest}); only the username can be left empty, which
 * the backend treats as "keep the current value".
 *
 * The Password card changes the current password (or establishes the first one
 * for OAuth-created accounts): it sends `currentPassword` only to swap an
 * existing password and surfaces 400/409 from the backend as an inline
 * card-level error, keeping the edits so the user can correct and retry.
 */
@Component({
  selector: 'app-profile-page',
  imports: [
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatCheckbox,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
    MatProgressSpinner,
    MatSelect,
    MatSlider,
    MatSliderThumb,
    ReactiveFormsModule,
  ],
  templateUrl: './profile-page.html',
  styleUrl: './profile-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly profile = inject(ProfileService);
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionState);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly filterModeOptions = FILTER_MODE_OPTIONS;
  protected readonly dietOptions = DIET_OPTIONS;

  /** Identity shown in the header: email when present, else the username. */
  protected readonly email = computed(
    () => this.session.user()?.email ?? this.session.user()?.username ?? 'Profile',
  );

  // --- Identity card --------------------------------------------------------

  /**
   * Reactive form of the Identity card, prefilled from the session profile.
   *
   * The username is NOT required on purpose: the backend contract of
   * {@link UpdateProfileRequest} ignores a null username ("keep current"), so
   * clearing the field is a valid no-op, not an error. Min/max length still
   * apply and disable Save while violated.
   */
  readonly identityForm = this.fb.group({
    username: ['', [Validators.minLength(3), Validators.maxLength(20)]],
    firstName: ['', Validators.maxLength(50)],
    lastName: ['', Validators.maxLength(50)],
  });

  /**
   * Error-state matcher for the username field (bound in the template).
   *
   * Required so Material 22 projects the `taken` server error even though
   * the control never became touched during a failed save — see
   * {@link UsernameErrorStateMatcher}.
   */
  readonly usernameErrorStateMatcher = new UsernameErrorStateMatcher();

  /**
   * The last server-confirmed identity, in payload shape.
   *
   * Compared against the live payload to know whether Save would change
   * anything: the user can edit a field and revert it, which leaves the form
   * dirty but the effective payload identical — Save must be disabled then too.
   */
  private readonly identityBaseline = signal<UpdateProfileRequest>({
    username: null,
    firstName: null,
    lastName: null,
  });

  protected readonly identitySaving = signal(false);
  protected readonly identitySaveError = signal<string | null>(null);

  /**
   * True while the effective identity payload differs from the saved one.
   *
   * Compares payloads, not form dirtiness or control values: empty inputs are
   * encoded to `null` before comparison, so clearing an already-empty field is
   * a change-free no-op while clearing a filled field enables Save.
   */
  readonly identityChanged = toSignal(
    this.identityForm.valueChanges.pipe(
      startWith(this.identityForm.getRawValue()),
      map(() => JSON.stringify(this.identityPayload()) !== JSON.stringify(this.identityBaseline())),
    ),
    { initialValue: false },
  );

  // --- Password card -------------------------------------------------------

  /**
   * Reactive form of the password card.
   *
   * `currentPassword` is deliberately optional: password-less OAuth users
   * establish their first password by leaving it empty (backend contract of
   * {@link ChangePasswordRequest}); the backend answers 409 on empty when the
   * account already has a password and 400 when it does not match.
   */
  readonly passwordForm = this.fb.group(
    {
      currentPassword: [''],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: createPasswordMatchValidator('newPassword') },
  );

  protected readonly passwordSaving = signal(false);

  /** Card-level error message received from the API, or null. */
  readonly passwordSaveError = signal<string | null>(null);

  /**
   * Error-state matcher for the confirm field, same rationale as the register
   * page: the group-level mismatch must surface on the field for Material 22
   * to project the `mat-error`. See {@link PasswordMismatchErrorStateMatcher}.
   */
  readonly passwordMismatchErrorStateMatcher = new PasswordMismatchErrorStateMatcher();

  /**
   * Sends the new password; on success resets the card and notifies.
   *
   * `confirmPassword` is a client-only helper field and is deliberately NOT
   * sent. A 400 (mismatch against the current password) or 409 (empty current
   * password while one exists) keeps the edited values and surfaces the
   * backend message inside the card, so the rest of the profile stays intact.
   */
  protected savePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.passwordSaving.set(true);
    this.passwordSaveError.set(null);

    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordSaving.set(false);
        this.passwordForm.reset();
        this.snackBar.open('Contraseña actualizada', 'OK', { duration: 3000 });
      },
      error: (error: HttpErrorResponse) => {
        this.passwordSaving.set(false);
        this.passwordSaveError.set(
          toErrorResponse(error).message || 'No se pudo actualizar la contraseña.',
        );
      },
    });
  }

  // --- Preferences card ----------------------------------------------------

  /** Reactive form of the Preferences card, empty placeholders until loaded. */
  readonly prefsForm = this.fb.group({
    filterMode: this.fb.control<FilterMode>('LAX', Validators.required),
    coverageThreshold: this.fb.control(80, [
      Validators.required,
      Validators.min(0),
      Validators.max(100),
    ]),
    diet: this.fb.control<Diet>('BALANCED', Validators.required),
  });

  protected readonly prefsLoading = signal(true);
  protected readonly prefsLoadError = signal<string | null>(null);
  protected readonly prefsSaveError = signal<string | null>(null);
  protected readonly savingPrefs = signal(false);

  /**
   * True while the filter mode is STRICT: the coverage slider is locked at 100.
   *
   * Kept as a signal feeding the slider's `[disabled]` binding — instead of
   * `control.disable()` — so locking never marks the form dirty or touched and
   * the pristine-load flow is preserved. Updated by `wireCoverageInvariant`.
   */
  protected readonly coverageLocked = signal(false);

  // --- Allergy exclusions card ---------------------------------------------

  /** Allergens of the catalog, in the API-driven order (by name). */
  protected readonly allergens = signal<Allergen[]>([]);
  /** One boolean control per allergen, parallel to `allergens()`. */
  readonly exclusions = new FormArray<FormControl<boolean>>([]);

  protected readonly exclusionsLoading = signal(true);
  protected readonly exclusionsLoadError = signal<string | null>(null);
  protected readonly exclusionsSaveError = signal<string | null>(null);
  protected readonly savingExclusions = signal(false);

  ngOnInit(): void {
    this.wireCoverageInvariant();
    this.loadPreferences();
    this.loadExclusions();
    this.syncIdentity(this.session.user());
  }

  /**
   * Prefills the identity form from the session profile and re-baselines it.
   *
   * `reset()` keeps the form pristine, so Save stays disabled until the user
   * actually types something. The baseline is committed BEFORE `reset()`
   * because `reset` synchronously emits `valueChanges`, which re-evaluates
   * `identityChanged` against the current baseline; after a successful save
   * this method is called again with the server response to turn Save off.
   */
  private syncIdentity(user: UserResponse | null): void {
    const empty = (value: string | null | undefined) => value ?? '';
    this.identityBaseline.set(this.identityPayloadOf(user));
    this.identityForm.reset({
      username: empty(user?.username),
      firstName: empty(user?.firstName),
      lastName: empty(user?.lastName),
    });
  }

  /** Encodes a user profile into request shape (names null when not set). */
  private identityPayloadOf(user: UserResponse | null): UpdateProfileRequest {
    return {
      username: user?.username ?? null,
      firstName: user?.firstName ?? null,
      lastName: user?.lastName ?? null,
    };
  }

  /**
   * Encodes the current form values into the request body.
   *
   * Empty inputs become `null`: the backend clears names but ignores an empty
   * username (keeps the current one). The form thus always sends all three
   * fields explicitly — full replacement, never a partial patch.
   */
  protected identityPayload(): UpdateProfileRequest {
    const { username, firstName, lastName } = this.identityForm.getRawValue();
    const emptyToNull = (value: string) => (value === '' ? null : value);
    return {
      username: emptyToNull(username),
      firstName: emptyToNull(firstName),
      lastName: emptyToNull(lastName),
    };
  }

  /**
   * Sends the identity replacement and re-syncs session and form on success.
   *
   * On success the server response feeds {@link SessionState.restore} so the
   * shell/navigation react immediately, and `syncIdentity` re-baselines the
   * form (pristine again, Save disabled). A 400 is the backend's "username
   * already taken" signal by contract: its message is shown as a field error
   * on the username control — typing in it clears the error through the
   * normal re-validation flow — while other failures surface as a card-level
   * error and keep the edited values.
   */
  protected saveIdentity(): void {
    if (this.identityForm.invalid || !this.identityChanged()) {
      return;
    }
    this.identitySaving.set(true);
    this.identitySaveError.set(null);
    this.profile.updateProfile(this.identityPayload()).subscribe({
      next: (updated) => {
        this.session.restore(updated);
        this.syncIdentity(updated);
        this.identitySaving.set(false);
        this.snackBar.open('Perfil actualizado', 'OK', { duration: 3000 });
      },
      error: (error: HttpErrorResponse) => {
        this.identitySaving.set(false);
        const message = toErrorResponse(error).message;
        if (error.status === 400 && message) {
          this.identityForm.controls.username.setErrors({ taken: message });
        } else {
          this.identitySaveError.set(message || 'No se pudo actualizar el perfil.');
        }
      },
    });
  }

  /**
   * Enforces `filterMode == 'STRICT'` ⟺ `coverageThreshold == 100` on user edits.
   *
   * Both directions listen to the other field's `valueChanges` and guard their
   * write against an already-consistent sibling, so the two converge to the
   * invariant without echoing each other's changes in a loop. Programmatic
   * `setValue` never marks the form dirty, so load and post-save resets keep a
   * pristine form even when the handler fires during them.
   */
  private wireCoverageInvariant(): void {
    this.prefsForm.controls.filterMode.valueChanges.subscribe((mode) => {
      this.coverageLocked.set(mode === 'STRICT');
      if (mode === 'STRICT' && this.prefsForm.controls.coverageThreshold.value !== 100) {
        this.prefsForm.controls.coverageThreshold.setValue(100);
      }
    });

    this.prefsForm.controls.coverageThreshold.valueChanges.subscribe((threshold) => {
      if (threshold === 100 && this.prefsForm.controls.filterMode.value !== 'STRICT') {
        this.prefsForm.controls.filterMode.setValue('STRICT');
      }
    });
  }

  /** Fetches the stored preferences (or the backend defaults) into the form. */
  protected loadPreferences(): void {
    this.prefsLoading.set(true);
    this.prefsLoadError.set(null);
    this.profile.getPreferences().subscribe({
      next: (prefs) => {
        this.prefsForm.reset(this.normalizePreferences(prefs));
        this.prefsLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.prefsLoading.set(false);
        this.prefsLoadError.set(toErrorResponse(error).message || 'Unable to load preferences.');
      },
    });
  }

  /**
   * Aligns a server response with the coverage invariant before resetting.
   *
   * The backend contract is permissive (threshold 0-100, no cross-field
   * validation), but the domain forbids the STRICT + threshold < 100 pair, so
   * it is normalized here — before `reset()` — to load a pristine form.
   */
  private normalizePreferences(prefs: UserPreferences): UserPreferences {
    if (prefs.filterMode === 'STRICT' && prefs.coverageThreshold !== 100) {
      return { ...prefs, coverageThreshold: 100 };
    }
    return prefs;
  }

  /** Sends the edited preferences; on success re-syncs the form with the server. */
  protected savePreferences(): void {
    if (this.prefsForm.invalid) {
      return;
    }
    this.savingPrefs.set(true);
    this.prefsSaveError.set(null);
    this.profile.updatePreferences(this.prefsForm.getRawValue()).subscribe({
      next: (saved) => {
        this.prefsForm.reset(saved);
        this.savingPrefs.set(false);
        this.snackBar.open('Preferences saved', 'OK', { duration: 3000 });
      },
      error: (error: HttpErrorResponse) => {
        this.savingPrefs.set(false);
        this.prefsSaveError.set(toErrorResponse(error).message || 'Unable to save preferences.');
      },
    });
  }

  /**
   * Fetches the allergen catalog and the current exclusions in parallel.
   *
   * Both are needed to build the checklist, and fetching them together with
   * forkJoin guarantees a coherent snapshot (e.g. a catalog refresh can never
   * pair with a stale exclusion list).
   */
  protected loadExclusions(): void {
    this.exclusionsLoading.set(true);
    this.exclusionsLoadError.set(null);
    forkJoin({
      catalog: this.profile.getAllergenCatalog(),
      current: this.profile.getAllergyExclusions(),
    }).subscribe({
      next: ({ catalog, current }) => {
        this.exclusions.clear();
        const selected = new Set(current.exclusions.map((item) => item.code));
        for (const allergen of catalog) {
          this.exclusions.push(this.fb.control(selected.has(allergen.code)));
        }
        // Structural edits (clear/push) leave the array dirty; a freshly loaded
        // checklist must start pristine so Save stays disabled until a change.
        this.exclusions.markAsPristine();
        this.allergens.set(catalog);
        this.exclusionsLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.exclusionsLoading.set(false);
        this.exclusionsLoadError.set(
          toErrorResponse(error).message || 'Unable to load exclusions.',
        );
      },
    });
  }

  /**
   * Sends the full current selection and re-syncs the checklist on success.
   *
   * The API replaces the whole exclusion list, so a single PUT with the
   * complete (uppercase) catalog selection is sent instead of per-checkbox
   * updates — and the server response becomes the new baseline for Save.
   */
  protected saveExclusions(): void {
    const codes = this.allergens().flatMap((allergen, index) =>
      this.exclusions.at(index).value ? [allergen.code] : [],
    );
    this.savingExclusions.set(true);
    this.exclusionsSaveError.set(null);
    this.profile.updateAllergyExclusions(codes).subscribe({
      next: (saved) => {
        const savedCodes = new Set(saved.exclusions.map((item) => item.code));
        this.exclusions.reset(this.allergens().map((allergen) => savedCodes.has(allergen.code)));
        this.savingExclusions.set(false);
        this.snackBar.open('Allergy exclusions saved', 'OK', { duration: 3000 });
      },
      error: (error: HttpErrorResponse) => {
        this.savingExclusions.set(false);
        this.exclusionsSaveError.set(
          toErrorResponse(error).message || 'Unable to save exclusions.',
        );
      },
    });
  }
}
