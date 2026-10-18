import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlideToggle, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toErrorResponse } from '../../core/auth/auth.models';
import { UnitService } from '../../core/catalog/unit.service';
import { CookingService } from '../../core/cooking/cooking.service';
import { RecipeAvailabilityPanel } from '../../core/recipes/recipe-availability-panel';
import {
  IngredientCoverageRow,
  RecipeAvailability,
  coverageClass,
  coveragePercent,
  formatAmount,
  pairCoverage,
  recipeAvailability,
} from '../../core/recipes/recipe-matching';
import { RecipeDetail, RecipeSource } from '../../core/recipes/recipes.models';
import { RecipesService } from '../../core/recipes/recipes.service';
import { StartCookingDialog } from './start-cooking-dialog';

/** How the two recipe origins are named in the UI. */
const SOURCE_LABELS: Record<RecipeSource, string> = {
  USER: 'Own',
  THE_MEAL_DB: 'TheMealDB',
};

const sourceLabel = (source: RecipeSource): string => SOURCE_LABELS[source] ?? source;

/**
 * Joins the category and area of a recipe into one header line, dropping the
 * separator when either half is missing: "Pasta · Italian" reads, but an empty
 * category followed by " · Italian" would look like a typo.
 */
const categoryArea = (category: string, area: string): string | null => {
  const parts = [category, area].filter((part) => part.trim() !== '');
  return parts.length > 0 ? parts.join(' · ') : null;
};

/**
 * One recipe in full: what it asks for, how the pantry covers it, and what it
 * gives back per serving.
 *
 * The page is a read, so its states are the read's: a spinner while it
 * travels, a page-level error with a retry when the request fails for any
 * reason but one, and a dedicated "not found" card for the 404. The 404 is the
 * one failure the user can act on (the recipe is gone or never existed — the
 * pantry cannot repair it), and a recipe that does not exist must not look
 * like a broken page: it says so and offers the way back to the grid.
 *
 * The ingredient list is the join explained in {@link pairCoverage}: the
 * recipe's own lines in their own order, each one carrying the coverage the
 * pantry has for it. A covered line is drawn with its check; a line the pantry
 * only partially holds is drawn with a warning and both figures (needed vs.
 * available), because the whole point of the breakdown is that the user can
 * see exactly how far the gap is. A line the catalog never understood
 * (`matched: false`) is drawn apart — provider wording in muted type, a
 * "Not in catalog" label and no quantities at all — plus a footnote saying
 * those lines are why the coverage percentage falls: they cannot be measured
 * against the pantry, so they count as missing. Unit codes become text
 * through the unit catalog, never through a local table.
 */
