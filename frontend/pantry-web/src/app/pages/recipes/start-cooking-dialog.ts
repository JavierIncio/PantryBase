import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { RecipeAvailabilityPanel } from '../../core/recipes/recipe-availability-panel';
import { RecipeAvailability } from '../../core/recipes/recipe-matching';

/**
 * Confirms starting a recipe the pantry cannot fully cover.
 *
 * The starting of a session itself never touches the pantry — stock is only
 * deducted on completion — so this is friction the user asked for, not a
 * refusal: the point is to warn *before* cooking that some ingredients are short
 * or unrecognised. "Start anyway" proceeds and "Cancel" leaves the detail
 * untouched, so the only consequence of cancelling is that no session is made.
 *
 * The body reuses {@link RecipeAvailabilityPanel}, so the dialog and the panel
 * on the page never word the same three facts differently.
 */
@Component({
  selector: 'app-start-cooking-dialog',
  imports: [MatButton, MatDialogActions, MatDialogContent, MatDialogTitle, RecipeAvailabilityPanel],
  templateUrl: './start-cooking-dialog.html',
  styleUrl: './start-cooking-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StartCookingDialog {
  private readonly dialogRef = inject(MatDialogRef<StartCookingDialog, boolean>);

  /** The availability of the recipe the user is about to cook. */
  protected readonly availability = inject<RecipeAvailability>(MAT_DIALOG_DATA);

  /** Cancels: no session is created and the recipe stays on screen. */
  protected cancel(): void {
    this.dialogRef.close(false);
  }

  /** Proceeds: the caller creates the session despite the gaps. */
  protected confirm(): void {
    this.dialogRef.close(true);
  }
}
