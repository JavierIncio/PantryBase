import { DatePipe, LowerCasePipe } from '@angular/common';
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
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toErrorResponse } from '../../core/auth/auth.models';
import { UnitService } from '../../core/catalog/unit.service';
import {
  CompletionDeduction,
  CookingSession,
  CookingSessionStatus,
  asCompletionDeduction,
  isTerminal,
  runsOut,
} from '../../core/cooking/cooking.models';
import { CookingService } from '../../core/cooking/cooking.service';
import { splitSteps } from '../../core/cooking/cooking-steps';
import { formatAmount } from '../../core/recipes/recipe-matching';
import { RecipeDetail } from '../../core/recipes/recipes.models';
import { RecipesService } from '../../core/recipes/recipes.service';
import { ShortageDialog } from './shortage-dialog';

/** How each status is named in the UI. */
const STATUS_LABELS: Record<CookingSessionStatus, string> = {
  IN_PROGRESS: 'In progress',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  ABANDONED: 'Abandoned',
};

const statusLabel = (status: CookingSessionStatus): string => STATUS_LABELS[status] ?? status;

/**
 * One cooking session: the recipe, a local step checklist and the state controls.
 *
 * The session is the page's subject and the recipe is a convenience: the status,
 * the controls and the deduction are all read from `/api/cooking`, so a recipe
 * that fails to load still leaves a usable page (the checklist is simply empty)
 * instead of an error for a fact that is not the session's.
 *
 * The step checklist is **local and never persisted**: the backend models no
 * structured steps and stores only the session's status, so the steps are
 * derived from the recipe's free-text `instructions` (see {@link splitSteps})
 * and ticking one writes nothing anywhere. Reloading the page starts the marks
 * afresh; that is the honest behaviour of a convenience that has no server side.
 *
 * Completing is the one transition with a second act. A completion that would
 * leave the pantry short comes back as a **409 carrying the deduction plan**
 * rather than an error — the backend has changed nothing and wants the user to
 * confirm. That plan is shown in {@link ShortageDialog} and, if accepted, the
 * completion is re-sent with `acceptShortage`; the same 409 used for an illegal
 * transition carries the standard error body instead and is rendered as the
 * plain error it is. Telling the two apart is
 * {@link asCompletionDeduction}'s whole job.
 *
 * A terminal session (`COMPLETED`/`ABANDONED`) is read-only: the backend refuses
 * any further transition, so no control that could only fail is offered.
 */
