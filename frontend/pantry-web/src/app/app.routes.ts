import { Routes } from '@angular/router';
import { Shell } from './core/layout/shell';
import { authGuard } from './core/auth/auth.guard';

/**
 * Route definitions of the application.
 *
 * The authentication screens are public and live outside the shell (no
 * toolbar/navigation). Every product area hangs from the guarded shell route:
 * an anonymous visitor is either silently restored through the refresh cookie
 * by {@link authGuard} or bounced to the login page.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () => import('./pages/auth/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Create account',
    loadComponent: () => import('./pages/auth/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'auth/callback',
    title: 'Signing in',
    loadComponent: () =>
      import('./pages/auth/oauth-redirect-page').then((m) => m.OAuthRedirectPage),
  },
  {
    // Path fixed by the backend email link: `{base}/auth/reset-password?token=`.
    path: 'auth/forgot-password',
    title: 'Forgot password',
    loadComponent: () =>
      import('./pages/auth/forgot-password-page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'auth/reset-password',
    title: 'Reset password',
    loadComponent: () =>
      import('./pages/auth/reset-password-page').then((m) => m.ResetPasswordPage),
  },
  {
    path: '',
    canMatch: [authGuard],
    component: Shell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'pantry' },
      {
        path: 'pantry',
        title: 'Pantry',
        loadComponent: () => import('./pages/pantry/pantry-page').then((m) => m.PantryPage),
      },
      {
        path: 'recipes',
        title: 'Recipes',
        loadComponent: () => import('./pages/recipes/recipes-page').then((m) => m.RecipesPage),
      },
      {
        // Declared before `recipes/:id` on purpose: `new` is a literal the
        // parameter route would otherwise swallow as an id (and 404 on).
        path: 'recipes/new',
        title: 'New recipe',
        loadComponent: () =>
          import('./pages/recipes/recipe-create-page').then((m) => m.RecipeCreatePage),
      },
      {
        path: 'recipes/:id',
        title: 'Recipe',
        loadComponent: () =>
          import('./pages/recipes/recipe-detail-page').then((m) => m.RecipeDetailPage),
      },
      {
        // The session detail keeps the `/cooking/:id` URL (it is also where the
        // "Start cooking" action lands), even though the cooking list was merged
        // into History.
        path: 'cooking/:id',
        title: 'Cooking session',
        loadComponent: () =>
          import('./pages/cooking/cooking-session-page').then((m) => m.CookingSessionPage),
      },
      {
        // Old bookmarks in `/cooking` land on the merged page instead of 404.
        path: 'cooking',
        pathMatch: 'full',
        redirectTo: 'history',
      },
      {
        path: 'saved',
        title: 'My cookbook',
        loadComponent: () =>
          import('./pages/collection/collection-page').then((m) => m.CollectionPage),
      },
      {
        path: 'history',
        title: 'History',
        loadComponent: () => import('./pages/history/history-page').then((m) => m.HistoryPage),
      },
      {
        path: 'profile',
        title: 'Profile',
        loadComponent: () => import('./pages/profile/profile-page').then((m) => m.ProfilePage),
      },
      {
        path: '**',
        title: 'Not found',
        loadComponent: () => import('./pages/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
