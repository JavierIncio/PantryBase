import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { IngredientDetail, IngredientSearchHit } from './catalog.models';
import { CatalogService } from './catalog.service';

/**
 * A provider reference, exactly as the search endpoint answers it.
 *
 * Note what it does not carry: no local ingredient id, because the search is a
 * lookup of an external catalog and the pantry endpoint only ever wants the
 * `fdcId`.
 */
const HITS: IngredientSearchHit[] = [
  { fdcId: 171265, name: 'Milk, whole', dataType: 'Foundation' },
  { fdcId: 168917, name: 'Flour, white', dataType: 'SR Legacy' },
];

const MILK: IngredientDetail = {
  id: 41,
  fdcId: 171265,
  name: 'Milk, whole',
  dataType: 'Foundation',
  category: 'Dairy and Egg Products',
  measures: [
    { unitCode: 'CUP', gramPerUnit: 244 },
    { unitCode: 'PINT', gramPerUnit: 489 },
  ],
  nutrients: { energyKcal: 61, proteinG: 3.15, fatG: 3.25, carbsG: 4.8 },
  densityClass: 'LIQUID',
};

describe('CatalogService', () => {
  let service: CatalogService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(CatalogService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('searchIngredients GETs the query as a parameter, not as part of the path', async () => {
    const result = firstValueFrom(service.searchIngredients('milk & honey'));

    const req = http.expectOne((candidate) => candidate.url === '/api/catalog/ingredients');
    expect(req.request.method).toBe('GET');
    // A parameter, so the backend can cache the answer by it and so a query with
    // spaces or ampersands needs no escaping at the call site.
    expect(req.request.params.get('query')).toBe('milk & honey');
    req.flush(HITS);

    await expect(result).resolves.toEqual(HITS);
  });

  it('getIngredient reads one food by its fdcId and keeps the measures', async () => {
    const result = firstValueFrom(service.getIngredient(171265));

    const req = http.expectOne('/api/catalog/ingredients/171265');
    expect(req.request.method).toBe('GET');
    req.flush(MILK);

    // The measures are the reason this call is not optional: without them the unit
    // selector has nothing to put first.
    await expect(result).resolves.toMatchObject({
      fdcId: 171265,
      measures: [
        { unitCode: 'CUP', gramPerUnit: 244 },
        { unitCode: 'PINT', gramPerUnit: 489 },
      ],
    });
  });

  it('surfaces the provider failure of a search as a 502 with its own message', async () => {
    const result = firstValueFrom(service.searchIngredients('milk'));

    http
      .expectOne((candidate) => candidate.url === '/api/catalog/ingredients')
      .flush(
        {
          timestamp: '2026-10-05T09:00:00Z',
          status: 502,
          error: 'Bad Gateway',
          message: 'USDA FoodData Central rejected the request: quota exceeded',
          path: '/api/catalog/ingredients',
        },
        { status: 502, statusText: 'Bad Gateway' },
      );

    // There is no local catalog to answer from, so the caller is told the provider
    // failed rather than shown an empty result that looks like "no such food".
    await expect(result).rejects.toMatchObject({
      status: 502,
      error: { message: 'USDA FoodData Central rejected the request: quota exceeded' },
    });
  });

  it('reports a food that is not in the local catalog as a 404', async () => {
    const result = firstValueFrom(service.getIngredient(999999));

    http.expectOne('/api/catalog/ingredients/999999').flush(
      {
        timestamp: '2026-10-05T09:00:00Z',
        status: 404,
        error: 'Not Found',
        message: 'Ingredient 999999 not found',
        path: '/api/catalog/ingredients/999999',
      },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(result).rejects.toMatchObject({ status: 404 });
  });
});
