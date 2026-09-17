import { Routes } from '@angular/router';
import { Shell } from './core/layout/shell';

/** Route definitions of the application: feature pages are lazy-loaded leaves under the shell. */
export const routes: Routes = [
  {
    path: '',
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
