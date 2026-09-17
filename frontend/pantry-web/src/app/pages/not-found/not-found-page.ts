import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PlaceholderPage } from '../placeholder/placeholder-page';

/** Fallback view rendered when the requested route does not match any page. */
@Component({
  selector: 'app-not-found-page',
  imports: [PlaceholderPage],
  template: `<app-placeholder-page title="Page not found" icon="error_outline" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
