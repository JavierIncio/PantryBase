import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatChip, MatChipSet } from '@angular/material/chips';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toErrorResponse } from '../../core/auth/auth.models';
import { UnitService } from '../../core/catalog/unit.service';
import { CookingService } from '../../core/cooking/cooking.service';
import {
  IngredientCoverageRow,
  coverageClass,
  coveragePercent,
  formatAmount,
  pairCoverage,
} from '../../core/recipes/recipe-matching';
import { RecipeDetail, RecipeSource } from '../../core/recipes/recipes.models';
import { RecipesService } from '../../core/recipes/recipes.service';

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
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatChip,
    MatChipSet,
    MatIcon,
    MatProgressSpinner,
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

  protected readonly recipe = signal<RecipeDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly notFound = signal(false);

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
   */
  protected startCooking(): void {
    const recipe = this.recipe();
    if (recipe === null || this.starting()) {
      return;
    }

    this.starting.set(true);
    this.startError.set(null);
    this.cooking.createSession(recipe.id).subscribe({
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
  protected nutrientText = (value: number | null): string => (value === null ? '—' : String(value));
}
