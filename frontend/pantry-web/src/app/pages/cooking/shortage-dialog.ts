import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { UnitService } from '../../core/catalog/unit.service';
import { CompletionDeduction } from '../../core/cooking/cooking.models';
import { formatAmount } from '../../core/recipes/recipe-matching';

/**
 * Confirms a completion the pantry cannot fully cover.
 *
 * The backend refuses to change anything when a completion would leave a line
 * short, and answers 409 with the plan instead (see
 * {@link CompletionDeduction}). That refusal is not an error: it is a question
 * the user has to answer, and this dialog is the answer's two halves — cancel,
 * which changes nothing, or "Cook anyway", which re-sends the completion with
 * `acceptShortage` and lets the backend deduct what is actually there.
 *
 * Every line shown is a line that would be short, so the figures say exactly
 * what the confirmation costs: how much the recipe needs, how much is left, how
 * much of that would be deducted, and how much would remain. The part the
 * pantry cannot cover is assumed brought from outside and is deliberately not
 * recorded as a movement — the ledger only knows what was really stored.
 */
@Component({
  selector: 'app-shortage-dialog',
  imports: [MatButton, MatDialogActions, MatDialogContent, MatDialogTitle, MatIcon],
  templateUrl: './shortage-dialog.html',
  styleUrl: './shortage-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShortageDialog implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<ShortageDialog, boolean>);
  private readonly units = inject(UnitService);

  /** The plan the completion was refused with; every line here is short. */
  protected readonly deduction = inject<CompletionDeduction>(MAT_DIALOG_DATA);

  ngOnInit(): void {
    // A no-op when the page that opened this dialog already loaded the catalog,
    // but the figures cannot be read without the unit names, so it asks anyway.
    this.units.load();
  }

  protected unitLabel = (unitCode: string): string => this.units.label(unitCode);
  protected formatAmount = formatAmount;

  /** Cancels: nothing is deducted and the session stays where it was. */
  protected cancel(): void {
    this.dialogRef.close(false);
  }

  /** Accepts the shortage: the caller re-sends the completion with `acceptShortage`. */
  protected accept(): void {
    this.dialogRef.close(true);
  }
}
