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
import { MatOption } from '@angular/material/core';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { toErrorResponse } from '../../core/auth/auth.models';
import {
  AdjustPantryItemRequest,
  ConsumptionEntry,
  ConsumptionOrigin,
  PantryItem,
  UpdatePantryItemRequest,
} from '../../core/pantry/pantry.models';
import { PantryService } from '../../core/pantry/pantry.service';

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

/**
 * Display symbols for the codes of the seeded `units` catalog.
 *
 * The API answers with `unitCode` and nothing else, so the label lives here
 * until the unit catalog reaches the frontend (`GET /api/units` exists but is
 * not part of this feature). An unknown code renders as itself rather than
 * blank: a code we do not recognise is still true information.
 */
const UNIT_LABELS: Record<string, string> = {
  GRAM: 'g',
  ML: 'ml',
  UNIT: 'units',
  KG: 'kg',
  OZ: 'oz',
  LB: 'lb',
  L: 'l',
  CUP: 'cup',
  TBSP: 'tbsp',
  TSP: 'tsp',
  PINT: 'pint',
};

/**
 * Units offered when adjusting a line, drawn from the same seeded catalog.
 *
 * Deliberately not narrowed to what the line's own unit can be converted into:
 * whether a pair of units converts at all depends on the ingredient's density
 * (the ladder of H2-B-4), which only the server knows. Guessing here would hide
 * combinations that do work and invent ones that do not, so an impossible pair
 * is refused by the API and its message shown.
 */
const ADJUSTMENT_UNITS: ReadonlyArray<{ code: string; label: string }> = Object.entries(
  UNIT_LABELS,
).map(([code, label]) => ({ code, label }));

const ORIGIN_LABELS: Record<ConsumptionOrigin, string> = { MANUAL: 'Manual' };

/** Unit code as the user sees it, falling through to the raw code. */
const unitLabel = (unitCode: string): string => UNIT_LABELS[unitCode] ?? unitCode;

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
 * milestone allows — edit location/note, adjust the quantity with a signed
 * amount, remove the line, read its ledger — available inline on each row.
 *
 * Only one row is expanded at a time. The four actions are the whole feature
 * area of a line and they all read better next to the line they belong to, but
 * four independently collapsible panels would leave the list impossible to scan;
 * a single `openPanel` keeps one row tall and the rest compact.
 *
 * The list is the whole view, so a failed load is a page-level error with a
 * retry rather than an empty inventory that silently looks like an empty pantry.
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
  private readonly snackBar = inject(MatSnackBar);

  protected readonly adjustmentUnits = ADJUSTMENT_UNITS;

  /** The whole pantry, in the order the API answered. */
  private readonly items = signal<PantryItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  /** Category blocks of the list, derived so a write never re-sorts anything. */
  protected readonly sections = computed(() => groupByCategory(this.items()));
  protected readonly itemCount = computed(() => this.items().length);
  protected readonly categoryCount = computed(() => this.sections().length);
  protected readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.items().length === 0,
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
    this.load();
  }

  /**
   * Loads the pantry.
   *
   * The response replaces the list outright rather than being merged: the
   * server is the only authority on what exists, and a merge would keep showing
   * a line that was deleted while the page was open.
   */
  protected load(): void {
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

  /** True when the given panel of the given row is the expanded one. */
  protected isOpen(itemId: number, kind: PanelKind): boolean {
    const open = this.openPanel();
    return open?.id === itemId && open.kind === kind;
  }

  protected unitLabel = unitLabel;
  protected originLabel = originLabel;
  protected signedDelta = signedDelta;

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
