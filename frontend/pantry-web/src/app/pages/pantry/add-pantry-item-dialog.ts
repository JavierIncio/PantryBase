import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatOption, MatOptgroup } from '@angular/material/core';
import {
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { toErrorResponse } from '../../core/auth/auth.models';
import { IngredientDetail, IngredientSearchHit } from '../../core/catalog/catalog.models';
import { CatalogService } from '../../core/catalog/catalog.service';
import { UnitGroup, buildUnitGroups } from '../../core/catalog/unit-groups';
import { UnitService } from '../../core/catalog/unit.service';
import { CreatePantryItemRequest, PantryItem } from '../../core/pantry/pantry.models';
import { PantryService } from '../../core/pantry/pantry.service';

/** Which of the two steps of the flow is on screen. */
type AddStep = 'search' | 'quantity';

/**
 * What the dialog reports to the page that opened it.
 *
 * `created` carries the stored line, so the page can say what was actually stored
 * (the backend normalizes the amount to the unit the line keeps) and reload the
 * list instead of guessing where the line belongs in it. `existing` is the 409
 * the dialog cannot resolve by itself: it carries the `fdcId` the conflict was
 * about plus the backend's own wording, and only the page knows the lines.
 */
export type AddPantryItemResult =
  | { readonly kind: 'created'; readonly item: PantryItem }
  | { readonly kind: 'existing'; readonly fdcId: number; readonly message: string };

/**
 * Wording for a failed call to the external food provider.
 *
 * The API answers one status (502) for two facts the client cannot tell apart:
 * the provider is down, and its hourly quota is spent (the provider's own 429 is
 * mapped to 502 upstream). Both are named here instead of being flattened into
 * "something went wrong", because they call for different patience. Nothing
 * retries on its own after this: the quota is 30 requests an hour without a key,
 * so a silent retry would spend a quota that is already gone. The user decides.
 */
const PROVIDER_FAILURE =
  'The food provider could not answer, or its hourly quota is spent (30 requests an hour without an API key).';

/**
 * Rejects a blank query: a search for spaces is not a search, and the API answers
 * 400 for one.
 *
 * A validator rather than a check in the handler, so the error lives on the
 * control and disappears as soon as there is something to search for, instead of
 * costing a request to find out.
 */
const notBlank = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim() !== '' ? null : { blank: true };

/**
 * Rejects an amount that is not a number greater than zero.
 *
 * A pantry line is stock on hand, so there is no such thing as zero of something
 * or a negative amount of it, and the endpoint answers 400 for both. A validator
 * rather than a check on submit, for the same reason the adjustment panel uses
 * one: the error belongs to the field and the request never leaves the browser.
 * A control with no number at all is left to `Validators.required`, so the field
 * says "enter an amount" rather than "enter more than zero".
 */
const positiveAmount = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  // An empty field is `null`, not a number: answering that here too would stack a
  // second error on the field and ask the user to fix what is not wrong yet.
  return typeof value !== 'number' ? null : value > 0 ? null : { positive: true };
};

/**
 * The first measure the provider weighed for this food, or an empty code.
 *
 * Offered as the default because it is the unit the food was measured in, and
 * therefore the one the user is most likely to mean. When the provider weighed no
 * measure nothing is chosen: every other unit relies on a density this food may
 * not have, and a silent default would store a quantity nobody asked for.
 */
function firstMeasuredUnit(detail: IngredientDetail): string {
  return detail.measures[0]?.unitCode ?? '';
}

