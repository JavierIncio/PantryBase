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
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatOption, MatOptgroup } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { toErrorResponse } from '../../core/auth/auth.models';
import { UnitGroup, buildUnitGroups } from '../../core/catalog/unit-groups';
import { UnitService } from '../../core/catalog/unit.service';
import {
  AdjustPantryItemRequest,
  ConsumptionEntry,
  ConsumptionOrigin,
  PantryItem,
  PantrySummary,
  UpdatePantryItemRequest,
} from '../../core/pantry/pantry.models';
import { PantryService } from '../../core/pantry/pantry.service';
import { AddPantryItemDialog, AddPantryItemResult } from './add-pantry-item-dialog';

/** Which of a row's inline editors is expanded; at most one, never two. */
type PanelKind = 'edit' | 'adjust' | 'history';

/** The expanded row and what it is showing. */
interface OpenPanel {
  readonly id: number;
  readonly kind: PanelKind;
}

/** One category block of the list, in the order the API returned it. */
interface PantrySection {
  readonly category: string;
  readonly items: PantryItem[];
}

const ORIGIN_LABELS: Record<ConsumptionOrigin, string> = {
  MANUAL: 'Manual',
  COOKING_SESSION: 'Cooking session',
};

/** Origin of a movement as the user sees it, falling through to the raw value. */
const originLabel = (origin: ConsumptionOrigin): string => ORIGIN_LABELS[origin] ?? origin;

/**
 * Renders a ledger delta with its direction always visible.
 *
 * A positive movement would otherwise read as a bare number and be skimmed past
 * as if it were the new quantity; the explicit `+` makes both directions legible
 * without relying on colour alone.
 */
const signedDelta = (delta: number): string => (delta > 0 ? `+${delta}` : String(delta));

/**
 * Rejects a zero amount: it is not a movement, and the API answers 400 for it.
 *
 * A validator rather than a check in the submit handler, so the error lives on
 * the control, the normal re-validation flow clears it as soon as the user
 * types, and the request never leaves the browser.
 */
const nonZeroAmount = (control: AbstractControl): ValidationErrors | null =>
  control.value === 0 ? { zero: true } : null;

/**
 * Groups a flat list into consecutive category blocks.
 *
 * A single pass and no sorting: the API already answers by category and then by
 * name, so sorting here could only disagree with the server's collation (and
 * cost a second copy of the list on every write).
 */
function groupByCategory(items: readonly PantryItem[]): PantrySection[] {
  const sections: PantrySection[] = [];
  for (const item of items) {
    const last = sections[sections.length - 1];
    if (last && last.category === item.category) {
      last.items.push(item);
    } else {
      sections.push({ category: item.category, items: [item] });
    }
  }
  return sections;
}

/**
 * "My pantry": the user's inventory grouped by category, with every write the
 * milestone allows — add a food from the catalog, edit location/note, adjust the
 * quantity with a signed amount, remove the line, read its ledger — available
 * without leaving the list.
 *
 * Only one row is expanded at a time. The four row actions are the whole feature
 * area of a line and they all read better next to the line they belong to, but
 * four independently collapsible panels would leave the list impossible to scan;
 * a single `openPanel` keeps one row tall and the rest compact.
 *
 * Unit codes are labelled from the unit catalog (`GET /api/units`), never from a
 * local table: the codes travel in every payload and their names are the API's
 * to say, so a second copy of them here could only ever disagree with it.
 *
 * The list is the whole view, so a failed load is a page-level error with a
 * retry rather than an empty inventory that silently looks like an empty pantry.
 *
 * The counts in the header are the server's (`GET /api/pantry/summary`) and this
 * view prints them instead of counting the lines again: one inventory counted
 * twice is free to disagree with itself. A failed aggregate is a small line with
 * a retry, never the page's error — a user who came to see what is stored must
 * not lose it over a header — and the figures already on screen survive a re-read
 * that fails. No breakdown is drawn here: the list groups the same lines by
 * category a few centimetres below, so a chart of them would be a copy.
 *
 * A 404 means the line is gone (or never was this user's) and there is nothing
 * local to correct: every write that gets one closes its panel and reloads the
 * list, because the server's version is the only one that can still be true. A
 * 409 on an adjustment, by contrast, is about the amount the user typed — the
 * message arrives with the three figures it computed, so it is shown as-is
 * inside the panel and the typed amount survives for the retry.
 */
