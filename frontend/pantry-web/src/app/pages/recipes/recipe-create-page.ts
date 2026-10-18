import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  MatAutocomplete,
  MatAutocompleteSelectedEvent,
  MatAutocompleteTrigger,
} from '@angular/material/autocomplete';
import { MatButton, MatIconButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { ErrorStateMatcher, MatOption, MatOptgroup } from '@angular/material/core';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, Subscription } from 'rxjs';
import { toErrorResponse } from '../../core/auth/auth.models';
import { IngredientSearchHit } from '../../core/catalog/catalog.models';
import { CatalogService } from '../../core/catalog/catalog.service';
import { UnitGroup, buildUnitGroups } from '../../core/catalog/unit-groups';
import { UnitService } from '../../core/catalog/unit.service';
import {
  CreateRecipeIngredientRequest,
  CreateRecipeRequest,
} from '../../core/recipes/recipes.models';
import { RecipesService } from '../../core/recipes/recipes.service';

/**
 * The ingredient the row is narrowed by, resolved against the local catalog.
 *
 * Same two-step read as the recipe grid's ingredient filter (see
 * `RecipesPage`): a catalog search hit carries only the provider `fdcId`, while
 * `POST /api/recipes` addresses its lines by the catalog's internal id, so the
 * picked hit must be resolved through `CatalogService.getIngredient` before the
 * row can be sent. Until that answer arrives the control holds the raw hit or
 * the text being typed, and {@link resolvedIngredient} keeps the row invalid.
 */
interface SelectedIngredient {
  readonly id: number;
  readonly name: string;
}

/** How long a row's ingredient box waits before searching the catalog. */
const SEARCH_DEBOUNCE_MS = 300;

/** The three controls of one ingredient line, as the form array holds them. */
type IngredientRowValue = {
  ingredient: FormControl<SelectedIngredient | IngredientSearchHit | string | null>;
  amount: FormControl<number | null>;
  unitCode: FormControl<string>;
};

type IngredientRow = FormGroup<IngredientRowValue>;

/**
 * Rejects an amount that is not a number greater than zero, the same rule the
 * pantry dialog applies: the recipe API validates a positive amount per line,
 * so the error belongs to the field and the request never leaves the browser.
 * An empty field is left to `required` so it says "enter an amount" rather
 * than "enter more than zero".
 */
const positiveAmount = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  return typeof value !== 'number' ? null : value > 0 ? null : { positive: true };
};

/**
 * Rejects an ingredient that has not been resolved to the local catalog.
 *
 * The row's control holds three shapes over its life: the text being typed, the
 * raw search hit Material writes back on a pick, and the resolved
 * {@link SelectedIngredient} the API needs. Only the last one carries the
 * internal `id`, so anything else is an incomplete line and this validator is
 * what keeps an unresolved food out of the request — the same guarantee the
 * disabled submit button gives, but enforced where the data lives.
 */
const resolvedIngredient = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as { id?: unknown } | string | null;
  const resolved = value !== null && typeof value === 'object' && typeof value.id === 'number';
  return resolved ? null : { unresolved: true };
};

/**
 * "New recipe": a form for the recipe's own fields plus a dynamic array of
 * ingredient lines, one per catalog food, ending in a single POST.
 *
 * The recipe starts private on purpose — publishing is a separate decision
 * taken from the detail page once the recipe exists, so this page has exactly
 * one outcome: a stored draft the user can review. That is also why a success
 * navigates to the detail rather than showing a message: the detail is where
 * the result can be read, corrected and published.
 *
 * Each ingredient line is a search box of its own, because a recipe names its
 * foods in its own words and no single box can hold six of them. Every row
 * searches, resolves and fails independently (per-row subscriptions, per-row
 * race guards and per-row error slots), so a provider hiccup on one line never
 * touches the others. Rows hold only catalog foods — free text is not a
 * quantity the pantry can one day measure.
 *
 * The form sends what the API contracts for and nothing more: optional text is
 * trimmed and blank becomes `null` (replacement semantics), positions are the
 * row order, and fields this page does not collect yet (`videoUrl`, `tags`,
 * `sourceUrl`) travel as explicit `null` rather than absent keys.
 */
