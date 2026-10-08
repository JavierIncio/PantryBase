import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { RecipeDetail, RecipeSummary } from './recipes.models';
import { RecipesService } from './recipes.service';

const HIT: RecipeSummary = {
  id: 1,
  source: 'THE_MEAL_DB',
  externalId: '52771',
  name: 'Spaghetti alla Carbonara',
  category: 'Pasta',
  area: 'Italian',
  imageUrl: 'https://www.themealdb.com/images/media/meals/llcbn01574260722.jpg',
  servings: 4,
  matchScore: 1,
  missingCount: 0,
  allergenCodes: ['EGG', 'MILK'],
};

const DETAIL: RecipeDetail = {
  id: 1,
  source: 'THE_MEAL_DB',
  externalId: '52771',
  name: 'Spaghetti alla Carbonara',
  category: 'Pasta',
  area: 'Italian',
  instructions: 'Boil the pasta.\nFry the bacon.',
  imageUrl: 'https://www.themealdb.com/images/media/meals/llcbn01574260722.jpg',
  videoUrl: null,
  tags: ['Pasta'],
  servings: 4,
  sourceUrl: 'https://www.themealdb.com/meal/52771',
  ingredients: [],
  nutrients: null,
  allergenCodes: ['EGG'],
  matchScore: 1,
  coverage: [],
};

describe('RecipesService', () => {
  let service: RecipesService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(RecipesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  /**
   * Matches a search request whether or not it carries query params: the
   * testing backend compares string matchers against the full URL, so the
   * empty-query form walks through the predicate the same way.
   */
  const expectSearch = () => http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));

  it('searches with no query string when no filter was opened', async () => {
    const result = firstValueFrom(service.search());

    const req = expectSearch();
    expect(req.request.method).toBe('GET');
    expect(req.request.urlWithParams).toBe('/api/recipes');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('sends a trimmed name and nothing else when only a name was typed', async () => {
    const result = firstValueFrom(service.search({ name: '  carbonara  ' }));

    const req = expectSearch();
    expect(req.request.params.get('name')).toBe('carbonara');
    expect(req.request.params.keys()).toEqual(['name']);
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('never sends a blank name, which would mean "filter by nothing"', async () => {
    const result = firstValueFrom(service.search({ name: '   ' }));

    // An absent name says "no name filter" on the API; `name=` would say the
    // opposite — that a name was asked for and it is empty.
    const req = expectSearch();
    expect(req.request.urlWithParams).toBe('/api/recipes');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('sends the toggles and the threshold only when they are set', async () => {
    const result = firstValueFrom(
      service.search({ name: 'pasta', strict: true, allergenSafe: true, threshold: 70 }),
    );

    const req = expectSearch();
    expect(req.request.params.get('name')).toBe('pasta');
    expect(req.request.params.get('strict')).toBe('true');
    expect(req.request.params.get('allergenSafe')).toBe('true');
    expect(req.request.params.get('threshold')).toBe('70');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('leaves an off toggle out of the query instead of sending false', async () => {
    const result = firstValueFrom(service.search({ name: 'pasta', strict: false }));

    const req = expectSearch();
    expect(req.request.params.keys()).toEqual(['name']);
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('getDetail GETs one recipe by its id', async () => {
    const result = firstValueFrom(service.getDetail(1));

    const req = http.expectOne('/api/recipes/1');
    expect(req.request.method).toBe('GET');
    req.flush(DETAIL);

    await expect(result).resolves.toEqual(DETAIL);
  });

  it('surfaces a 404 of a missing recipe for the page to render as "not found"', async () => {
    const result = firstValueFrom(service.getDetail(999));

    http.expectOne('/api/recipes/999').flush(
      {
        timestamp: '2026-10-05T12:00:00Z',
        status: 404,
        error: 'Not Found',
        message: 'Recipe 999 not found',
        path: '/api/recipes/999',
      },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(result).rejects.toMatchObject({ status: 404 });
  });
});