/**
 * Quick add: search a food in the catalog, pick it, say how much of it there is,
 * and add it to the pantry.
 *
 * A dialog and not a page of its own, because the whole value of the feature is
 * adding a line while still seeing the pantry: a route would replace the list, and
 * coming back would be the only way to learn what happened. The flow is two small
 * steps (search, then a quantity) rather than a place the user would want to
 * linger, so it fits a dialog and keeps the page it was opened from alive behind.
 *
 * Picking a food is not enough to store it, and that is the shape of the flow
 * rather than a gap in it: the detail read is what materializes the ingredient in
 * the local catalog (without it the add is refused with 404) and it is the only
 * call that says which household measures the provider weighed for this food.
 * That is why the second step cannot exist before the read answers, and why the
 * read is not something this dialog could skip.
 *
 * The unit selector offers the whole catalog and puts the measured measures of
 * the chosen food first; whether a pair of units converts at all is the server's
 * decision, so nothing is hidden here (see {@link buildUnitGroups}).
 *
 * The dialog never inserts anything into the list it was opened from: it reports
 * what it created and the page reloads, because the order and the grouping of the
 * list are the server's.
 */
@Component({
  selector: 'app-add-pantry-item-dialog',
  imports: [
    MatButton,
    MatDialogActions,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
    MatOptgroup,
    MatProgressSpinner,
    MatSelect,
    ReactiveFormsModule,
  ],
  templateUrl: './add-pantry-item-dialog.html',
  styleUrl: './add-pantry-item-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddPantryItemDialog implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly catalog = inject(CatalogService);
  private readonly units = inject(UnitService);
  private readonly pantry = inject(PantryService);
  private readonly dialogRef = inject(MatDialogRef<AddPantryItemDialog, AddPantryItemResult>);

  /**
   * Sequence number of the newest request of each kind, so a late answer can tell
   * it has been superseded.
   *
   * Nothing in the current template reaches this: the search button is disabled
   * while a search runs, and the results are disabled while a detail read is in
   * flight, so two of either cannot be started from the screen. The counters are
   * the methods defending themselves instead of trusting that arrangement --
   * {@link add} already refuses a second click in code rather than on the button,
   * and these two reads were the odd ones out. Two answers out of order is not a
   * state a dialog should be able to end up in: the slow one is the obsolete one,
   * and letting it land would put foods named "milk" under a "flour" query, or
   * offer the measures of a food the user did not pick last.
   *
   * A counter rather than cancellation because aborting the request would change
   * nothing for the provider -- it is already being served -- and would cost the
   * flow a subscription to rebuild for a saving that is not ours to make.
   *
   * {@link changeFood} bumps the detail counter as well, so a read still on its
   * way cannot push the dialog into the step the user just left.
   */
  private searchSeq = 0;
  private detailSeq = 0;

  /** The only input of the first step. */
  readonly searchForm = this.fb.group({
    query: ['', notBlank],
  });

  /**
   * The inputs of the second step.
   *
   * `amount` is a nullable number control because the commonest mistake is an
   * empty field rather than a wrong number, and `unitCode` is required because a
   * quantity without a unit is not a quantity: choosing one for the user would
   * mean guessing what gets stored.
   */
  readonly quantityForm = this.fb.group({
    amount: this.fb.control<number | null>(null, [Validators.required, positiveAmount]),
    unitCode: ['', Validators.required],
  });

  protected readonly step = signal<AddStep>('search');

  protected readonly hits = signal<IngredientSearchHit[]>([]);
  /** True once a search has answered, so "no results" can be told from "no search". */
  protected readonly searched = signal(false);
  protected readonly searching = signal(false);
  protected readonly searchError = signal<string | null>(null);

  /** True while the detail of a chosen food is being read. */
  protected readonly detailLoading = signal(false);
  /** Message of a failed detail read; the results stay on screen to pick again. */
  protected readonly detailError = signal<string | null>(null);
  /** The chosen food, with the measures the provider weighed for it. */
  protected readonly ingredient = signal<IngredientDetail | null>(null);

  protected readonly saving = signal(false);
  protected readonly addError = signal<string | null>(null);
  /**
   * The add was refused because the ingredient is not in the local catalog.
   *
   * Kept apart from the message because it is the one failure with a concrete
   * repair, the very call the backend message names, so that call is offered
   * instead of a blind retry of the add.
   */
  protected readonly ingredientMissing = signal(false);
  /**
   * The add was refused because the ingredient is already in the pantry.
   *
   * Not a dead end: the backend asks for an adjustment, so the dialog hands the
   * user over to the existing line instead of repeating the add.
   */
  protected readonly alreadyInPantry = signal(false);

  /** Options of the unit selector: grouped by category, measured ones first. */
  protected readonly unitGroups = computed<UnitGroup[]>(() =>
    buildUnitGroups(this.units.units() ?? [], this.ingredient()?.measures ?? []),
  );

  /** Message of a failed unit catalog load, which costs labels and nothing else. */
  protected readonly unitCatalogError = computed(() => this.units.loadError());

  /**
   * The backend's own wording for the amount, when it was the one that refused it.
   *
   * A method and not a computed on purpose: form errors are not signals, so a
   * computed would read them once and keep the stale answer forever. The template
   * calls it on every change detection of the step, which is exactly when the
   * errors can have changed.
   */
  protected rejectedAmount(): string | undefined {
    return this.quantityForm.controls.amount.errors?.['rejected'] as string | undefined;
  }

  ngOnInit(): void {
    // A no-op when the page that opened this dialog already loaded the catalog;
    // the dialog cannot offer an empty selector, so it asks for it anyway.
    this.units.load();
  }

  /**
   * Asks the catalog for foods matching the typed query.
   *
   * A submitted search and not one per keystroke: the backend caches search
   * answers, but each call is still a round trip, and results the user has not
   * finished asking for are results they will never read.
   */
  protected search(): void {
    if (this.searchForm.invalid) {
      this.searchForm.markAllAsTouched();
      return;
    }

    const query = this.searchForm.getRawValue().query.trim();
    const seq = ++this.searchSeq;
    this.searching.set(true);
    this.searchError.set(null);

    this.catalog.searchIngredients(query).subscribe({
      next: (hits) => {
        if (seq !== this.searchSeq) {
          return;
        }
        this.hits.set(hits);
        this.searched.set(true);
        this.searching.set(false);
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.searchSeq) {
          return;
        }
        this.searching.set(false);
        // The results of the previous query are dropped with it: showing them
        // under this query's failure would let the user pick a food they did not
        // search for.
        this.hits.set([]);
        this.searched.set(false);
        this.searchError.set(this.describe(error, 'Unable to search the food catalog.'));
      },
    });
  }

  /**
   * Reads the detail of a result and moves on to the quantity.
   *
   * The read is mandatory before an add, not an optional extra: it materializes
   * the ingredient in the local catalog and tells the unit selector which
   * measures exist for this particular food. A failure leaves the results on
   * screen, because the user can always pick a different food.
   */
  protected choose(hit: IngredientSearchHit): void {
    const seq = ++this.detailSeq;
    this.detailError.set(null);
    this.detailLoading.set(true);

    this.catalog.getIngredient(hit.fdcId).subscribe({
      next: (detail) => {
        if (seq !== this.detailSeq) {
          return;
        }
        this.ingredient.set(detail);
        this.detailLoading.set(false);
        this.step.set('quantity');
        this.quantityForm.reset({ amount: null, unitCode: firstMeasuredUnit(detail) });
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.detailSeq) {
          return;
        }
        this.detailLoading.set(false);
        this.detailError.set(this.describe(error, 'Unable to read this ingredient.'));
      },
    });
  }

  /**
   * Reads the ingredient again after the add was refused for a missing local copy.
   *
   * The typed amount and unit survive: what failed was that the ingredient was
   * not materialized, nothing about the quantity. And since this call can cost a
   * provider request, it is only ever made because the user asked for it.
   */
  protected reloadIngredient(): void {
    const detail = this.ingredient();
    if (!detail) {
      return;
    }

    const seq = ++this.detailSeq;
    this.ingredientMissing.set(false);
    this.addError.set(null);
    this.detailLoading.set(true);

    this.catalog.getIngredient(detail.fdcId).subscribe({
      next: (refreshed) => {
        if (seq !== this.detailSeq) {
          return;
        }
        this.ingredient.set(refreshed);
        this.detailLoading.set(false);
        // Only fills in a unit when the user has not chosen one: this repairs the
        // ingredient, it is not a second chance to change the quantity.
        if (!this.quantityForm.controls.unitCode.value) {
          this.quantityForm.controls.unitCode.setValue(firstMeasuredUnit(refreshed));
        }
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.detailSeq) {
          return;
        }
        this.detailLoading.set(false);
        this.addError.set(this.describe(error, 'Unable to read this ingredient.'));
      },
    });
  }

  /** Back to the results, keeping the query and the results themselves. */
  protected changeFood(): void {
    // Supersedes a detail read that is still on its way: the user has said no to
    // this food, and answering with its detail afterwards would drag the dialog
    // into the step they just left.
    ++this.detailSeq;
    this.ingredient.set(null);
    this.addError.set(null);
    this.alreadyInPantry.set(false);
    this.step.set('search');
  }

  /** Closes handing over to the line that already holds this food. */
  protected gotoExisting(): void {
    const detail = this.ingredient();
    if (!detail) {
      return;
    }
    this.dialogRef.close({
      kind: 'existing',
      fdcId: detail.fdcId,
      message: this.addError() ?? '',
    });
  }

  /** Retries the unit catalog, which is local data and costs no provider quota. */
  protected retryUnitCatalog(): void {
    this.units.load();
  }

  protected cancel(): void {
    this.dialogRef.close();
  }

  /**
   * Creates the line.
   *
   * The request carries the `fdcId` the catalog speaks in and never an internal
   * ingredient id, and the amount travels as it was typed, in the unit it was
   * typed in: which unit the line ends up keeping is the server's decision when it
   * answers, and what comes back is what gets reported to the user.
   *
   * A second click is refused here and not only by disabling the button, so a
   * double click cannot create two lines of the same food even if the button were
   * pressed twice before the first request finished.
   */
  protected add(): void {
    const detail = this.ingredient();
    const { amount, unitCode } = this.quantityForm.getRawValue();
    if (!detail || this.quantityForm.invalid || amount === null) {
      this.quantityForm.markAllAsTouched();
      return;
    }
    if (this.saving()) {
      return;
    }

    const request: CreatePantryItemRequest = { fdcId: detail.fdcId, amount, unitCode };

    this.saving.set(true);
    this.addError.set(null);
    this.alreadyInPantry.set(false);
    this.ingredientMissing.set(false);

    this.pantry.createItem(request).subscribe({
      next: (item) => {
        this.saving.set(false);
        this.dialogRef.close({ kind: 'created', item });
      },
      error: (error: HttpErrorResponse) => this.failAdd(error),
    });
  }

  /**
   * Decides what a refused add means for this dialog.
   *
   * The refusals that are not a validation error each get their own handling
   * instead of one message, because each says something different and has a
   * different way out: a 409 is a line that already exists and can be adjusted
   * from the pantry, a 404 means the food was never materialized and the detail
   * read repairs it, and anything else (a 502, for the provider being called on
   * the way) is shown as it came and retried by nobody. A 400 is a validation
   * error and belongs to the amount field.
   */
  private failAdd(error: HttpErrorResponse): void {
    this.saving.set(false);
    const message = toErrorResponse(error).message;

    switch (error.status) {
      case 409:
        this.alreadyInPantry.set(true);
        this.addError.set(message);
        return;
      case 404:
        this.ingredientMissing.set(true);
        this.addError.set(message);
        return;
      case 400:
        // The amount is the only field of this form the API validates as a
        // number, and a blank unit cannot be sent from here at all.
        this.quantityForm.controls.amount.setErrors({ rejected: message });
        return;
      default:
        this.addError.set(this.describe(error, 'Unable to add this ingredient.'));
    }
  }

  /**
   * The text of a failure, with the provider wording prepended when the provider
   * is the one that failed.
   *
   * The backend's own message is always kept: it is the only text that knows what
   * the provider actually said, and a client that replaced it with its own would
   * be guessing.
   */
  private describe(error: HttpErrorResponse, fallback: string): string {
    const message = toErrorResponse(error).message || fallback;
    return error.status === 502 ? `${PROVIDER_FAILURE} ${message}` : message;
  }
}