@Component({
  selector: 'app-pantry-page',
  imports: [
    DatePipe,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatError,
    MatFormField,
    MatHint,
    MatIcon,
    MatInput,
    MatLabel,
    MatOption,
    MatOptgroup,
    MatProgressSpinner,
    MatSelect,
    ReactiveFormsModule,
  ],
  templateUrl: './pantry-page.html',
  styleUrl: './pantry-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PantryPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly pantry = inject(PantryService);
  private readonly units = inject(UnitService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** The whole pantry, in the order the API answered. */
  private readonly items = signal<PantryItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  /**
   * Totals of the inventory as the server counted them, `null` until the first
   * answer arrives.
   *
   * Deliberately not derived from {@link items}: the header would then be a
   * second count of lines the server already counted, and two counts of one fact
   * are free to disagree. The list keeps its own length for what only the list
   * needs (an empty state); the header prints what the API says.
   *
   * Held across reloads instead of cleared: the figures on screen were true when
   * they arrived, so a re-read that is still in flight has nothing to correct.
   */
  protected readonly summary = signal<PantrySummary | null>(null);
  protected readonly summaryLoading = signal(false);
  protected readonly summaryError = signal<string | null>(null);

  /** Category blocks of the list, derived so a write never re-sorts anything. */
  protected readonly sections = computed(() => groupByCategory(this.items()));
  protected readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.items().length === 0,
  );

  /**
   * The totals worth printing in the header.
   *
   * Null for an empty pantry because the page then shows a card explaining that
   * it is empty: a header line reading "0 items" above it would say the same
   * thing twice and less clearly.
   */
  protected readonly totals = computed(() => {
    const summary = this.summary();
    return summary !== null && summary.totalItems > 0 ? summary : null;
  });

  /**
   * True while the totals are still unknown and nothing else takes their place.
   *
   * The page's own spinner already says the view is loading, so a second one in
   * the header would only compete with it, and a list that failed to load takes
   * the whole body with it.
   */
  protected readonly summaryPending = computed(
    () =>
      this.summary() === null &&
      this.summaryLoading() &&
      !this.loading() &&
      this.loadError() === null,
  );

  /**
   * Units offered when adjusting a line, grouped by category.
   *
   * The whole catalog, and not narrowed to what the line's own unit can be
   * converted into: whether a pair of units converts at all depends on the
   * ingredient's density (the ladder of H2-B-4), which only the server knows.
   * Guessing here would hide combinations that do work and invent ones that do
   * not, so an impossible pair is refused by the API and its message shown.
   */
  protected readonly adjustmentGroups = computed<UnitGroup[]>(() =>
    buildUnitGroups(this.units.units() ?? []),
  );

  /** The one expanded panel, or null when every row is collapsed. */
  protected readonly openPanel = signal<OpenPanel | null>(null);
  /** Row waiting for a delete confirmation. */
  protected readonly confirmingDeleteId = signal<number | null>(null);
  /** Message from a rejected write, rendered inside the open panel. */
  protected readonly panelError = signal<string | null>(null);

  protected readonly savingItemId = signal<number | null>(null);
  protected readonly deletingItemId = signal<number | null>(null);
  /**
   * True while any write is in flight.
   *
   * Every action button honours it: the quantity is a shared fact and two
   * concurrent writes from one screen would produce a ledger whose order the
   * user never chose.
   */
  protected readonly busy = computed(
    () => this.savingItemId() !== null || this.deletingItemId() !== null,
  );

  protected readonly entries = signal<ConsumptionEntry[] | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);

  /**
   * Inline editor of location and note.
   *
   * One form for the whole list instead of one per row: only a single panel is
   * ever open, so there is nothing to keep apart, and `reset` guarantees a panel
   * never shows a previous line's text or a stale untouched field.
   */
  readonly editForm = this.fb.group({
    location: ['', Validators.maxLength(100)],
    note: ['', Validators.maxLength(255)],
  });

  /**
   * Inline form of a quantity adjustment.
   *
   * `amount` is a nullable number control because the common mistake is not a
   * wrong number but an empty one, and `unitCode` starts on the line's own unit:
   * that is the answer for most adjustments and the backend converts whatever
   * else is sent.
   */
  readonly adjustForm = this.fb.group({
    amount: this.fb.control<number | null>(null, [Validators.required, nonZeroAmount]),
    unitCode: ['', Validators.required],
  });

  ngOnInit(): void {
    // The unit labels of the rows and the units offered to adjust them both come
    // from the catalog, so it is loaded with the pantry rather than on demand.
    this.units.load();
    this.load();
  }

  /**
   * Loads the pantry and the totals of its header.
   *
   * The two requests are issued together and settled separately. Neither answer is
   * needed to ask for the other, so there is nothing to sequence; and an aggregate
   * that fails must not take the list down with it, which is exactly what folding
   * them into one `forkJoin` would do — so they are two subscriptions with two
   * sets of state instead.
   *
   * The list response replaces the local lines outright rather than being merged:
   * the server is the only authority on what exists, and a merge would keep
   * showing a line that was deleted while the page was open.
   */
  protected load(): void {
    this.loadSummary();
    this.loading.set(true);
    this.loadError.set(null);
    this.pantry.listItems().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.loadError.set(toErrorResponse(error).message || 'Unable to load your pantry.');
      },
    });
  }

  /**
   * Reads the totals printed in the header.
   *
   * The figures already on screen stay there while the request travels and after a
   * failure: they were the server's answer when they arrived, and a number that
   * disappears is worse news than one that is briefly behind. Only the first
   * attempt has nothing to fall back on, which is when the pending and error
   * states are the ones on screen.
   *
   * Called on every reload of the view, because the totals are part of it, and not
   * only on entry: a header that keeps saying "34 items" under a list of 35 is
   * lying about the only thing it exists to say.
   */
  protected loadSummary(): void {
    this.summaryLoading.set(true);
    this.summaryError.set(null);
    this.pantry.getSummary().subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.summaryLoading.set(false);
        this.summaryError.set(toErrorResponse(error).message || 'Unable to count your pantry.');
      },
    });
  }

  /** True when the given panel of the given row is the expanded one. */
  protected isOpen(itemId: number, kind: PanelKind): boolean {
    const open = this.openPanel();
    return open?.id === itemId && open.kind === kind;
  }

  protected unitLabel = (unitCode: string): string => this.units.label(unitCode);
  protected originLabel = originLabel;
  protected signedDelta = signedDelta;

  /**
   * Opens the quick-add dialog over the pantry.
   *
   * A dialog and not a route because the point of the feature is adding a line
   * without losing sight of the pantry it goes into: a route would replace the
   * list, and the only way back to it would be to reload it. The dialog is the
   * flow, this page stays on screen behind it.
   *
   * Nothing is inserted into the list here. The dialog reports what it created and
   * the list is asked for again, because its order and its grouping are the
   * server's and finding the alphabetical position of the new line in the client
   * is exactly the duplication this page avoids everywhere else.
   */
  protected openAddItem(): void {
    this.dialog
      .open(AddPantryItemDialog, {
        autoFocus: 'first-tabbable',
        restoreFocus: true,
        maxWidth: '34rem',
        width: 'calc(100% - 2rem)',
      })
      .afterClosed()
      .subscribe((result: AddPantryItemResult | undefined) => {
        if (!result) {
          return;
        }
        if (result.kind === 'created') {
          this.load();
          // What the user typed is not what was stored: the backend normalizes the
          // amount to the unit the line keeps, and the row is about to show that
          // figure, so this notice must be the same one.
          const { name, quantity, unitCode } = result.item;
          this.notify(`${name} added as ${quantity} ${this.units.label(unitCode)}`);
          return;
        }
        this.openExistingLine(result.fdcId, result.message);
      });
  }

  /**
   * Takes the user to the line an add was refused because it already exists.
   *
   * The conflict message names the food by its `fdcId`, not by the line's id, and
   * the pantry on screen already holds every line, so the line is found there and
   * its adjustment panel is opened: the backend's advice is to adjust the
   * existing quantity instead of adding a second line, and that is exactly the
   * panel that does it.
   *
   * A pantry that does not hold that food has nothing to jump to — the list was
   * read before the line was added somewhere else — so the backend's message is
   * shown instead and the list asked for again.
   */
  private openExistingLine(fdcId: number, message: string): void {
    const item = this.items().find((candidate) => candidate.fdcId === fdcId);
    if (!item) {
      this.notify(message || 'That ingredient is already in your pantry.', true);
      this.load();
      return;
    }

    this.confirmingDeleteId.set(null);
    this.panelError.set(null);
    this.openPanel.set({ id: item.id, kind: 'adjust' });
    this.adjustForm.reset({ amount: null, unitCode: item.unitCode });
    this.notify(`${item.name} is already stored: add to its quantity instead.`);
  }

  /** Opens the location/note editor, or closes it when it is already open. */
  protected openEdit(item: PantryItem): void {
    this.togglePanel(item, 'edit');
    this.editForm.reset({
      location: item.location ?? '',
      note: item.note ?? '',
    });
  }

  /** Opens the adjustment form, defaulting to the unit the line keeps. */
  protected openAdjust(item: PantryItem): void {
    this.togglePanel(item, 'adjust');
    this.adjustForm.reset({ amount: null, unitCode: item.unitCode });
  }

  /**
   * Opens the ledger of a line, fetching it on first open.
   *
   * Lazy on purpose: a pantry of thirty lines would otherwise issue thirty
   * requests for a panel nobody opened. Re-opening refetches, because the answer
   * changes whenever any other writer moves the same stock.
   */
  protected openHistory(item: PantryItem): void {
    this.togglePanel(item, 'history');
    this.entries.set(null);
    this.historyError.set(null);
    this.historyLoading.set(true);
    this.pantry.listEntries(item.id).subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.historyLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.historyLoading.set(false);
        if (error.status === 404) {
          this.reportStaleLine(error);
          return;
        }
        this.historyError.set(toErrorResponse(error).message || 'Unable to load the history.');
      },
    });
  }

  protected closePanel(item: PantryItem): void {
    this.closePanelOf(item.id);
  }

  /**
   * Saves the location and note.
   *
   * Both fields always travel, and an emptied input becomes `null`: the endpoint
   * replaces the two editable attributes, so omitting one would clear it anyway
   * and encoding the empty string as null says the same thing explicitly.
   */
  protected saveDetails(item: PantryItem): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    const { location, note } = this.editForm.getRawValue();
    const request: UpdatePantryItemRequest = {
      location: location === '' ? null : location,
      note: note === '' ? null : note,
    };

    this.savingItemId.set(item.id);
    this.panelError.set(null);
    this.pantry.updateItem(item.id, request).subscribe({
      next: (updated) => {
        this.savingItemId.set(null);
        this.replaceItem(updated);
        this.closePanelOf(item.id);
        // Nothing else is asked for: neither the location nor the note is counted
        // anywhere, so this write cannot move a published figure.
        this.notify('Item updated');
      },
      error: (error: HttpErrorResponse) => this.failWrite(error, 'Unable to update this item.'),
    });
  }

  /**
   * Sends a signed movement of stock.
   *
   * The sign the user typed is the whole meaning of the field — positive adds,
   * negative consumes — so it is carried verbatim with no direction toggle to
   * contradict it, and the unit travels as typed for the server to convert.
   * A rejected request leaves the amount in the field: the backend message says
   * how much is left and how much was asked for, so the fix is a smaller number.
   */
  protected applyAdjustment(item: PantryItem): void {
    const { amount, unitCode } = this.adjustForm.getRawValue();
    if (this.adjustForm.invalid || amount === null) {
      this.adjustForm.markAllAsTouched();
      return;
    }

    const request: AdjustPantryItemRequest = { amount, unitCode };

    this.savingItemId.set(item.id);
    this.panelError.set(null);
    this.pantry.adjustQuantity(item.id, request).subscribe({
      next: (updated) => {
        this.savingItemId.set(null);
        this.replaceItem(updated);
        this.closePanelOf(item.id);
        // A signed movement can push a line to zero or lift it from there, and that
        // count is published in the header, so it is asked for again.
        this.loadSummary();
        this.notify('Quantity updated');
      },
      error: (error: HttpErrorResponse) => this.failWrite(error, 'Unable to adjust the quantity.'),
    });
  }

  protected askDelete(item: PantryItem): void {
    this.panelError.set(null);
    this.confirmingDeleteId.set(item.id);
  }

  protected cancelDelete(item: PantryItem): void {
    if (this.confirmingDeleteId() === item.id) {
      this.confirmingDeleteId.set(null);
    }
  }

  /**
   * Removes the line once the inline confirmation is answered.
   *
   * The confirmation is not a formality: the delete takes the ledger entries
   * with it and there is no endpoint that could bring them back, so the cost of
   * a stray click is permanent. Nothing is requested before it is confirmed.
   */
  protected confirmDelete(item: PantryItem): void {
    this.deletingItemId.set(item.id);
    this.pantry.deleteItem(item.id).subscribe({
      next: () => {
        this.deletingItemId.set(null);
        this.confirmingDeleteId.set(null);
        this.closePanelOf(item.id);
        this.items.update((items) => items.filter((candidate) => candidate.id !== item.id));
        // A removed line is one line fewer in the header's count, and the categories
        // it was the only member of are gone from the server's breakdown too.
        this.loadSummary();
        this.notify('Item removed');
      },
      error: (error: HttpErrorResponse) => {
        this.deletingItemId.set(null);
        if (error.status === 404) {
          this.reportStaleLine(error);
          return;
        }
        this.notify(toErrorResponse(error).message || 'Unable to remove this item.', true);
      },
    });
  }

  /**
   * Expands a panel, or collapses it when the user asked for the open one.
   *
   * Opening one collapses the other and drops a pending delete confirmation:
   * a row never shows two editors, and a confirmation the user wandered away
   * from should not be one click away from a destructive request.
   */
  private togglePanel(item: PantryItem, kind: PanelKind): void {
    if (this.isOpen(item.id, kind)) {
      this.closePanelOf(item.id);
      return;
    }
    this.confirmingDeleteId.set(null);
    this.panelError.set(null);
    this.openPanel.set({ id: item.id, kind });
  }

  /** Collapses the given row's panel, and forgets any message it carried. */
  private closePanelOf(itemId: number): void {
    if (this.openPanel()?.id === itemId) {
      this.openPanel.set(null);
      this.panelError.set(null);
    }
  }

  /**
   * Swaps one line for the server's version of it.
   *
   * In place instead of refetching: an update and an adjustment cannot change
   * the name or the category, so the grouping the server ordered is still valid
   * and there is no fact to learn that the response does not already carry.
   */
  private replaceItem(updated: PantryItem): void {
    this.items.update((items) => items.map((item) => (item.id === updated.id ? updated : item)));
  }

  /**
   * Handles a failed write that happened inside a panel.
   *
   * A 404 is not a message for a form: the line is gone, so the panel is dropped
   * and the list reloaded. Anything else — a 400 from a too long field, the 409
   * of an adjustment that would leave the shelf short, a 401 the interceptor
   * could not recover — stays in the panel with the backend's own wording, which
   * is the only text that knows the numbers involved.
   */
  private failWrite(error: HttpErrorResponse, fallback: string): void {
    this.savingItemId.set(null);
    if (error.status === 404) {
      this.reportStaleLine(error);
      return;
    }
    this.panelError.set(toErrorResponse(error).message || fallback);
  }

  /**
   * Reacts to a line the server no longer has for this user.
   *
   * The 404 is deliberately indistinguishable from "belongs to somebody else", so
   * the page cannot offer a repair; it says what happened and asks the server
   * again, because anything still on screen was loaded before the line moved on.
   */
  private reportStaleLine(error: HttpErrorResponse): void {
    this.openPanel.set(null);
    this.confirmingDeleteId.set(null);
    this.panelError.set(null);
    this.notify(toErrorResponse(error).message || 'That item is no longer in your pantry.', true);
    this.load();
  }

  /** Transient feedback for the outcomes that have no field to hold a message. */
  private notify(message: string, isError = false): void {
    this.snackBar.open(message, 'OK', { duration: isError ? 8000 : 3000 });
  }
}
