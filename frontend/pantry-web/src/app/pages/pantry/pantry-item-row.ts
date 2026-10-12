import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { PantryItem } from '../../core/pantry/pantry.models';
import { PanelKind, PantryViewMode, stepForUnit } from './pantry-helpers';

/**
 * One pantry line: its summary, the inline quantity stepper and the four
 * action buttons that open the line's panels.
 *
 * It exists so the row's own layout and controls can be reasoned about without
 * the page's toolbar, category sections and panel markup around them — a
 * presentational child that renders what it is handed and emits what the user
 * did, while `PantryPage` keeps every write and every piece of state. It never
 * talks to `PantryService`.
 *
 * The component host *is* the row element (`.pantry-item`): the pantry's grid
 * container lays out its children, so if the row wrapped an inner element the
 * grid would space out wrappers and the row borders would land on the wrong
 * box. `grid-card` and `pantry-item-open` are host classes for the same reason
 * — the visual state belongs to the element the container positions.
 *
 * The page projects the edited/adjusted/history/delete panels through the
 * `<ng-content>` slot below the row, so a panel stays the page's markup and
 * keeps the page's styles while the row that anchors it stays here.
 */
@Component({
  selector: 'app-pantry-item-row',
  imports: [MatIconButton, MatFormField, MatIcon, MatInput, MatSuffix],
  templateUrl: './pantry-item-row.html',
  styleUrl: './pantry-item-row.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pantry-item',
    '[class.grid-card]': "viewMode() === 'grid'",
    '[class.pantry-item-open]': 'open()',
  },
})
export class PantryItemRow {
  /** The line to render; the page owns every write to it. */
  readonly item = input.required<PantryItem>();

  /**
   * The line's unit code already resolved to a display label.
   *
   * Passed in rather than looked up here so the row has no dependency on the
   * unit catalog and the page keeps one way of naming a unit for the whole view.
   */
  readonly unitLabel = input.required<string>();

  /** `grid` reflows the row into a card; `list` keeps it a single line. */
  readonly viewMode = input.required<PantryViewMode>();

  /** True while a write is in flight anywhere on the page (see `PantryPage.busy`). */
  readonly busy = input.required<boolean>();

  /** True when this row has a panel expanded, for the tinted surface. */
  readonly open = input.required<boolean>();

  /** True while this row's quantity is an editable field. */
  readonly editing = input.required<boolean>();

  /** Draft text of the inline field, owned by the page. */
  readonly draft = input.required<string>();

  /**
   * A signed step requested with the −/+ buttons, already scaled to the line's
   * unit. The page turns it into an adjustment; the row never posts anything.
   */
  readonly step = output<number>();

  /** The read-only amount was clicked, asking for the editable field. */
  readonly startEdit = output<void>();

  /** The draft field changed, carrying its new text. */
  readonly draftChange = output<string>();

  /** Enter was pressed in the draft field. */
  readonly commitEdit = output<void>();

  /** Escape or blur abandoned the draft field. */
  readonly cancelEdit = output<void>();

  /** One of the row actions was used; the page decides which panel that opens. */
  readonly panel = output<PanelKind>();

  /** The remove action was used, asking for the confirmation the page owns. */
  readonly remove = output<void>();

  /** Exposed to the template so the stepper and its labels share the step rule. */
  protected stepForUnit = stepForUnit;
}
