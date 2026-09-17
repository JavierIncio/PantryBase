import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardSubtitle,
  MatCardTitle,
} from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';

/**
 * Presentational empty-state used by the feature pages that are not implemented
 * yet (scaffold milestone H0). It displays the feature name and a "coming
 * soon" note so every route has a meaningful placeholder view.
 */
@Component({
  selector: 'app-placeholder-page',
  imports: [MatCard, MatCardContent, MatCardHeader, MatCardSubtitle, MatCardTitle, MatIcon],
  styleUrl: './placeholder-page.scss',
  template: `
    <mat-card class="placeholder">
      <mat-card-header>
        <mat-icon mat-card-avatar>{{ icon() }}</mat-icon>
        <mat-card-title>{{ title() }}</mat-card-title>
        <mat-card-subtitle>Coming soon</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <p>
          This area of PantryBase is part of the product roadmap and will be implemented in a later
          milestone.
        </p>
      </mat-card-content>
    </mat-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderPage {
  /** Feature name displayed as the page title. */
  readonly title = input.required<string>();

  /** Material icon ligature shown next to the title. */
  readonly icon = input('construction');
}
