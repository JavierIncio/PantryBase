import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // HTTP with the JWT interceptor: API calls are same-origin in dev through
    // the proxy (proxy.conf.json), so the httpOnly refresh cookie flows on its
    // own and the interceptor only manages the Bearer access token + 401 retry.
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideRouter(routes),
  ],
};
