import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CreateRecipeRequest, HistoryItem, RecipeDetail, RecipeSummary } from './recipes.models';
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
  favorite: true,
  saved: false,
  owned: false,
  published: false,
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
  favorite: false,
  saved: false,
  owned: true,
  published: true,
};

const HISTORY_ITEM: HistoryItem = {
  id: 7,
  userId: 1,
  recipeId: 1,
  cookingSessionId: 3,
  createdAt: '2026-10-12T18:30:00Z',
};

const CREATE_REQUEST: CreateRecipeRequest = {
  name: 'My Carbonara',
  category: 'Pasta',
  area: 'Italian',
  instructions: 'Boil, fry, mix.',
  imageUrl: null,
  videoUrl: null,
  tags: ['quick'],
  servings: 2,
  sourceUrl: null,
  ingredients: [{ ingredientId: 12, amount: 200, unitCode: 'G', position: 0 }],
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

  /** The recommend path must be matched exactly: it also starts with `/api/recipes`. */
  const expectRecommend = () =>
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));

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

  it('sends ingredientId alone when only an ingredient is selected', async () => {
    const result = firstValueFrom(service.search({ ingredientId: 12 }));

    const req = expectSearch();
    expect(req.request.params.keys()).toEqual(['ingredientId']);
    expect(req.request.params.get('ingredientId')).toBe('12');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('sends ingredientId alongside the name, toggles and threshold', async () => {
    const result = firstValueFrom(
      service.search({
        name: 'cabbage',
        ingredientId: 12,
        strict: true,
        allergenSafe: true,
        threshold: 0,
      }),
    );

    const req = expectSearch();
    expect(req.request.params.get('name')).toBe('cabbage');
    expect(req.request.params.get('ingredientId')).toBe('12');
    expect(req.request.params.get('strict')).toBe('true');
    expect(req.request.params.get('allergenSafe')).toBe('true');
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('sends the diet only when it actually narrows the search', async () => {
    const result = firstValueFrom(service.search({ diet: 'LOW_CARB' }));

    const req = expectSearch();
    expect(req.request.params.keys()).toEqual(['diet']);
    expect(req.request.params.get('diet')).toBe('LOW_CARB');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('never sends a BALANCED diet, which the backend already reads as "no filter"', async () => {
    const balanced = firstValueFrom(service.search({ diet: 'BALANCED' }));

    const balancedReq = expectSearch();
    // `diet=BALANCED` would ask for exactly the filter an absent param means.
    expect(balancedReq.request.urlWithParams).toBe('/api/recipes');
    balancedReq.flush([HIT]);
    await expect(balanced).resolves.toEqual([HIT]);

    const absent = firstValueFrom(service.search({ name: 'pasta', diet: undefined }));

    const absentReq = expectSearch();
    expect(absentReq.request.params.keys()).toEqual(['name']);
    absentReq.flush([HIT]);
    await expect(absent).resolves.toEqual([HIT]);
  });

  it('recommend sends the diet so the suggestions respect it', async () => {
    const result = firstValueFrom(service.recommend({ diet: 'HIGH_PROTEIN' }));

    const req = expectRecommend();
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual(['diet']);
    expect(req.request.params.get('diet')).toBe('HIGH_PROTEIN');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('recommend leaves a BALANCED diet out and sends the other filters alone', async () => {
    const balanced = firstValueFrom(service.recommend({ diet: 'BALANCED', strict: true }));

    const balancedReq = expectRecommend();
    expect(balancedReq.request.params.keys()).toEqual(['strict']);
    balancedReq.flush([HIT]);
    await expect(balanced).resolves.toEqual([HIT]);

    const none = firstValueFrom(service.recommend());

    const noneReq = expectRecommend();
    expect(noneReq.request.urlWithParams).toBe('/api/recipes/recommend');
    noneReq.flush([HIT]);
    await expect(none).resolves.toEqual([HIT]);
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

  it('toggleFavorite PUTs to mark the recipe favorite, with no body and a 204 answer', async () => {
    const result = firstValueFrom(service.toggleFavorite(1, true));

    const req = http.expectOne('/api/recipes/1/favorite');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeNull();
  });

  it('toggleFavorite DELETEs to clear the mark, with no body and a 204 answer', async () => {
    const result = firstValueFrom(service.toggleFavorite(1, false));

    const req = http.expectOne('/api/recipes/1/favorite');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeNull();
  });

  it('toggleSaved PUTs to save the recipe, with no body and a 204 answer', async () => {
    const result = firstValueFrom(service.toggleSaved(1, true));

    const req = http.expectOne('/api/recipes/1/saved');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeNull();
  });

  it('toggleSaved DELETEs to remove the saved mark, with no body and a 204 answer', async () => {
    const result = firstValueFrom(service.toggleSaved(1, false));

    const req = http.expectOne('/api/recipes/1/saved');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeNull();
  });

  it('favoriteIds GETs the caller favorite ids', async () => {
    const result = firstValueFrom(service.favoriteIds());

    const req = http.expectOne('/api/favorites');
    expect(req.request.method).toBe('GET');
    req.flush([3, 1]);

    await expect(result).resolves.toEqual([3, 1]);
  });

  it('savedIds GETs the caller saved ids', async () => {
    const result = firstValueFrom(service.savedIds());

    const req = http.expectOne('/api/saved');
    expect(req.request.method).toBe('GET');
    req.flush([2]);

    await expect(result).resolves.toEqual([2]);
  });

  it('history GETs the caller cooking history', async () => {
    const result = firstValueFrom(service.history());

    const req = http.expectOne('/api/history');
    expect(req.request.method).toBe('GET');
    req.flush([HISTORY_ITEM]);

    await expect(result).resolves.toEqual([HISTORY_ITEM]);
  });

  it('published GETs every author published recipes', async () => {
    const result = firstValueFrom(service.published());

    const req = http.expectOne('/api/recipes/published');
    expect(req.request.method).toBe('GET');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('mine GETs the caller own recipes, private or published', async () => {
    const result = firstValueFrom(service.mine());

    const req = http.expectOne('/api/recipes/mine');
    expect(req.request.method).toBe('GET');
    req.flush([HIT]);

    await expect(result).resolves.toEqual([HIT]);
  });

  it('publish POSTs with no body and maps the refreshed detail', async () => {
    const published = { ...DETAIL, published: true };
    const result = firstValueFrom(service.publish(1));

    const req = http.expectOne('/api/recipes/1/publish');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(published);

    await expect(result).resolves.toEqual(published);
  });

  it('unpublish DELETEs and maps the refreshed detail', async () => {
    const unpublished = { ...DETAIL, published: false };
    const result = firstValueFrom(service.unpublish(1));

    const req = http.expectOne('/api/recipes/1/publish');
    expect(req.request.method).toBe('DELETE');
    req.flush(unpublished);

    await expect(result).resolves.toEqual(unpublished);
  });

  it('create POSTs the request body and maps the created detail', async () => {
    const result = firstValueFrom(service.create(CREATE_REQUEST));

    const req = http.expectOne('/api/recipes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(CREATE_REQUEST);
    req.flush(DETAIL);

    await expect(result).resolves.toEqual(DETAIL);
  });
});
