import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  MatAutocomplete,
  MatAutocompleteSelectedEvent,
  MatAutocompleteTrigger,
} from '@angular/material/autocomplete';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { ErrorStateMatcher, MatOption } from '@angular/material/core';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { toErrorResponse } from '../../core/auth/auth.models';
import { IngredientSearchHit } from '../../core/catalog/catalog.models';
import { CatalogService } from '../../core/catalog/catalog.service';
import { RecipeSearchQuery, RecipeSource, RecipeSummary } from '../../core/recipes/recipes.models';
import {
  coverageClass,
  coveragePercent,
  orderByCoverage,
  passesThreshold,
} from '../../core/recipes/recipe-matching';
import { RecipesService } from '../../core/recipes/recipes.service';
import { ProfileService } from '../../core/user/profile.service';

/** How the two recipe origins are named in the UI. */
const SOURCE_LABELS: Record<RecipeSource, string> = {
  USER: 'Own',
  THE_MEAL_DB: 'TheMealDB',
};

const sourceLabel = (source: RecipeSource): string => SOURCE_LABELS[source] ?? source;

/**
 * The ingredient the search is narrowed by, resolved against the local catalog.
 *
 * A catalog search hit carries only the provider's `fdcId`, but `GET /api/recipes`
 * filters by the catalog's internal ingredient id. The two are different
 * namespaces (see `Ingredient` in the backend: a surrogate id next to the
 * provider id), so a hit cannot travel as `ingredientId` directly. Reading the
 * detail of the chosen `fdcId` is what exposes the internal id — and it is also
 * what materializes the food locally, without which the recipe endpoint answers
 * 404 for an ingredient it has never seen. That is why a selection is a two-step
 * read and why this stores the resolved pair, not the raw hit.
 */
interface SelectedIngredient {
  readonly id: number;
  readonly name: string;
}

/**
 * How long the name box waits before searching, so one search is made for each
 * pause in typing instead of one per keystroke.
 */
const SEARCH_DEBOUNCE_MS = 300;

/**
 * "Recipes": a search box, two filter toggles and a client-side coverage
 * slider over a grid of recipe cards.
 *
 * The search itself is the only thing that talks to the server. The name box
 * searches as the user types (one request per pause — the slow answer problem
 * exists here as much as in the catalog search, and a per-keystroke request
 * would answer questions the user already stopped asking); the `strict` and
 * `allergenSafe` toggles fire an immediate search, because the first is a
 * server-side filter (`matchScore == 1`, per the H4 engine) and the second
 * reads the user's allergy exclusions the client does not hold.
 *
 * The threshold slider is deliberately local: it recomputes over the rows the
 * last search already brought, sorting them by coverage (the lax order of H4)
 * and dropping those under the bar. It never re-queries — the scaffolding
 * instruction for H4 is that the slider recalculates without a call, and a
 * request per drag tick would answer no question the rows on screen cannot.
 *
 * Strict mode and the slider are two filters the same page must not let
 * contradict each other. When strict turns on, strict is what the user asked
 * for and it means "fully covered only", so the slider jumps to 100% and
 * locks there (the same invariant the profile preferences enforce); turning it
 * off unlocks the slider at the value it was left, exactly like flipping the
 * profile's filter mode back to LAX.
 *
 * The grid is a set of cards, one per recipe, each leading to its detail. A
 * recipe with no image gets an icon in its place, because a broken image would
 * say more about the network than about the recipe. An excluded allergen does
 * not decorate the card — the filter is either on and the recipe is gone, or
 * off and every recipe may carry any allergen.
 *
 * A failed search is a page-level error with a retry, never an empty grid: an
 * empty answer is a real state (no recipes match), while a 500 says nothing
 * about what the pantry can cook. The empty states are two: no recipes at all
 * for the query, or recipes that exist but none reaches the slider bar — the
 * second tells the user what to do (lower the threshold), the first what to
 * change (the search itself).
 *
 * The ingredient selector is a second, independent filter: its autocomplete
 * searches the food catalog and, on a pick, narrows the recipe search by that
 * ingredient. It does not touch the name box and the name box does not touch it,
 * because a user may want either alone or both together. It also stays out of
 * Suggest: recommend is driven by the pantry, and filtering it by an ingredient
 * the pantry may not hold would answer a question the user did not ask. A 404
 * from the recipe endpoint means the chosen ingredient left the catalog, which
 * is its own error state — clearing the filter is the repair, not retrying it.
 */
