import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IngredientDetail, IngredientSearchHit } from './catalog.models';

/**
 * Reads the ingredient catalog backed by USDA FoodData Central under
 * `/api/catalog/ingredients`.
 *
 * Lives outside the dialog that consumes it so the component stays
 * presentational and each endpoint is typed in exactly one place.
 * Authentication is handled by the JWT interceptor, so this service never
 * touches headers itself.
 *
 * Both methods call the external provider, so a failure of either is a failure
 * of that provider rather than of this application: the status is 502 whether the
 * provider is down or its hourly quota is spent, and there is no local data to
 * fall back on. Callers are expected to say so instead of retrying.
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  /**
   * Foods matching `query`, cheapest to ask and the only call of this feature
   * that may be repeated freely: the backend caches search answers, so a
   * keystroke-sized retry does not spend provider quota.
   *
   * A blank query is refused by the API with a 400, so callers validate before
   * asking. The results are provider references: picking one is not enough to
   * create a pantry line, {@link getIngredient} still has to be called.
   */
  searchIngredients(query: string): Observable<IngredientSearchHit[]> {
    return this.http.get<IngredientSearchHit[]>('/api/catalog/ingredients', {
      params: new HttpParams().set('query', query),
    });
  }

  /**
   * Reads one food in full, which is what materializes it in the local catalog.
   *
   * Not optional before `POST /api/pantry`: that endpoint resolves the
   * `fdcId` against the local catalog and answers 404 for a food that was only
   * ever searched for. The read costs one provider call the first time and is
   * served from cache for a day afterwards, which is why the quota policy stays
   * on the server instead of being spread over the pantry endpoints.
   *
   * It is also the only call that returns `measures`, the household measures the
   * provider actually weighed for this food, so the unit selector cannot be
   * filled in before it answers.
   */
  getIngredient(fdcId: number): Observable<IngredientDetail> {
    return this.http.get<IngredientDetail>(`/api/catalog/ingredients/${fdcId}`);
  }
}
