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
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardTitle,
} from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { toErrorResponse } from '../../core/auth/auth.models';
import {
  RecipeSearchQuery,
  RecipeSource,
  RecipeSummary,
} from '../../core/recipes/recipes.models';
import {
  coverageClass,
  coveragePercent,
  orderByCoverage,
  passesThreshold,
} from '../../core/recipes/recipe-matching';
import { RecipesService } from '../../core/recipes/recipes.service';

/** How the two recipe origins are named in the UI. */
const SOURCE_LABELS: Record<RecipeSource, string> = {
  USER: 'Own',
  THE_MEAL_DB: 'TheMealDB',
};

const sourceLabel = (source: RecipeSource): string => SOURCE_LABELS[source] ?? source;

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
 */
@Component({
  selector: 'app-recipes-page',
  imports: [
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatChip,
    MatChipSet,
    MatFormField,
    MatIcon,
    MatInput,
    MatLabel,
    MatProgressSpinner,
    MatSlider,
    MatSliderThumb,
    MatSlideToggle,
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

  /**
   * The search box.
   *
   * A form control and not a signal so the debounce rides Angular's reactive
   * stream: `valueChanges` already exists, filters and subscriptions are the
   * framework's, and nothing in the template has to remember a timer.
   */
  readonly nameControl = this.fb.control('');

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

  ngOnInit(): void {
    this.nameControl.valueChanges
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged())
      .subscribe(() => this.load());
    this.filters.controls.threshold.valueChanges.subscribe((value) => this.threshold.set(value));
    this.load();
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
    // The query only carries the filters that are on: the server reads an
    // absent param as "off", and `RecipeSearchQuery` is readonly because a
    // query is a value to send, not a workspace to mutate.
    const query: RecipeSearchQuery = {
      ...(name !== '' ? { name } : {}),
      ...(this.strict() ? { strict: true } : {}),
      ...(this.allergenSafe() ? { allergenSafe: true } : {}),
    };

    this.loading.set(true);
    this.loadError.set(null);
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
        this.loadError.set(toErrorResponse(error).message || 'Unable to load recipes.');
      },
    });
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
    this.load();
  }

  protected coveragePercent = coveragePercent;
  protected coverageClass = coverageClass;
  protected sourceLabel = sourceLabel;
}