@Component({
  selector: 'app-recipe-create-page',
  imports: [
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatError,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOption,
    MatOptgroup,
    MatProgressSpinner,
    MatSelect,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './recipe-create-page.html',
  styleUrl: './recipe-create-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipeCreatePage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly catalog = inject(CatalogService);
  private readonly units = inject(UnitService);
  private readonly recipes = inject(RecipesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** The recipe fields and its ingredient array, validated as one form. */
  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    category: ['', Validators.maxLength(100)],
    area: ['', Validators.maxLength(100)],
    instructions: [''],
    imageUrl: ['', Validators.maxLength(512)],
    servings: this.fb.control<number | null>(null, [Validators.min(1)]),
    ingredients: this.fb.array<IngredientRow>([]),
  });

  /** True while the create request is in flight; drives the button spinner. */
  protected readonly submitting = signal(false);
  /** Message of a refused create; the form keeps every edited value. */
  protected readonly formError = signal<string | null>(null);

  /**
   * Catalog hits per row, as each autocomplete shows them.
   *
   * A signal rather than plain state so the OnPush template re-renders when a
   * row's answer lands: the rows themselves are form controls, which are not
   * signals, and change detection must not depend on an unrelated event to
   * notice a late HTTP answer.
   */
  private readonly hitsByRow = signal<Map<IngredientRow, readonly IngredientSearchHit[]>>(
    new Map(),
  );

  /** Rows whose catalog search or detail read is in flight; drives the spinners. */
  private readonly pendingByRow = signal<ReadonlySet<IngredientRow>>(new Set());

  /** Failure message per row, shown in that row's field instead of blocking the form. */
  private readonly errorsByRow = signal<Map<IngredientRow, string>>(new Map());

  /** One valueChanges subscription per row, torn down when the row is removed. */
  private readonly rowSubscriptions = new Map<IngredientRow, Subscription>();

  /** Sequence number per row, so a late answer cannot overwrite a newer one. */
  private readonly rowSeq = new Map<IngredientRow, number>();

  /** One error-state matcher per row, so `mat-error` shows without a blur. */
  private readonly rowErrorStateMatchers = new Map<IngredientRow, ErrorStateMatcher>();

  /** The unit catalog grouped for the selectors; the whole catalog is offered. */
  protected readonly unitGroups = computed<UnitGroup[]>(() =>
    buildUnitGroups(this.units.units() ?? []),
  );

  constructor() {
    // A recipe needs at least one line to be worth saving, so the form opens
    // with one empty row rather than making the user ask for it first.
    this.ingredients.push(this.newRow());
  }

  ngOnInit(): void {
    // Labels for the unit selectors; a no-op when another page already did it.
    this.units.load();
  }

  /** The ingredient lines, typed for the template's and the specs' row bindings. */
  get ingredients(): FormArray<IngredientRow> {
    return this.form.controls.ingredients;
  }

  /** The catalog hits a row's autocomplete offers. */
  protected hitsFor(row: IngredientRow): readonly IngredientSearchHit[] {
    return this.hitsByRow().get(row) ?? [];
  }

  /** Whether a row's catalog search or detail read is still travelling. */
  protected searching(row: IngredientRow): boolean {
    return this.pendingByRow().has(row);
  }

  /** The failure message of a row's catalog work, if any. */
  protected rowError(row: IngredientRow): string | null {
    return this.errorsByRow().get(row) ?? null;
  }

  /**
   * The error-state matcher of a row's ingredient box, cached per row.
   *
   * A catalog failure is a state of the page, not of the control's validation:
   * the box may never have been blurred, so the default `touched && invalid`
   * rule would keep the message hidden. The row's own matcher adds the failure
   * signal on top of that rule rather than replacing it, so the field still
   * announces "choose a catalog food" once the user has visited it.
   */
  protected errorStateFor(row: IngredientRow): ErrorStateMatcher {
    let matcher = this.rowErrorStateMatchers.get(row);
    if (!matcher) {
      matcher = {
        isErrorState: (control) =>
          this.rowError(row) !== null || (!!control && control.invalid && control.touched),
      };
      this.rowErrorStateMatchers.set(row, matcher);
    }
    return matcher;
  }

  /**
   * Renders the row's ingredient box: the resolved (or picked) food's name,
   * the raw text otherwise — the same union contract as the recipe grid's
   * filter box.
   */
  protected displayIngredient(
    value: SelectedIngredient | IngredientSearchHit | string | null,
  ): string {
    if (value === null) {
      return '';
    }
    return typeof value === 'string' ? value : value.name;
  }

  /** Appends an empty line; the new row searches for itself like any other. */
  protected addRow(): void {
    this.ingredients.push(this.newRow());
  }

  /**
   * Drops a line and everything hanging from it: its subscription, its hits
   * and its pending work, whose late answers would otherwise write into maps
   * whose row no longer exists.
   */
  protected removeRow(index: number): void {
    if (this.ingredients.length <= 1) {
      return;
    }
    const row = this.ingredients.at(index);
    this.rowSubscriptions.get(row)?.unsubscribe();
    this.rowSubscriptions.delete(row);
    this.rowSeq.delete(row);
    this.dropRowState(row);
    this.ingredients.removeAt(index);
  }

  /**
   * Creates the recipe and opens its detail.
   *
   * The payload is built here and not from `form.value`: optional text must
   * arrive trimmed or as `null`, and `position` is the row order made explicit
   * so the stored list renders exactly as authored. A valid form has already
   * run every row through {@link resolvedIngredient} and the required
   * validators, which is what the two casts below name.
   */
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.submitting()) {
      return;
    }

    const { name, category, area, instructions, imageUrl, servings } = this.form.getRawValue();
    const rows = this.ingredients.controls;
    const request: CreateRecipeRequest = {
      name: name.trim(),
      category: blankToNull(category),
      area: blankToNull(area),
      instructions: blankToNull(instructions),
      imageUrl: blankToNull(imageUrl),
      videoUrl: null,
      tags: null,
      servings,
      sourceUrl: null,
      ingredients: rows.map((row, index): CreateRecipeIngredientRequest => {
        const ingredient = row.controls.ingredient.value as SelectedIngredient;
        return {
          ingredientId: ingredient.id,
          amount: row.controls.amount.value as number,
          unitCode: row.controls.unitCode.value,
          position: index + 1,
        };
      }),
    };

    this.submitting.set(true);
    this.formError.set(null);
    this.recipes.create(request).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.router.navigate(['/recipes', created.id]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(toErrorResponse(error).message || 'Could not create the recipe.');
      },
    });
  }

  /**
   * Builds one line with its own search pipeline.
   *
   * The subscription lives on the row's ingredient box and ends with the
   * component (or with {@link removeRow}): each keystroke pause searches the
   * catalog for this row only, and a non-string value is the pick Material
   * wrote back — {@link onIngredientSelected} owns that path, so the handler
   * stays out of its way.
   */
  private newRow(): IngredientRow {
    const row = this.fb.group<IngredientRowValue>({
      ingredient: this.fb.control<SelectedIngredient | IngredientSearchHit | string | null>(
        null,
        resolvedIngredient,
      ),
      amount: this.fb.control<number | null>(null, [Validators.required, positiveAmount]),
      unitCode: this.fb.control<string>('', Validators.required),
    });
    this.rowSeq.set(row, 0);
    const subscription = row.controls.ingredient.valueChanges
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((value) => this.onRowValueChange(row, value));
    this.rowSubscriptions.set(row, subscription);
    return row;
  }

  /**
   * Reacts to a row's ingredient box being edited.
   *
   * An object is the pick Material just wrote back and the selection handler
   * resolves it; a string is the user typing, which invalidates any previous
   * pick (the validator does that) and asks the catalog for the new text. An
   * empty box drops the row's hits and error and spends no request.
   */
  private onRowValueChange(
    row: IngredientRow,
    value: SelectedIngredient | IngredientSearchHit | string | null,
  ): void {
    if (value !== null && typeof value === 'object') {
      return;
    }
    const query = typeof value === 'string' ? value.trim() : '';
    if (query === '') {
      this.hitsByRow.update((map) => {
        const next = new Map(map);
        next.delete(row);
        return next;
      });
      this.clearRowError(row);
      return;
    }
    this.searchRow(row, query);
  }

  /** Asks the catalog for foods matching the text, guarded by the row's sequence. */
  private searchRow(row: IngredientRow, query: string): void {
    const seq = (this.rowSeq.get(row) ?? 0) + 1;
    this.rowSeq.set(row, seq);
    this.setRowPending(row, true);
    this.clearRowError(row);

    this.catalog.searchIngredients(query).subscribe({
      next: (hits) => {
        if (seq !== this.rowSeq.get(row)) {
          return;
        }
        this.setRowPending(row, false);
        this.hitsByRow.update((map) => {
          const next = new Map(map);
          next.set(row, hits);
          return next;
        });
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.rowSeq.get(row)) {
          return;
        }
        this.setRowPending(row, false);
        this.hitsByRow.update((map) => {
          const next = new Map(map);
          next.set(row, []);
          return next;
        });
        this.setRowError(row, toErrorResponse(error).message || 'Could not search the catalog.');
      },
    });
  }

  /**
   * Resolves the picked hit into the internal ingredient id the API needs.
   *
   * The detail read is not optional: it is what materializes the food in the
   * local catalog (a create addressing an unmaterialized food is refused) and
   * the only answer carrying the internal id. On failure the pick is dropped
   * from the box — it never became a usable line — and the row says why, while
   * the rest of the form stays untouched.
   */
  protected onIngredientSelected(row: IngredientRow, event: MatAutocompleteSelectedEvent): void {
    const hit = event.option.value as IngredientSearchHit;
    const seq = (this.rowSeq.get(row) ?? 0) + 1;
    this.rowSeq.set(row, seq);
    this.setRowPending(row, true);
    this.clearRowError(row);

    this.catalog.getIngredient(hit.fdcId).subscribe({
      next: (detail) => {
        if (seq !== this.rowSeq.get(row)) {
          return;
        }
        this.setRowPending(row, false);
        this.hitsByRow.update((map) => {
          const next = new Map(map);
          next.set(row, []);
          return next;
        });
        row.controls.ingredient.setValue({ id: detail.id, name: detail.name });
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.rowSeq.get(row)) {
          return;
        }
        this.setRowPending(row, false);
        // Without the resolved id the pick is unusable, so the box is cleared
        // without an event: the text that triggered the search is gone with it.
        row.controls.ingredient.setValue(null, { emitEvent: false });
        this.setRowError(row, toErrorResponse(error).message || 'Could not read the ingredient.');
      },
    });
  }

  /** Mirrors a row's pending flag into the signal the template reads. */
  private setRowPending(row: IngredientRow, pending: boolean): void {
    this.pendingByRow.update((set) => {
      const next = new Set(set);
      if (pending) {
        next.add(row);
      } else {
        next.delete(row);
      }
      return next;
    });
  }

  /** Mirrors a row's failure message into the signal the template reads. */
  private setRowError(row: IngredientRow, message: string): void {
    this.errorsByRow.update((map) => {
      const next = new Map(map);
      next.set(row, message);
      return next;
    });
  }

  private clearRowError(row: IngredientRow): void {
    this.errorsByRow.update((map) => {
      if (!map.has(row)) {
        return map;
      }
      const next = new Map(map);
      next.delete(row);
      return next;
    });
  }

  /** Forgets every per-row entry of a removed line. */
  private dropRowState(row: IngredientRow): void {
    this.rowErrorStateMatchers.delete(row);
    this.hitsByRow.update((map) => {
      if (!map.has(row)) {
        return map;
      }
      const next = new Map(map);
      next.delete(row);
      return next;
    });
    this.setRowPending(row, false);
    this.clearRowError(row);
  }
}

/** Trims optional text and turns a blank into the `null` the API stores. */
function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
