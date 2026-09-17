import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Pantry area: inventory of ingredients and quantities (roadmap H3). */
@Component({
  selector: 'app-pantry-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Pantry" icon="kitchen" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PantryPage {}
