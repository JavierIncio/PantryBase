import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Cooking area: cooking sessions, progress tracking and stock deduction (roadmap H5). */
@Component({
  selector: 'app-cooking-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Cooking" icon="soup_kitchen" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CookingPage {}
