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
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { toErrorResponse } from '../../core/auth/auth.models';
import { RecipeDetail, RecipeSummary } from '../../core/recipes/recipes.models';
import { RecipesService } from '../../core/recipes/recipes.service';

/**
 * Load state of one collection tab (favorites, saved or created).
 *
 * Every tab runs the exact same lifecycle — read its rows and keep its own
 * loading/error/empty — so the behaviour lives here once and the page holds
 * one instance per tab, instead of duplicating signals and methods per tab.
 * Only the backing differs: favorites and saved answer bare ids that
 * {@link resolveIds} turns into tiles, while created already answers full
 * summaries. Signals are read from the page template, hence the public
 * members.
 */
export class CollectionTab {
  private readonly summaries = signal<RecipeSummary[]>([]);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  /** Resolved tiles, in the order the API answered (most recent first). */
  readonly recipes = this.summaries.asReadonly();

  /** Empty only once this tab settled; a failure is its own state, not "nothing here". */
  readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.summaries().length === 0,
  );

  constructor(
    private readonly loadSource: () => Observable<RecipeSummary[]>,
    private readonly errorFallback: string,
  ) {}

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.loadSource().subscribe({
      next: (recipes) => {
        this.summaries.set(recipes);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(toErrorResponse(error).message || this.errorFallback);
      },
    });
  }
}

/**
 * Turns an id list into tiles, dropping the recipes that no longer exist.
 *
 * The reads run together (`forkJoin`), so the tab waits for the whole grid
 * instead of filling in tile by tile; each failure is swallowed locally so one
 * orphan cannot fail the batch. The per-id detail read is used rather than the
 * unfiltered search because the search scores and drops rows on coverage — a
 * favorite with no coverage (a `USER` recipe) or one below the default bar
 * would silently disappear from a page that exists exactly to show it. A
 * per-id read resolves every recipe regardless of coverage, and a 404 (or any
 * failure) means the recipe left the catalog, so that row is skipped rather
 * than rendered as an empty tile.
 */
function resolveIds(
  recipesService: RecipesService,
  loadIds: () => Observable<number[]>,
): Observable<RecipeSummary[]> {
  return loadIds().pipe(
    switchMap((ids) =>
      ids.length === 0
        ? of<RecipeSummary[]>([])
        : forkJoin(
            ids.map((id) => recipesService.getDetail(id).pipe(catchError(() => of(null)))),
          ).pipe(
            map((details) =>
              details.filter((detail): detail is RecipeDetail => detail !== null).map(asTile),
            ),
          ),
    ),
  );
}

/**
 * Widens a recipe detail into the summary shape the tiles render.
 *
 * `missingCount` is a search-row figure the detail payload never carries, so
 * it travels as null — the model's "no coverage counted" value, which is the
 * truth for a per-id read.
 */
function asTile(detail: RecipeDetail): RecipeSummary {
  return { ...detail, missingCount: null };
}

/** One collection tab plus the copy its empty state shows. */
interface CollectionTabView {
  readonly label: string;
  readonly icon: string;
  readonly emptyTitle: string;
  readonly emptyHint: string;
  readonly state: CollectionTab;
}

/**
 * "My cookbook": the recipes the user kept or made, split into Favorites,
 * Saved and Created.
 *
 * Favorites and Saved answer only ids (most recent first), which is not enough
 * to draw a tile, so each id is resolved through the recipe detail
 * ({@link resolveIds} — coverage-safe, unlike the scoring search). Created
 * comes from `GET /api/recipes/mine`, which already answers full summaries
 * (name, image, category, area), so that tab renders them directly with no
 * per-id round trip. A tab whose own request fails keeps that failure and gets
 * a retry, independent of the other tabs.
 */
@Component({
  selector: 'app-collection-page',
  imports: [
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatIcon,
    MatProgressSpinner,
    MatTab,
    MatTabGroup,
    RouterLink,
  ],
  templateUrl: './collection-page.html',
  styleUrl: './collection-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CollectionPage implements OnInit {
  private readonly recipes = inject(RecipesService);

  /** Favorites first: the tab the page opens on; Created trails, being the newest. */
  protected readonly tabs: readonly CollectionTabView[] = [
    {
      label: 'Favorites',
      icon: 'favorite_border',
      emptyTitle: 'No favorite recipes yet',
      emptyHint: 'Mark a recipe as a favorite from the search and it will show up here.',
      state: new CollectionTab(
        () => resolveIds(this.recipes, () => this.recipes.favoriteIds()),
        'Could not load your favorites.',
      ),
    },
    {
      label: 'Saved',
      icon: 'bookmark_border',
      emptyTitle: 'No saved recipes yet',
      emptyHint:
        'Save a recipe from the search and it will show up here so it is at hand when you cook.',
      state: new CollectionTab(
        () => resolveIds(this.recipes, () => this.recipes.savedIds()),
        'Could not load your saved recipes.',
      ),
    },
    {
      // The only tab whose endpoint answers full summaries: no id resolution,
      // the rows render straight from `GET /api/recipes/mine`.
      label: 'Created',
      icon: 'edit_note',
      emptyTitle: 'No created recipes yet',
      emptyHint: 'Create a recipe and it will show up here.',
      state: new CollectionTab(() => this.recipes.mine(), 'Could not load your created recipes.'),
    },
  ];

  ngOnInit(): void {
    // All lists load together: they are cheap reads, and the tabs are meant to
    // be switched instantly, so deferring a hidden one would only add latency
    // when the user reaches for it.
    for (const tab of this.tabs) {
      tab.state.load();
    }
  }
}