@Component({
  selector: 'app-cooking-session-page',
  imports: [
    DatePipe,
    LowerCasePipe,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatCheckbox,
    MatIcon,
    MatProgressSpinner,
    RouterLink,
  ],
  templateUrl: './cooking-session-page.html',
  styleUrl: './cooking-session-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookingSessionPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly cooking = inject(CookingService);
  private readonly recipes = inject(RecipesService);
  private readonly units = inject(UnitService);
  private readonly dialog = inject(MatDialog);

  protected readonly session = signal<CookingSession | null>(null);
  protected readonly recipe = signal<RecipeDetail | null>(null);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly notFound = signal(false);

  /** True while the recipe read is in flight; the session is already on screen. */
  protected readonly recipeLoading = signal(false);
  /** Message of a failed recipe read; the checklist is what the user loses. */
  protected readonly recipeError = signal<string | null>(null);

  /** Message from a refused state change, rendered next to the controls. */
  protected readonly transitionError = signal<string | null>(null);
  /** True while a state change is in flight, so no two can overlap. */
  protected readonly busy = signal(false);

  /** Steps derived from the recipe's instructions; empty until it is loaded. */
  protected readonly steps = computed(() => splitSteps(this.recipe()?.instructions ?? ''));

  /**
   * Indices of the steps ticked in this visit.
   *
   * A signal holding immutable sets, so a tick replaces the set and the OnPush
   * view re-renders; nothing here is sent to the server (see the class doc).
   */
  private readonly doneSteps = signal<ReadonlySet<number>>(new Set());

  protected readonly doneCount = computed(() => this.doneSteps().size);

  /** A finished or abandoned session, which the state machine cannot move. */
  protected readonly terminal = computed(() => {
    const session = this.session();
    return session !== null && isTerminal(session.status);
  });

  /** The plan of a completed session, worth showing as what was deducted. */
  protected readonly deduction = computed(() => this.session()?.deduction ?? null);

  ngOnInit(): void {
    // The deduction figures and the ingredient amounts are codes until the unit
    // catalog names them; loading it is a no-op when a page already did.
    this.units.load();
    this.load();
  }

  /**
   * Reads the session named by the `:id` route parameter.
   *
   * An id the URL cannot hold (not a positive integer) is treated as the 404 it
   * would have been, without spending a request on a route that cannot exist.
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
    this.cooking.getSession(id).subscribe({
      next: (session) => {
        this.session.set(session);
        this.loading.set(false);
        this.loadRecipe(session.recipeId);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 404) {
          this.notFound.set(true);
          return;
        }
        this.loadError.set(
          toErrorResponse(error).message || 'Unable to load this cooking session.',
        );
      },
    });
  }

  /**
   * Reads the recipe to name the dish and build the checklist.
   *
   * Kept off the page's own error state: the session is already displayable and
   * a failed recipe read costs the checklist, not the session. The message is
   * still shown so the user knows why the steps are missing.
   */
  private loadRecipe(recipeId: number): void {
    this.recipe.set(null);
    this.recipeError.set(null);
    this.recipeLoading.set(true);
    this.recipes.getDetail(recipeId).subscribe({
      next: (recipe) => {
        this.recipe.set(recipe);
        this.recipeLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.recipeLoading.set(false);
        this.recipeError.set(toErrorResponse(error).message || 'Unable to load the recipe.');
      },
    });
  }

  /** The recipe's name, or its id while the read is in flight or failed. */
  protected recipeTitle(): string {
    return this.recipe()?.name ?? `Recipe #${this.session()?.recipeId ?? ''}`.trim();
  }

  protected isStepDone(index: number): boolean {
    return this.doneSteps().has(index);
  }

  /** Ticks or unticks a step; purely local, no request is made. */
  protected toggleStep(index: number): void {
    this.doneSteps.update((done) => {
      const next = new Set(done);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  protected pause(): void {
    this.applyState('PAUSED');
  }

  protected resume(): void {
    this.applyState('IN_PROGRESS');
  }

  protected abandon(): void {
    this.applyState('ABANDONED');
  }

  protected complete(): void {
    this.applyState('COMPLETED');
  }

  /**
   * Sends one state transition and reacts to its outcome.
   *
   * A completion is the only transition that can be answered with the shortage
   * plan: when it is, the plan is handed to {@link confirmShortage} instead of
   * being shown as an error, because nothing failed — the backend is asking a
   * question. Everything else, the illegal-transition 409 included, is a
   * message next to the controls.
   */
  private applyState(status: CookingSessionStatus, acceptShortage = false): void {
    const session = this.session();
    if (session === null || this.busy()) {
      return;
    }

    this.busy.set(true);
    this.transitionError.set(null);
    this.cooking.updateState(session.id, status, acceptShortage).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.session.set(updated);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if (status === 'COMPLETED' && error.status === 409) {
          const shortage = asCompletionDeduction(error.error);
          if (shortage !== null) {
            this.confirmShortage(shortage);
            return;
          }
        }
        this.transitionError.set(
          toErrorResponse(error).message || 'Unable to update this session.',
        );
      },
    });
  }

  /**
   * Asks the user to accept a shortfall, and re-sends the completion if they do.
   *
   * Only an explicit `true` re-sends: dismissing the dialog is a no and leaves
   * everything as it was, which is exactly the state the refused completion
   * left behind.
   */
  private confirmShortage(shortage: CompletionDeduction): void {
    this.dialog
      .open(ShortageDialog, {
        data: shortage,
        autoFocus: 'first-tabbable',
        restoreFocus: true,
        maxWidth: '34rem',
        width: 'calc(100% - 2rem)',
      })
      .afterClosed()
      .subscribe((accepted: boolean | undefined) => {
        if (accepted === true) {
          this.applyState('COMPLETED', true);
        }
      });
  }

  protected statusLabel = statusLabel;
  protected formatAmount = formatAmount;
  protected runsOut = runsOut;

  /** Display name of a unit code, from the catalog with its own fallback. */
  protected unitLabel = (unitCode: string): string => this.units.label(unitCode);
}
