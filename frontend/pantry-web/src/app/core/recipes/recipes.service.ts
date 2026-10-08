import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RecipeDetail, RecipeSearchQuery, RecipeSummary } from './recipes.models';

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
   * the backend reads absence as "off".
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
    return params.keys().length > 0
      ? this.http.get<RecipeSummary[]>('/api/recipes', { params })
      : this.http.get<RecipeSummary[]>('/api/recipes');
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
}
