import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Recipes area: search, filtering by pantry coverage and detail views (roadmap H4). */
@Component({
  selector: 'app-recipes-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Recipes" icon="menu_book" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecipesPage {}
