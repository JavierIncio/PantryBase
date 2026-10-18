import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { type Diet } from '../user/profile.models';
import {
  CreateRecipeRequest,
  HistoryItem,
  RecipeDetail,
  RecipeSearchQuery,
  RecipeSummary,
} from './recipes.models';

/**
 * Reads the recipe catalog under `/api/recipes` (roadmap H4).
 *
 * Lives outside the pages so the components stay presentational and each
 * endpoint is typed in exactly one place. Authentication is handled by the
 * JWT interceptor, so this service never touches headers itself.
 */
@Injectable({ providedIn: 'root' })
export class RecipesService {
  private readonly http = inject(HttpClient);

  /**
   * Searches the recipe catalog and answers with the grid rows.
   *
   * A parameter is only sent when it carries a value: an absent `name` means
   * "no name filter", which is not the same HTTP request as `name=`, and an
   * optional filter the user never opened must not appear in the query at all —
   * the backend reads absence as "off". `diet` follows the same rule with
   * `BALANCED` as its "off" value: sending it would ask for the exact filter
   * an absent param already means, so it is dropped here once, at the wire.
   */
  search(query: RecipeSearchQuery = {}): Observable<RecipeSummary[]> {
    let params = new HttpParams();
    const name = query.name?.trim();
    if (name !== undefined && name !== '') {
      params = params.set('name', name);
    }
    if (query.ingredientId !== undefined) {
      params = params.set('ingredientId', String(query.ingredientId));
    }
    if (query.strict === true) {
      params = params.set('strict', 'true');
    }
    if (query.threshold !== undefined) {
      params = params.set('threshold', String(query.threshold));
    }
    if (query.allergenSafe === true) {
      params = params.set('allergenSafe', 'true');
    }
    if (query.diet !== undefined && query.diet !== 'BALANCED') {
      params = params.set('diet', query.diet);
    }
    return params.keys().length > 0
      ? this.http.get<RecipeSummary[]>('/api/recipes', { params })
      : this.http.get<RecipeSummary[]>('/api/recipes');
  }

  /**
   * Recommends recipes based on the user's pantry.
   *
   * Calls `GET /api/recipes/recommend` with the given params. The server forces
   * threshold to 0 internally, so the client slider refines the returned set.
   * `diet` is sent only when it actually narrows the answer: `BALANCED` means
   * "every diet" to the backend and is indistinguishable from an absent param.
   */
  recommend(
    params: { strict?: boolean; allergenSafe?: boolean; diet?: Diet } = {},
  ): Observable<RecipeSummary[]> {
    let httpParams = new HttpParams();
    if (params.strict === true) {
      httpParams = httpParams.set('strict', 'true');
    }
    if (params.allergenSafe === true) {
      httpParams = httpParams.set('allergenSafe', 'true');
    }
    if (params.diet !== undefined && params.diet !== 'BALANCED') {
      httpParams = httpParams.set('diet', params.diet);
    }
    return httpParams.keys().length > 0
      ? this.http.get<RecipeSummary[]>('/api/recipes/recommend', { params: httpParams })
      : this.http.get<RecipeSummary[]>('/api/recipes/recommend');
  }

  /**
   * Reads one recipe in full, coverage included.
   *
   * The answer is the only source of the ingredient lines and their pantry
   * coverage; a recipe that no longer exists (or never did) is a 404, which the
   * detail page renders as a "not found" state instead of an error.
   */
  getDetail(recipeId: number): Observable<RecipeDetail> {
    return this.http.get<RecipeDetail>(`/api/recipes/${recipeId}`);
  }

  /**
   * Marks or clears the recipe as favorite for the signed-in user.
   *
   * Both verbs are idempotent and answer 204, so the caller can drive this from
   * a toggle without reading the current state first: `favorite` only picks the
   * verb (`true` → PUT, `false` → DELETE), and re-sending a call already in
   * effect is a no-op rather than an error. The return stays void because the
   * resulting state is exactly what was requested — there is nothing to read
   * back.
   */
  toggleFavorite(recipeId: number, favorite: boolean): Observable<void> {
    return favorite
      ? this.http.put<void>(`/api/recipes/${recipeId}/favorite`, null)
      : this.http.delete<void>(`/api/recipes/${recipeId}/favorite`);
  }

  /** Marks or clears the recipe as saved for later; same idempotent toggle as {@link toggleFavorite}. */
  toggleSaved(recipeId: number, saved: boolean): Observable<void> {
    return saved
      ? this.http.put<void>(`/api/recipes/${recipeId}/saved`, null)
      : this.http.delete<void>(`/api/recipes/${recipeId}/saved`);
  }

  /** Ids of the caller's favorite recipes, most recent first; empty when none. */
  favoriteIds(): Observable<number[]> {
    return this.http.get<number[]>('/api/favorites');
  }

  /** Ids of the caller's saved recipes, most recent first; empty when none. */
  savedIds(): Observable<number[]> {
    return this.http.get<number[]>('/api/saved');
  }

  /**
   * The caller's completed cooking sessions, newest first.
   *
   * Entries carry only ids (see {@link HistoryItem}), so a consumer resolves the
   * recipe name and image through {@link getDetail}; an entry whose recipe is
   * gone stays in the list and must be skipped, since the history records the
   * act of cooking, not the recipe.
   */
  history(): Observable<HistoryItem[]> {
    return this.http.get<HistoryItem[]>('/api/history');
  }

  /**
   * Every author's published user recipes, newest first.
   *
   * Only `USER` recipes can be published, so provider recipes never appear here
   * (they are already public through {@link search}). Each summary's
   * `favorite`/`saved`/`owned` flags describe the signed-in caller, letting the
   * grid offer the same actions it does on the normal search.
   */
  published(): Observable<RecipeSummary[]> {
    return this.http.get<RecipeSummary[]>('/api/recipes/published');
  }

  /**
   * The caller's own recipes — private or published — newest first.
   *
   * Unlike {@link favoriteIds}/{@link savedIds}, this listing answers full
   * summaries (name, image, category, area included), so a consumer renders
   * the rows directly instead of resolving one id at a time. Ownership and
   * publication state travel in each summary's `owned`/`published` flags.
   */
  mine(): Observable<RecipeSummary[]> {
    return this.http.get<RecipeSummary[]>('/api/recipes/mine');
  }

  /**
   * Publishes one of the caller's own recipes (idempotent) and answers its
   * refreshed detail with `published: true`.
   *
   * A recipe the caller does not own is rejected rather than silently ignored,
   * so the UI can tell "not allowed" apart from "already done".
   */
  publish(recipeId: number): Observable<RecipeDetail> {
    return this.http.post<RecipeDetail>(`/api/recipes/${recipeId}/publish`, null);
  }

  /** Takes one of the caller's own recipes back to private (idempotent); refreshed detail with `published: false`. */
  unpublish(recipeId: number): Observable<RecipeDetail> {
    return this.http.delete<RecipeDetail>(`/api/recipes/${recipeId}/publish`);
  }

  /**
   * Creates a private recipe owned by the caller (201) and answers its detail.
   *
   * The recipe starts private and unpublished — making it visible is a separate
   * {@link publish} call; there is no partial update semantics here, see
   * {@link CreateRecipeRequest}.
   */
  create(request: CreateRecipeRequest): Observable<RecipeDetail> {
    return this.http.post<RecipeDetail>('/api/recipes', request);
  }
}