@Component({
  selector: 'app-recipes-page',
  imports: [
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatChip,
    MatChipSet,
    MatError,
    MatFormField,
    MatHint,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOption,
    MatProgressSpinner,
    MatSlider,
    MatSliderThumb,
    MatSlideToggle,
    MatSuffix,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './recipes-page.html',
  styleUrl: './recipes-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipesPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly recipesService = inject(RecipesService);
  private readonly profileService = inject(ProfileService);
  private readonly catalog = inject(CatalogService);

  /**
   * The search box.
   *
   * A form control and not a signal so the debounce rides Angular's reactive
   * stream: `valueChanges` already exists, filters and subscriptions are the
   * framework's, and nothing in the template has to remember a timer.
   */
  readonly nameControl = this.fb.control('');

  /**
   * The ingredient autocomplete box.
   *
   * Its value is a union on purpose: it holds the string the user is typing (or
   * `''`/`null` once cleared) and the picked {@link IngredientSearchHit} once
   * Material writes the chosen option back into the control. The union is what
   * lets {@link onIngredientValueChange} tell "the user is editing this box"
   * from "the user just picked an option", which call for opposite responses.
   *
   * The hit is kept in the control rather than only in a signal so Material's
   * autocomplete can render the pick with `displayWith` and clear it natively.
   */
  readonly ingredientControl = this.fb.control<IngredientSearchHit | string | null>(null);

  /**
   * The picked ingredient, resolved to the local catalog's internal id.
   *
   * Read by {@link load} to set the `ingredientId` parameter; `null` means no
   * ingredient filter. Deliberately separate from the control: the control may
   * hold the raw string a user is still typing, while this only ever holds a
   * fully resolved selection the search can trust.
   */
  protected readonly selectedIngredient = signal<SelectedIngredient | null>(null);

  /** Catalog hits for the text in the ingredient box, as the autocomplete shows them. */
  protected readonly ingredientOptions = signal<IngredientSearchHit[]>([]);
  /** True while the catalog search is in flight; drives the field's spinner. */
  protected readonly ingredientSearching = signal(false);
  /** True while the chosen hit's detail is being read to resolve its internal id. */
  protected readonly ingredientResolving = signal(false);
  /** Message of a failed catalog search or a failed detail read. */
  protected readonly ingredientError = signal<string | null>(null);

  /**
   * Shows the field's error state while a catalog read failed.
   *
   * `mat-error` only renders inside a form field whose control is errored, but
   * this box has no validators of its own — the failure is a state of the
   * page, not of the control's grammar. A custom matcher is how Material steps
   * out of the validators-only rule and lets the page's own signal decide.
   */
  readonly ingredientErrorState: ErrorStateMatcher = {
    isErrorState: () => this.ingredientError() !== null,
  };

  /**
   * True when the last search failed because the chosen ingredient left the
   * catalog (a 404). It is a distinct error state because its repair is not a
   * retry — the same request would fail again — but clearing the filter.
   */
  protected readonly ingredientStale = signal(false);

  /**
   * The threshold slider state, 0-100.
   *
   * Kept as a form control for the same reason the search box is one — a
   * slider is a native input and Material's control binding is the proven path
   * (the profile page runs the same slider on a form control). The value is
   * mirrored into a signal because the filtering decision reads it reactively;
   * it is 70 as a presentation default: coverage under 70% is a recipe the
   * pantry is not really cooking.
   */
  readonly filters = this.fb.group({
    threshold: this.fb.control(70, [Validators.min(0), Validators.max(100)]),
  });

  /** The client-side threshold, as a signal the grid filter reads. */
  private readonly threshold = signal(70);

  /** Strict mode (server-side `strict=true`): only `matchScore == 1`. */
  protected readonly strict = signal(false);
  /** Excludes recipes carrying an allergen the user excluded. */
  protected readonly allergenSafe = signal(false);

  /** The rows of the last answered search, in the server's order. */
  private readonly recipes = signal<RecipeSummary[]>([]);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  /** Indicates whether the current results come from recommend or search. */
  protected readonly mode = signal<'search' | 'suggest'>('search');

  /** Suggest-specific state. */
  protected readonly suggestLoading = signal(false);
  protected readonly suggestError = signal<string | null>(null);
  protected readonly suggestEmpty = signal(false);

  /**
   * The cards on screen: the searched rows, filtered by the slider and ordered
   * by coverage, best covered first. Derived, so moving the slider never
   * touches the server and re-renders nothing but the grid.
   */
  protected readonly visibleRecipes = computed(() =>
    orderByCoverage(
      this.recipes().filter((recipe) => passesThreshold(recipe.matchScore, this.threshold())),
    ),
  );

  /**
   * True while strict mode owns the threshold: the slider is locked at 100,
   * because strict already means "fully covered only" and a movable bar under
   * it could only contradict the very mode it is part of.
   */
  protected readonly thresholdLocked = computed(() => this.strict());

  /** True when the last search answered with rows at all, before the slider. */
  protected readonly hasRecipes = computed(() => this.recipes().length > 0);

  /**
   * The two empty cases share one card but not one message: no rows at all is
   * about the search, rows that all fail the slider are about the threshold.
   */
  protected readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.visibleRecipes().length === 0,
  );

  /**
   * The threshold the empty card may quote; set to 100 by strict mode, which
   * makes the message self-explanatory when the slider is the reason.
   */
  protected readonly visibleThreshold = computed(() =>
    this.thresholdLocked() ? 100 : this.threshold(),
  );

  /**
   * Sequence number of the newest search, so a late answer cannot overwrite
   * the rows of a search the user has already moved on from.
   */
  private searchSeq = 0;

  /** Supersedes a catalog search and a detail read the same way {@link searchSeq} does. */
  private catalogSeq = 0;
  private ingredientSeq = 0;

  ngOnInit(): void {
    this.nameControl.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged())
      .subscribe(() => this.load());
    // The ingredient box searches the catalog on its own debounce, for the same
    // reason the name box does: one request per pause, not one per keystroke.
    this.ingredientControl.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged())
      .subscribe((value) => this.onIngredientValueChange(value));
    this.filters.controls.threshold.valueChanges.subscribe((value) => this.threshold.set(value));
    this.loadPreferences();
    this.load();
  }

  private loadPreferences(): void {
    this.profileService.getPreferences().subscribe({
      next: (preferences) => {
        const threshold = preferences.coverageThreshold;
        this.strict.set(preferences.filterMode === 'STRICT');
        if (threshold >= 0 && threshold <= 100) {
          this.threshold.set(threshold);
          this.filters.controls.threshold.setValue(threshold, { emitEvent: false });
        }
        if (preferences.filterMode === 'STRICT') {
          this.filters.controls.threshold.setValue(100, { emitEvent: false });
          this.threshold.set(100);
        }
      },
      error: () => {
        // If the profile fails to load, keep defaults as-is.
      },
    });
  }

  /**
   * Searches with the filters as they are now.
   *
   * The response replaces the previous rows outright: the server is the only
   * authority on what matches, and rows of an older query would mix answers to
   * two different questions on one screen. A search superseded by a newer one
   * (a toggle clicked while a search was still travelling) lands nowhere.
   *
   * The name is trimmed before it travels and dropped when empty: searching
   * for spaces is not a search, and an absent value means "no name filter" on
   * the API, which is what a cleared box has to say.
   */
  protected load(): void {
    const seq = ++this.searchSeq;
    const name = this.nameControl.value.trim();
    const ingredientId = this.selectedIngredient()?.id;
    // The query only carries the filters that are on: the server reads an
    // absent param as "off", and `RecipeSearchQuery` is readonly because a
    // query is a value to send, not a workspace to mutate.
    const query: RecipeSearchQuery = {
      ...(name !== '' ? { name } : {}),
      ...(ingredientId !== undefined ? { ingredientId } : {}),
      ...(this.strict() ? { strict: true } : {}),
      ...(this.allergenSafe() ? { allergenSafe: true } : {}),
      threshold: 0,
    };

    this.mode.set('search');
    this.loading.set(true);
    this.loadError.set(null);
    this.ingredientStale.set(false);
    this.suggestEmpty.set(false);
    this.suggestError.set(null);
    this.recipesService.search(query).subscribe({
      next: (recipes) => {
        if (seq !== this.searchSeq) {
          return;
        }
        this.recipes.set(recipes);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.searchSeq) {
          return;
        }
        this.loading.set(false);
        // A 404 with an ingredient filter is the backend saying the chosen
        // ingredient is no longer in the catalog. That is not a generic
        // failure and not a retry: the same request can only 404 again, so it
        // is surfaced as its own state whose repair is clearing the filter.
        this.ingredientStale.set(error.status === 404 && ingredientId !== undefined);
        this.loadError.set(toErrorResponse(error).message || 'Unable to load recipes.');
      },
    });
  }

  protected suggest(): void {
    const seq = ++this.searchSeq;
    this.mode.set('suggest');
    this.suggestLoading.set(true);
    this.suggestError.set(null);
    this.suggestEmpty.set(false);
    this.loadError.set(null);
    this.loading.set(false);

    this.recipesService
      .recommend({
        strict: this.strict() ? true : false,
        allergenSafe: this.allergenSafe() ? true : false,
      })
      .subscribe({
        next: (recipes) => {
          if (seq !== this.searchSeq) {
            return;
          }
          this.recipes.set(recipes);
          this.suggestLoading.set(false);
          this.suggestEmpty.set(recipes.length === 0);
        },
        error: (error: HttpErrorResponse) => {
          if (seq !== this.searchSeq) {
            return;
          }
          this.suggestLoading.set(false);
          this.suggestError.set(toErrorResponse(error).message || 'Unable to load suggestions.');
        },
      });
  }

  protected retrySuggest(): void {
    this.suggest();
  }

  /**
   * Renders the ingredient box: the picked hit's name, the raw text otherwise.
   *
   * Material asks this for whatever the control holds, and the control holds
   * both a string (being typed) and a hit (just picked), so both cases are named.
   */
  protected displayIngredient(value: IngredientSearchHit | string | null): string {
    if (value === null) {
      return '';
    }
    return typeof value === 'string' ? value : value.name;
  }

  /**
   * Reacts to a change of the ingredient box.
   *
   * A value that is not a string is a hit Material wrote back on selection, and
   * {@link onIngredientSelected} owns that path — re-searching the catalog or
   * dropping the filter here would fight it. A string is the user editing the
   * box: any previous pick no longer names what the text says, so the filter is
   * dropped and the grid re-searched while the catalog is queried for the new
   * text.
   */
  private onIngredientValueChange(value: IngredientSearchHit | string | null): void {
    if (value !== null && typeof value === 'object') {
      return;
    }

    const query = typeof value === 'string' ? value.trim() : '';
    if (this.selectedIngredient() !== null) {
      this.selectedIngredient.set(null);
      this.ingredientStale.set(false);
      this.load();
    }

    if (query === '') {
      this.ingredientOptions.set([]);
      this.ingredientError.set(null);
      this.ingredientSearching.set(false);
      return;
    }

    this.searchIngredients(query);
  }

  /** Asks the catalog for foods matching the text, with the same race guard as a search. */
  private searchIngredients(query: string): void {
    const seq = ++this.catalogSeq;
    this.ingredientSearching.set(true);
    this.ingredientError.set(null);
    this.catalog.searchIngredients(query).subscribe({
      next: (hits) => {
        if (seq !== this.catalogSeq) {
          return;
        }
        this.ingredientOptions.set(hits);
        this.ingredientSearching.set(false);
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.catalogSeq) {
          return;
        }
        this.ingredientOptions.set([]);
        this.ingredientSearching.set(false);
        this.ingredientError.set(this.describe(error, 'Unable to search the food catalog.'));
      },
    });
  }

  /**
   * Resolves a picked hit into the internal ingredient id and re-searches.
   *
   * The detail read is not optional: the catalog search answers provider
   * references (`fdcId`), while the recipe endpoint filters by the internal id,
   * and the same read is what materializes the food in the local catalog — the
   * endpoint answers 404 for an ingredient it has never stored. Only once the
   * id is known does the search carry `ingredientId`.
   */
  protected onIngredientSelected(event: MatAutocompleteSelectedEvent): void {
    const hit = event.option.value as IngredientSearchHit;
    const seq = ++this.ingredientSeq;
    this.ingredientError.set(null);
    this.ingredientResolving.set(true);

    this.catalog.getIngredient(hit.fdcId).subscribe({
      next: (detail) => {
        if (seq !== this.ingredientSeq) {
          return;
        }
        this.ingredientResolving.set(false);
        this.selectedIngredient.set({ id: detail.id, name: detail.name });
        this.ingredientOptions.set([]);
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        if (seq !== this.ingredientSeq) {
          return;
        }
        this.ingredientResolving.set(false);
        // The pick could not be resolved, so the box must not keep claiming it:
        // the text and the filter are dropped together and the user tries again.
        this.ingredientControl.setValue(null, { emitEvent: false });
        this.selectedIngredient.set(null);
        this.ingredientError.set(this.describe(error, 'Unable to read this ingredient.'));
      },
    });
  }

  /**
   * Drops the ingredient filter.
   *
   * The control is reset without an event, so the single re-search below is the
   * only request a clear spends; letting `valueChanges` fire as well would ask
   * the same question twice. A clear that only removes loose text — a pick was
   * never made — changes nothing the server knows, so it costs no request.
   */
  protected clearIngredient(): void {
    const hadSelection = this.selectedIngredient() !== null;
    const hasText = this.ingredientControl.value !== null && this.ingredientControl.value !== '';
    if (!hadSelection && !hasText) {
      return;
    }

    this.ingredientControl.setValue(null, { emitEvent: false });
    this.selectedIngredient.set(null);
    this.ingredientOptions.set([]);
    this.ingredientError.set(null);
    this.ingredientStale.set(false);

    if (hadSelection) {
      this.load();
    }
  }

  /** The backend's own wording for a failure, with a fallback when it sent none. */
  private describe(error: HttpErrorResponse, fallback: string): string {
    return toErrorResponse(error).message || fallback;
  }

  /**
   * Flips strict mode and re-searches.
   *
   * Turning strict on locks the slider at 100 (see the strict invariant of the
   * class doc); turning it off unlocks the slider at the value it was left, so
   * the user's previous bar is preserved instead of being reset to a default
   * they never chose.
   */
  protected onStrictChange(change: MatSlideToggleChange): void {
    this.strict.set(change.checked);
    if (change.checked) {
      this.filters.controls.threshold.setValue(100);
    }
    this.load();
  }

  /** Flips the allergen-safe filter and re-searches, so excluded recipes leave. */
  protected onAllergenSafeChange(change: MatSlideToggleChange): void {
    this.allergenSafe.set(change.checked);
    if (this.mode() === 'suggest') {
      this.suggest();
      return;
    }
    this.load();
  }

  protected coveragePercent = coveragePercent;
  protected coverageClass = coverageClass;
  protected sourceLabel = sourceLabel;
}
