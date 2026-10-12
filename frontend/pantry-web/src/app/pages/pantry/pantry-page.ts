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
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatOption, MatOptgroup } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import {
  MatError,
  MatFormField,
  MatHint,
  MatLabel,
  MatPrefix,
  MatSuffix,
} from '@angular/material/form-field';
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
import { PantryItemRow } from './pantry-item-row';
import {
  LOW_STOCK_THRESHOLD,
  PanelKind,
  PantryViewMode,
  deltaToTarget,
  filterPantryItems,
  groupByCategory,
} from './pantry-helpers';

/** The expanded row and what it is showing. */
interface OpenPanel {
  readonly id: number;
  readonly kind: PanelKind;
}

/** localStorage key remembering the chosen list/grid layout. */
const VIEW_MODE_STORAGE_KEY = 'pantry.viewMode';

/** Rows of the loading skeleton, per fake category. */
const SKELETON_SECTIONS = [0, 1, 2] as const;
const SKELETON_ROWS = [0, 1, 2] as const;

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

/** Reads the persisted layout choice; unknown values fall back to the list. */
const loadStoredViewMode = (): PantryViewMode => {
  try {
    return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'grid' ? 'grid' : 'list';
  } catch {
    // Storage can be unavailable (private mode); the list is always a safe default.
    return 'list';
  }
};

/**
 * "My pantry": the user's inventory grouped by category, with every write the
 * milestone allows — add a food from the catalog, edit location/note, adjust the
 * quantity with a signed amount, remove the line, read its ledger — available
 * without leaving the list.
 *
 * The line itself — its summary, the quantity stepper and the action buttons
 * that open the four panels — is rendered by the presentational
 * {@link PantryItemRow}, which emits what the user did and projects the panels
 * this page still owns. Extracting it keeps the row's layout out of this file's
 * style budget and lets the row be tested on its own, while every write and
 * every piece of state stays here.
 *
 * Only one row is expanded at a time. The four row actions are the whole feature
 * area of a line and they all read better next to the line they belong to, but
 * four independently collapsible panels would leave the list impossible to scan;
 * a single `openPanel` keeps one row tall and the rest compact.
 *
 * Quantity edits happen inline on the row itself: −/+ move the stock by the
 * unit's step and clicking the amount turns it into a field that submits the
 * signed delta to `POST /api/pantry/{id}/adjustments` (never a PUT: changing
 * stock is a ledgered movement by contract). The server's response replaces the
 * row in place — the amount can come back normalized to the line's unit — and a
 * 409 (would go below zero) or a network failure restores the previous value
 * and reports the backend's own message. A row's buttons and field are disabled
 * while its request is in flight, and the rest of the page honours the shared
 * `busy` so two writers cannot race the same stock.
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
 * The search box and the category/low-stock filters run in the client over the
 * list this page already fetched. The pantry is household-sized, every filter is
 * a predicate over fields the rows already carry, and the server's order
 * survives filtering, so a request per keystroke would answer no question the
 * browser cannot. Category sections collapse in place (the state lives here, not
 * in the URL), and the list/grid toggle persists to localStorage because there
 * is no backend preference for a pure presentation choice.
 *
 * A 404 means the line is gone (or never was this user's) and there is nothing
 * local to correct: every write that gets one closes its panel and reloads the
 * list, because the server's version is the only one that can still be true. A
 * 409 on an adjustment, by contrast, is about the amount the user typed — the
 * message arrives with the three figures it computed, so it is shown as-is
 * inside the panel and the typed amount survives for the retry; inline edits
 * keep no draft (the row already shows the previous value), so the message goes
 * to the snackbar.
 */
