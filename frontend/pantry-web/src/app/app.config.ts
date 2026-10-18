import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // HTTP with the JWT interceptor: API calls are same-origin in dev through
    // the proxy (proxy.conf.json), so the httpOnly refresh cookie flows on its
    // own and the interceptor only manages the Bearer access token + 401 retry.
    provideHttpClient(withInterceptors([jwtInterceptor])),
    // In-memory scrolling makes routerLink fragments (`/profile#statistics`)
    // scroll to the target id after the view renders. The scroll offset stays
    // at the router default [0, 0] (Angular 22 no longer accepts `scrollOffset`
    // on this feature): each anchor target declares its own `scroll-margin-top`
    // against the sticky toolbar instead.
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
  ],
};
