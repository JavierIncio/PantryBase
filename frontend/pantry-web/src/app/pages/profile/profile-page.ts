import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Profile area: account, preferences and allergy exclusions (roadmap H1/H7). */
@Component({
  selector: 'app-profile-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Profile" icon="person" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {}