@Component({
  selector: 'app-pantry-page',
  imports: [
    DatePipe,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardSubtitle,
    MatCardTitle,
    MatCheckbox,
    MatError,
    MatFormField,
    MatHint,
    MatIcon,
    MatInput,
    MatLabel,
    MatOption,
    MatOptgroup,
    MatPrefix,
    MatProgressSpinner,
    MatSelect,
    MatSuffix,
    PantryItemRow,
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

  protected readonly skeletonSections = SKELETON_SECTIONS;
  protected readonly skeletonRows = SKELETON_ROWS;
  protected readonly lowStockThreshold = LOW_STOCK_THRESHOLD;

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

  /** The layout the rows are rendered in; defaults to the list. */
  protected readonly viewMode = signal<PantryViewMode>(loadStoredViewMode());

  /** Search text, matched against the line names of the already-fetched list. */
  protected readonly query = signal('');
  /** Category the toolbar keeps, or `null` for every category. */
  protected readonly categoryFilter = signal<string | null>(null);
  /** True to keep only zeroed or low lines (see {@link LOW_STOCK_THRESHOLD}). */
  protected readonly lowStockOnly = signal(false);
  /** Categories of the pantry, in the server's order, for the toolbar select. */
  protected readonly categories = computed(() => {
    const seen: string[] = [];
    for (const item of this.items()) {
      if (!seen.includes(item.category)) {
        seen.push(item.category);
      }
    }
    return seen;
  });

  /**
   * Category blocks of the list after the toolbar filters, derived so a write
   * or a filter never re-sorts anything.
   */
  protected readonly sections = computed(() => groupByCategory(this.filteredItems()));

  /** The list the toolbar produced from the whole pantry. */
  private readonly filteredItems = computed(() =>
    filterPantryItems(this.items(), {
      query: this.query(),
      category: this.categoryFilter(),
      lowStockOnly: this.lowStockOnly(),
    }),
  );

  protected readonly isEmpty = computed(
    () => !this.loading() && this.loadError() === null && this.items().length === 0,
  );

  /**
   * No line matched the toolbar, although the pantry itself is not empty.
   *
   * Deliberately a state of its own and not the "empty pantry" card: "nothing
   * in the house" and "nothing matches what I asked for" call for different
   * next actions (add the first item vs. clear the filters), and confusing them
   * would send a user hunting in the wrong place.
   */
  protected readonly noResults = computed(
    () =>
      !this.loading() &&
      this.loadError() === null &&
      this.items().length > 0 &&
      this.filteredItems().length === 0,
  );

  /** True when any filter of the toolbar is narrowing the list. */
  protected readonly hasActiveFilters = computed(
    () => this.query().trim() !== '' || this.categoryFilter() !== null || this.lowStockOnly(),
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
   * The page's own skeletons already say the view is loading, so a second
   * spinner there would only compete with them, and a list that failed to load
   * takes the whole body with it.
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
  /** Row whose inline quantity request is in flight. */
  protected readonly inlineSavingId = signal<number | null>(null);
  /** Row currently showing its editable quantity field; at most one. */
  protected readonly editingQuantityId = signal<number | null>(null);
  /** Draft text of the inline quantity field. */
  protected readonly quantityDraft = signal('');

  /** Categories the user has collapsed; a section is hidden when its name is here. */
  protected readonly collapsedCategories = signal<ReadonlySet<string>>(new Set());

  /**
   * True while any write is in flight.
   *
   * Every action button honours it: the quantity is a shared fact and two
   * concurrent writes from one screen would produce a ledger whose order the
   * user never chose. The inline quantity request joins the panel saves and the
   * delete, so opening a panel mid-save is as impossible as a second save.
   */
  protected readonly busy = computed(
    () =>
      this.savingItemId() !== null ||
      this.deletingItemId() !== null ||
      this.inlineSavingId() !== null,
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

  /**
   * Opens the panel the row asked for.
   *
   * The row knows which button was used; the page knows the state and the form
   * behind each panel, so the mapping stays here rather than in the child.
   */
  protected onOpenPanel(item: PantryItem, kind: PanelKind): void {
    switch (kind) {
      case 'edit':
        this.openEdit(item);
        return;
      case 'adjust':
        this.openAdjust(item);
        return;
      case 'history':
        this.openHistory(item);
        return;
    }
  }

  protected unitLabel = (unitCode: string): string => this.units.label(unitCode);
  protected originLabel = originLabel;
  protected signedDelta = signedDelta;

  /** Switches the row layout and remembers it for the next visit. */
  protected setViewMode(mode: PantryViewMode): void {
    this.viewMode.set(mode === 'grid' ? 'grid' : 'list');
    try {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, this.viewMode());
    } catch {
      // The switch already took effect on screen; persistence is best-effort.
    }
  }

  /** Updates the search text from the toolbar input. */
  protected onQueryInput(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected clearQuery(): void {
    this.query.set('');
  }

  protected setCategoryFilter(value: string | null): void {
    this.categoryFilter.set(value === '' ? null : value);
  }

  /** Drops every filter; the search box and the select reset with them. */
  protected clearFilters(): void {
    this.query.set('');
    this.categoryFilter.set(null);
    this.lowStockOnly.set(false);
  }

  /** True when the given category header is collapsed. */
  protected isCollapsed = (category: string): boolean => this.collapsedCategories().has(category);

  /**
   * Collapses or expands a category section in place.
   *
   * The state is a set of collapsed names held by the component: sections are a
   * derived view, so remembering the names (not the indices) keeps the state
   * valid when filters change which sections exist. Collapsing a section that is
   * mid-editing drops the inline field first, so a requirement can never hide
   * inside a closed block.
   */
  protected toggleCategory(category: string): void {
    const collapsed = this.collapsedCategories();
    if (collapsed.has(category)) {
      const next = new Set(collapsed);
      next.delete(category);
      this.collapsedCategories.set(next);
      return;
    }
    const section = this.sections().find((candidate) => candidate.category === category);
    if (section?.items.some((item) => item.id === this.editingQuantityId())) {
      this.cancelQuantityEdit();
    }
    const next = new Set(collapsed);
    next.add(category);
    this.collapsedCategories.set(next);
  }

  /** Id of the collapse region, paired with its header's `aria-controls`. */
  protected categoryRegionId = (index: number): string => `pantry-category-${index}`;

  /** True while a write is in flight on the given row. */
  protected rowBusy(itemId: number): boolean {
    return this.busy() || this.inlineSavingId() === itemId;
  }

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
    this.cancelQuantityEdit();
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

  /**
   * Turns the row's quantity into an editable field.
   *
   * The draft starts from the stored amount, so pressing Enter with no change
   * costs no request (the delta is zero and nothing is sent). Opening a new
   * field silently closes an old one: at most one row edits, matching the
   * panel rule, so the draft signal can be a single value.
   */
  protected startQuantityEdit(item: PantryItem): void {
    if (this.rowBusy(item.id)) {
      return;
    }
    this.editingQuantityId.set(item.id);
    this.quantityDraft.set(String(item.quantity));
  }

  /** Keeps the draft in sync with what the user types. */
  protected onQuantityInput(value: string): void {
    this.quantityDraft.set(value);
  }

  /**
   * Commits the typed amount, closing the field.
   *
   * The typed value is a target, not a delta: the request posts
   * `target - current` in the line's unit, so the row reaches exactly what the
   * user wrote. A row that cannot be parsed (empty, `NaN`) is treated as a
   * cancel, a negative target is refused locally (the schema forbids it), and a
   * target equal to the stored amount is a no-op so "press Enter" never writes a
   * meaningless ledger entry. Errors roll the row back to its previous value:
   * the row only ever changes from the server's own response.
   */
  protected commitQuantityEdit(item: PantryItem): void {
    if (this.editingQuantityId() !== item.id || this.busy()) {
      return;
    }
    const raw = this.quantityDraft().trim();
    const target = Number(raw);
    this.cancelQuantityEdit();
    if (!Number.isFinite(target)) {
      return;
    }
    if (target < 0) {
      this.notify('A quantity cannot be negative.', true);
      return;
    }
    const delta = deltaToTarget(item.quantity, target);
    if (delta === 0) {
      return;
    }
    this.postAdjustment(item, delta);
  }

  /**
   * Reverts the inline field. Wired to Escape and blur, so leaving the field
   * without confirming abandons the draft instead of surprising the user with a
   * save they never asked for.
   */
  protected cancelQuantityEdit(): void {
    this.editingQuantityId.set(null);
    this.quantityDraft.set('');
  }

  /**
   * Applies a step the row already scaled to the line's unit.
   *
   * The row needs the unit's step to write its button labels, so it computes the
   * signed delta there; the page only carries it to the ledger through the same
   * path a typed adjustment uses.
   */
  protected onStep(item: PantryItem, delta: number): void {
    this.stepAdjustment(item, delta);
  }

  /**
   * Steps the quantity by a fixed signed delta.
   *
   * The advance of a single click is the unit's step; the request is the same
   * adjustment endpoint the typed edit uses. The minus button is disabled at
   * zero (there is nothing to take away), so the 409 path appears when an edit
   * goes below zero — not on a button that can only fail.
   */
  private stepAdjustment(item: PantryItem, delta: number): void {
    if (this.rowBusy(item.id)) {
      return;
    }
    this.cancelQuantityEdit();
    this.postAdjustment(item, delta);
  }

  /**
   * Posts a signed delta to the ledger and swaps the row on success.
   *
   * The row keeps whatever value it had until the server answers: an optimistic
   * update would have to be undone on a rejection, and the response is the only
   * authority on what the amount became (it can come back normalized). On success
   * the row is replaced in place, the header totals are re-read (a movement can
   * change the zero count), and a confirmation snackbar confirms the write. On a
   * failure the previous value simply stays: nothing local touched it. A 404
   * goes through the stale-line path (reload the list); every other status is the
   * backend's own wording, which is the only text that knows the numbers.
   */
  private postAdjustment(item: PantryItem, delta: number): void {
    this.inlineSavingId.set(item.id);
    this.pantry.adjustQuantity(item.id, { amount: delta, unitCode: item.unitCode }).subscribe({
      next: (updated) => {
        this.inlineSavingId.set(null);
        this.replaceItem(updated);
        this.loadSummary();
        this.notify('Quantity updated');
      },
      error: (error: HttpErrorResponse) => {
        this.inlineSavingId.set(null);
        if (error.status === 404) {
          this.reportStaleLine(error);
          return;
        }
        this.notify(toErrorResponse(error).message || 'Unable to adjust the quantity.', true);
      },
    });
  }

  protected askDelete(item: PantryItem): void {
    this.panelError.set(null);
    this.cancelQuantityEdit();
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
   * from should not be one click away from a destructive request. An inline
   * quantity field is closed as well: two editors on one row would fight over
   * the same authority.
   */
  private togglePanel(item: PantryItem, kind: PanelKind): void {
    if (this.isOpen(item.id, kind)) {
      this.closePanelOf(item.id);
      return;
    }
    this.confirmingDeleteId.set(null);
    this.panelError.set(null);
    this.cancelQuantityEdit();
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
    this.cancelQuantityEdit();
    this.notify(toErrorResponse(error).message || 'That item is no longer in your pantry.', true);
    this.load();
  }

  /** Transient feedback for the outcomes that have no field to hold a message. */
  private notify(message: string, isError = false): void {
    this.snackBar.open(message, 'OK', { duration: isError ? 8000 : 3000 });
  }
}
