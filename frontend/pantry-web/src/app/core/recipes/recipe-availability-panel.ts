import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { UnitService } from '../catalog/unit.service';
import { RecipeAvailability, formatAmount } from './recipe-matching';

/**
 * Presentational panel that summarises a recipe's pantry availability.
 *
 * It renders the classification produced by {@link RecipeAvailability} and
 * nothing else: the same component is dropped next to the detail page's "Start
 * cooking" button, inside the confirmation dialog that guards that button and
 * as the persistent banner of a cooking session. Keeping the labels in one
 * place is the point — the three screens would otherwise drift in wording for
 * the same three facts (low / missing / uncatalogued).
 *
 * The panel only reads its input; it never loads or writes anything, so a page
 * computes the availability once (from the already-loaded detail) and passes it
 * down. Unit codes become text through {@link UnitService}, never a local table.
 */
@Component({
  selector: 'app-recipe-availability-panel',
  imports: [MatIcon],
  templateUrl: './recipe-availability-panel.html',
  styleUrl: './recipe-availability-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipeAvailabilityPanel {
  private readonly units = inject(UnitService);

  /** The classification to render, always computed from a loaded detail. */
  readonly availability = input.required<RecipeAvailability>();

  /** A quantity with its catalog unit label, e.g. `150 Gram`. */
  protected amount(value: number, unitCode: string): string {
    return `${formatAmount(value)} ${this.units.label(unitCode)}`;
  }
}