@Component({
  selector: 'app-recipe-detail-page',
  imports: [
    MatButton,
    MatIconButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatChip,
    MatChipSet,
    MatIcon,
    MatProgressSpinner,
    MatSlideToggle,
    RecipeAvailabilityPanel,
    RouterLink,
  ],
  templateUrl: './recipe-detail-page.html',
  styleUrl: './recipe-detail-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipeDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recipesService = inject(RecipesService);
  private readonly cooking = inject(CookingService);
  private readonly units = inject(UnitService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly recipe = signal<RecipeDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly notFound = signal(false);

  /** Tracks pending favorite toggle. */
  protected readonly favoritePending = signal(false);
  /** Tracks pending saved toggle. */
  protected readonly savedPending = signal(false);
  /** Tracks the publish/unpublish call while it is in flight. */
  protected readonly publishPending = signal(false);

  /** True while the cooking session of this recipe is being created. */
  protected readonly starting = signal(false);
  /** Message of a refused session start; the recipe stays on screen. */
  protected readonly startError = signal<string | null>(null);

  /**
   * The ingredient lines with their coverage attached, in the recipe's order.
   *
   * Derived from the recipe so no write can ever leave the list and the
   * payload's two collections out of step.
   */
  protected readonly rows = computed<IngredientCoverageRow[]>(() => {
    const recipe = this.recipe();
    return recipe ? pairCoverage(recipe.ingredients, recipe.coverage) : [];
  });

  /**
   * Whether any line escaped the catalog, i.e. whether the card owes the user
   * the footnote about the coverage percentage.
   */
  protected readonly hasUnmatched = computed(() =>
    this.rows().some((row) => !row.ingredient.matched),
  );

  /**
   * The pantry classification of the loaded recipe, or null before it arrives.
   *
   * Derived from the detail so the panel, the start-cooking confirmation and the
   * button's behaviour can never disagree: they all read the same computed
   * value, and it is recomputed only when the recipe itself changes.
   */
  protected readonly availability = computed<RecipeAvailability | null>(() => {
    const recipe = this.recipe();
    return recipe ? recipeAvailability(recipe) : null;
  });

  /**
   * Whether the publish toggle may be shown.
   *
   * Only on the caller's own `USER` recipes: a provider recipe is already
   * public (the API answers 400 for it) and someone else's recipe is not ours
   * to publish (403), so on any other recipe the control could only ever
   * answer with a refusal.
   */
  protected readonly canPublish = computed(() => {
    const recipe = this.recipe();
    return recipe !== null && recipe.owned === true && recipe.source === 'USER';
  });

  ngOnInit(): void {
    // The amounts of the ingredient lines are labelled from the unit catalog;
    // loading it is a no-op when another page already did this session.
    this.units.load();
    this.load();
  }

  /**
   * Reads the recipe named by the `:id` route parameter.
   *
   * An id the URL cannot even hold (not a positive integer) is treated as the
   * 404 it would have been: no request is made for a route that cannot exist.
   */
  protected load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id) || id <= 0) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.loadError.set(null);
    this.notFound.set(false);
    this.recipesService.getDetail(id).subscribe({
      next: (recipe) => {
        this.recipe.set(recipe);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 404) {
          this.notFound.set(true);
          return;
        }
        this.loadError.set(toErrorResponse(error).message || 'Unable to load this recipe.');
      },
    });
  }

  /**
   * The amount the recipe asks for, canonically, with its unit named.
   *
   * `null` only when the line carries no quantity at all — an unmatched
   * provider line — and the template draws those apart instead of calling
   * this, so a matched line always answers with figures.
   */
  protected neededText(row: IngredientCoverageRow): string | null {
    const coverage = row.coverage;
    const amount = coverage?.neededAmount ?? row.ingredient.canonicalAmount;
    const unitCode = coverage?.neededUnitCode ?? row.ingredient.canonicalUnitCode;
    if (amount === null || unitCode === null) {
      return null;
    }
    return `${formatAmount(amount)} ${this.units.label(unitCode)}`;
  }

  /**
   * The amount the pantry holds, or an explicit "unknown" marker.
   *
   * A line without a coverage row has no pantry figure to report; rendering
   * nothing would read as "free", so the gap is named instead.
   */
  protected availableText(row: IngredientCoverageRow): string {
    const coverage = row.coverage;
    if (!coverage) {
      return 'not in pantry';
    }
    return `${formatAmount(coverage.availableAmount)} ${this.units.label(coverage.availableUnitCode)}`;
  }

  /**
   * Starts a cooking session for this recipe and opens it.
   *
   * The session is created here rather than by the cooking page so the recipe's
   * id is never a query parameter the URL has to carry: the response is what
   * names the session, and a failed creation is a message on this page — the
   * recipe the user is reading stays exactly where it was.
   *
   * When the pantry check found gaps, this class's `availability` opens a
   * confirmation first: starting a session deducts nothing, so the check is a
   * warning for the user to acknowledge, not a refusal — proceeding is their
   * call, and cancelling creates no session. With everything covered the session
   * starts directly, because there is nothing to warn about.
   */
  protected startCooking(): void {
    const recipe = this.recipe();
    if (recipe === null || this.starting()) {
      return;
    }

    const availability = this.availability();
    if (availability?.hasGaps) {
      this.confirmStart(recipe.id, availability);
      return;
    }

    this.createSession(recipe.id);
  }

  /**
   * Asks the user to acknowledge the gaps, and creates the session if they do.
   *
   * Only an explicit `true` proceeds: dismissing the dialog is a no, and no
   * session is created, which is the state the button's click found.
   */
  private confirmStart(recipeId: number, availability: RecipeAvailability): void {
    this.dialog
      .open(StartCookingDialog, {
        data: availability,
        autoFocus: 'first-tabbable',
        restoreFocus: true,
        maxWidth: '34rem',
        width: 'calc(100% - 2rem)',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (confirmed === true) {
          this.createSession(recipeId);
        }
      });
  }

  /** Sends the session creation; a failure is a message on this page. */
  private createSession(recipeId: number): void {
    this.starting.set(true);
    this.startError.set(null);
    this.cooking.createSession(recipeId).subscribe({
      next: (session) => {
        this.starting.set(false);
        this.router.navigate(['/cooking', session.id]);
      },
      error: (error: HttpErrorResponse) => {
        this.starting.set(false);
        this.startError.set(
          toErrorResponse(error).message || 'Unable to start cooking this recipe.',
        );
      },
    });
  }

  protected sourceLabel = sourceLabel;
  protected categoryArea = categoryArea;
  protected coveragePercent = coveragePercent;
  protected coverageClass = coverageClass;
  protected formatAmount = formatAmount;
  protected readonly favoriteIcon = (fav: boolean | null): string =>
    fav === true ? 'favorite' : 'favorite_border';
  protected readonly favoriteAria = (fav: boolean | null): string =>
    fav === true ? 'Remove from favorites' : 'Add to favorites';

  protected readonly savedIcon = (saved: boolean | null): string =>
    saved === true ? 'bookmark' : 'bookmark_border';
  protected readonly savedAria = (saved: boolean | null): string =>
    saved === true ? 'Remove from saved' : 'Add to saved';

  /**
   * Toggles favorite state on the detail page with optimistic update.
   */
  protected toggleFavorite(): void {
    const recipe = this.recipe();
    if (!recipe || this.favoritePending()) {
      return;
    }
    const next = !(recipe.favorite === true);
    const previous = recipe.favorite;
    this.recipe.set({ ...recipe, favorite: next });
    this.favoritePending.set(true);
    this.recipesService.toggleFavorite(recipe.id, next).subscribe({
      next: () => this.favoritePending.set(false),
      error: (error: HttpErrorResponse) => {
        const current = this.recipe();
        if (current?.id === recipe.id) {
          this.recipe.set({ ...current, favorite: previous });
        }
        this.favoritePending.set(false);
        this.snackBar.open(toErrorResponse(error).message || 'Could not update favorites', 'OK', {
          duration: 8000,
        });
      },
    });
  }

  /**
   * Toggles saved state on the detail page with optimistic update.
   */
  protected toggleSaved(): void {
    const recipe = this.recipe();
    if (!recipe || this.savedPending()) {
      return;
    }
    const next = !(recipe.saved === true);
    const previous = recipe.saved;
    this.recipe.set({ ...recipe, saved: next });
    this.savedPending.set(true);
    this.recipesService.toggleSaved(recipe.id, next).subscribe({
      next: () => this.savedPending.set(false),
      error: (error: HttpErrorResponse) => {
        const current = this.recipe();
        if (current?.id === recipe.id) {
          this.recipe.set({ ...current, saved: previous });
        }
        this.savedPending.set(false);
        this.snackBar.open(
          toErrorResponse(error).message || 'Could not update saved recipes',
          'OK',
          { duration: 8000 },
        );
      },
    });
  }

  /**
   * Publishes or takes back this recipe with optimistic update.
   *
   * The toggle flips ahead of the answer — visibility is the user's intent and
   * the request is one round trip — and a refusal puts it back exactly where
   * it was: the previous detail is restored wholesale, because the backend's
   * answer is the authority on the recipe and a revert that only touched
   * `published` could leave the rest of the copy stale.
   *
   * A failure is reported in a snackbar with the backend's own wording first
   * and a status-specific fallback second, because the three refusals say
   * different things: 403 means the recipe belongs to someone else, 400 that
   * it is not a user recipe, and neither has a repair this page can offer —
   * they are facts to read, not errors to retry.
   */
  protected togglePublish(change: MatSlideToggleChange): void {
    const recipe = this.recipe();
    if (recipe === null || !this.canPublish() || this.publishPending()) {
      return;
    }
    const previous = recipe;
    this.recipe.set({ ...recipe, published: change.checked });
    this.publishPending.set(true);
    const request$ = change.checked
      ? this.recipesService.publish(recipe.id)
      : this.recipesService.unpublish(recipe.id);
    request$.subscribe({
      next: () => this.publishPending.set(false),
      error: (error: HttpErrorResponse) => {
        const current = this.recipe();
        if (current?.id === previous.id) {
          this.recipe.set(previous);
        }
        this.publishPending.set(false);
        this.snackBar.open(this.describePublishFailure(error), 'OK', { duration: 8000 });
      },
    });
  }

  /** The backend's own wording for a publish refusal, or a status-specific fallback. */
  private describePublishFailure(error: HttpErrorResponse): string {
    const message = toErrorResponse(error).message;
    if (message) {
      return message;
    }
    switch (error.status) {
      case 403:
        return 'This recipe is not yours, so it cannot be published.';
      case 400:
        return 'Only your own recipes can be published.';
      default:
        return "Could not change the recipe's visibility.";
    }
  }

  protected nutrientText = (value: number | null): string => (value === null ? '—' : String(value));
}
