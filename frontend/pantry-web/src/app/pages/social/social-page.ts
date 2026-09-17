import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Social area: favorites, published recipes and cooking history (roadmap H7). */
@Component({
  selector: 'app-social-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Social" icon="people" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SocialPage {}
