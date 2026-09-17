import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root application component. It only hosts the routed content: the shell
 * (toolbar + navigation) is provided by the router through the default route.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  styleUrl: './app.scss',
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
