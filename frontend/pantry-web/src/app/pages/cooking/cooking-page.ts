import { DatePipe } from '@angular/common';
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
import { Router, RouterLink } from '@angular/router';
import { toErrorResponse } from '../../core/auth/auth.models';
import {
  CookingSession,
  CookingSessionStatus,
  isTerminal,
} from '../../core/cooking/cooking.models';
import { CookingService } from '../../core/cooking/cooking.service';
import { RecipesService } from '../../core/recipes/recipes.service';

/** How each status is named in the UI. */
const STATUS_LABELS: Record<CookingSessionStatus, string> = {
  IN_PROGRESS: 'In progress',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  ABANDONED: 'Abandoned',
};

const statusLabel = (status: CookingSessionStatus): string => STATUS_LABELS[status] ?? status;

/**
 * "Cooking": the user's sessions, live ones first and history underneath.
 *
 * The two groups are a reading of the same list and not two requests: the API
 * already answers every session, most recent first, and the split is a filter
 * over it ({@link isTerminal}). Neither group is re-sorted — an order the
 * server chose is the one to keep.
 *
 * A session carries only its `recipeId`, so the page reads the recipe catalog
 * once (`GET /api/recipes`, the unfiltered local list) to name each row. That
 * is a single request for the whole screen rather than one per recipe, and a
 * name that is missing falls back to the id instead of hiding the session — a
 * session is a fact even if its recipe left the catalog.
 *
 * A terminal session can start a new one for the same recipe ("Cook again":
 * the same dish cooked twice is two sessions, never a reused one), which is the
 * one write this page performs. Everything else is navigation to
 * `/cooking/:id`, where the live controls are.
 */
@Component({
  selector: 'app-cooking-page',
  imports: [
    DatePipe,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatIcon,
    MatProgressSpinner,
    RouterLink,
  ],
  templateUrl: './cooking-page.html',
  styleUrl: './cooking-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookingPage implements OnInit {
  private readonly cooking = inject(CookingService);
  private readonly recipes = inject(RecipesService);
  private readonly router = inject(Router);

  private readonly sessions = signal<CookingSession[]>([]);
  /** Recipe names by id, empty until the catalog answers. */
  private readonly recipeNames = signal<ReadonlyMap<number, string>>(new Map());

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  /** Recipe id of the session being created, so a second click is refused. */
  protected readonly startingId = signal<number | null>(null);
  protected readonly actionError = signal<string | null>(null);

  /** Live sessions: the ones the user can still pause, finish or abandon. */
  protected readonly active = computed(() =>
    this.sessions().filter((session) => !isTerminal(session.status)),
  );

  /** Finished or abandoned sessions, the ones worth starting again. */
  protected readonly history = computed(() =>
    this.sessions().filter((session) => isTerminal(session.status)),
  );

  protected readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.sessions().length === 0,
  );

  ngOnInit(): void {
    this.load();
  }

  /**
   * Reads the sessions and, with them, the names to label them.
   *
   * The two reads are independent: a session list that arrives without the
   * catalog is perfectly usable (the rows fall back to the recipe id), so the
   * name lookup is a second, best-effort read that never sets the page's error.
   */
  protected load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.actionError.set(null);
    this.cooking.listSessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
        this.loadRecipeNames(sessions);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(
          toErrorResponse(error).message || 'Unable to load your cooking sessions.',
        );
      },
    });
  }

  /**
   * Labels the sessions with recipe names, from the one local catalog read.
   *
   * The unfiltered recipe list is deliberately local (the H4 rule: an
   * un-filtered listing never calls the provider), so this costs one bounded
   * request whatever the size of the history.
   */
  private loadRecipeNames(sessions: readonly CookingSession[]): void {
    if (sessions.length === 0) {
      return;
    }
    this.recipes.search().subscribe({
      next: (recipes) => {
        this.recipeNames.set(new Map(recipes.map((recipe) => [recipe.id, recipe.name])));
      },
      error: () => {
        // A name is a convenience: the rows keep the recipe id and stay usable.
      },
    });
  }

  /** The name of a session's recipe, or its id when the catalog has no name. */
  protected recipeName(session: CookingSession): string {
    return this.recipeNames().get(session.recipeId) ?? `Recipe #${session.recipeId}`;
  }

  /**
   * Starts a fresh session for the recipe of a previous one and opens it.
   *
   * A new session rather than reopening the old: a completed or abandoned
   * session is a record of what happened, and reusing it would rewrite history.
   */
  protected startAgain(recipeId: number): void {
    if (this.startingId() !== null) {
      return;
    }
    this.startingId.set(recipeId);
    this.actionError.set(null);
    this.cooking.createSession(recipeId).subscribe({
      next: (session) => {
        this.startingId.set(null);
        this.router.navigate(['/cooking', session.id]);
      },
      error: (error: HttpErrorResponse) => {
        this.startingId.set(null);
        this.actionError.set(
          toErrorResponse(error).message || 'Unable to start this cooking session.',
        );
      },
    });
  }

  protected statusLabel = statusLabel;
  protected isTerminal = isTerminal;
}
