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
        path: 'cooking',
        title: 'Cooking',
        loadComponent: () => import('./pages/cooking/cooking-page').then((m) => m.CookingPage),
      },
      {
        path: 'social',
        title: 'Social',
        loadComponent: () => import('./pages/social/social-page').then((m) => m.SocialPage),
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
