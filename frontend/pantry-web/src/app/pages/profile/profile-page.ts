import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormArray,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatOption } from '@angular/material/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { toErrorResponse } from '../../core/auth/auth.models';
import { SessionState } from '../../core/auth/session.state';
import { Allergen, Diet, FilterMode, UserPreferences } from '../../core/user/profile.models';
import { ProfileService } from '../../core/user/profile.service';

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
 * Profile page: email header plus the Preferences and Allergy exclusions cards.
 *
 * Both sections load their own data on init so a failing backend call never
 * blocks the other, and each card surfaces an inline error with a manual retry.
 * Saving is explicit (no auto-save): the Save buttons stay disabled until the
 * user edits something, and a successful PUT re-syncs the form with the server
 * values — which also clears the dirty flag without extra bookkeeping.
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
    MatFormField,
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
  private readonly session = inject(SessionState);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly filterModeOptions = FILTER_MODE_OPTIONS;
  protected readonly dietOptions = DIET_OPTIONS;

  /** Identity shown in the header: email when present, else the username. */
  protected readonly email = computed(
    () => this.session.user()?.email ?? this.session.user()?.username ?? 'Profile',
  );

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
    this.loadPreferences();
    this.loadExclusions();
  }

  /** Fetches the stored preferences (or the backend defaults) into the form. */
  protected loadPreferences(): void {
    this.prefsLoading.set(true);
    this.prefsLoadError.set(null);
    this.profile.getPreferences().subscribe({
      next: (prefs) => {
        this.prefsForm.reset(prefs);
        this.prefsLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.prefsLoading.set(false);
        this.prefsLoadError.set(toErrorResponse(error).message || 'Unable to load preferences.');
      },
    });
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
