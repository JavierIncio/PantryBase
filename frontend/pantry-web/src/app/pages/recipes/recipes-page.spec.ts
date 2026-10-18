import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { IngredientDetail, IngredientSearchHit } from '../../core/catalog/catalog.models';
import { RecipeSummary } from '../../core/recipes/recipes.models';
import { RecipesPage } from './recipes-page';

/** Fully covered: 100%, no missing line, two allergens, an image, 4 servings. */
const PASTA: RecipeSummary = {
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
  favorite: false,
  saved: false,
  owned: false,
  published: false,
};

/** Partially covered: below the default bar once the slider moves, 2 missing. */
const STEW: RecipeSummary = {
  id: 2,
  source: 'USER',
  externalId: null,
  name: 'Beef Stew',
  category: 'Main Course',
  area: '',
  imageUrl: 'https://example.com/stew.jpg',
  servings: null,
  matchScore: 0.7,
  missingCount: 2,
  allergenCodes: [],
  favorite: false,
  saved: false,
  owned: true,
  published: false,
};

/** No match score at all: coverage unknown, so never above any bar. */
const SALAD: RecipeSummary = {
  id: 3,
  source: 'USER',
  externalId: null,
  name: 'Side Salad',
  category: 'Side',
  area: '',
  imageUrl: null,
  servings: 1,
  matchScore: null,
  missingCount: null,
  allergenCodes: [],
  favorite: false,
  saved: false,
  owned: true,
  published: false,
};

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-08T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/recipes',
});

/** A catalog hit, exactly as `GET /api/catalog/ingredients?query=` answers it. */
const CABBAGE_HIT: IngredientSearchHit = {
  fdcId: 170000,
  name: 'Cabbage',
  dataType: 'Foundation',
};

/**
 * The detail of that hit, which is what exposes the internal id the recipe
 * endpoint filters by — the hit itself only carries the provider `fdcId`.
 */
const CABBAGE: IngredientDetail = {
  id: 12,
  fdcId: CABBAGE_HIT.fdcId,
  name: CABBAGE_HIT.name,
  dataType: CABBAGE_HIT.dataType,
  category: 'Vegetables and Vegetable Products',
  nutrients: { energyKcal: 25, proteinG: 1.28, fatG: 0.1, carbsG: 5.8 },
  densityClass: null,
  measures: [],
};

/** A fully covered provider recipe discovered by the ingredient filter. */
const CABBAGE_DISH: RecipeSummary = {
  id: 9,
  source: 'THE_MEAL_DB',
  externalId: '52944',
  name: 'Cabbage Roll',
  category: 'Side',
  area: 'British',
  imageUrl: null,
  servings: 4,
  matchScore: 1,
  missingCount: 0,
  allergenCodes: [],
  favorite: false,
  saved: false,
  owned: false,
  published: false,
};

describe('RecipesPage', () => {
  let http: HttpTestingController;

  const configure = async () => {
    await TestBed.configureTestingModule({
      imports: [RecipesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /**
   * Creates the page and answers its initial search.
   *
   * The search cards expect the default threshold of 70, so a fixture wholly
   * below it must be passed to the empty-state tests.
   */
  const render = async (recipes: RecipeSummary[] = [PASTA, STEW, SALAD]) => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    const prefsReq = http.expectOne('/api/users/preferences');
    prefsReq.flush({ filterMode: 'LAX', coverageThreshold: 70, diet: 'BALANCED' });
    const recipesReq = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    expect(recipesReq.request.params.get('threshold')).toBe('0');
    recipesReq.flush(recipes);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<RecipesPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<RecipesPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<RecipesPage>, selector: string, index = 0): void => {
    (nodes(fixture, selector)[index] as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  /** Answers the next search — the debounce and the toggles both end in one. */
  const answerSearch = (fixture: ComponentFixture<RecipesPage>, recipes: RecipeSummary[]): void => {
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes')).flush(recipes);
    fixture.detectChanges();
  };

  const cardNames = (fixture: ComponentFixture<RecipesPage>): string[] =>
    nodes(fixture, '.recipe-card .mat-mdc-card-title').map((node) => text(node));

  /**
   * Matches search requests whether or not they carry params: the testing
   * backend compares string matchers against the full URL (`...?strict=true`),
   * so the predicate is the way to grab a search whatever it sends.
   */
  const searchReqs = (fixture: ComponentFixture<RecipesPage>) =>
    http.match((req) => req.urlWithParams.startsWith('/api/recipes'));

  const expectSearch = () => http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));

  /**
   * Types into the ingredient box and lets its debounce fire the catalog search.
   *
   * The debounce needs fake timers, but the Material autocomplete opens its
   * panel and shadows its options through real macrotasks, so timers are
   * restored as soon as the debounce has been advanced.
   */
  const typeIngredient = (fixture: ComponentFixture<RecipesPage>, value: string): void => {
    const input = nodes(fixture, '.ingredient-field input')[0] as HTMLInputElement;
    input.focus();
    vi.useFakeTimers();
    try {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      vi.advanceTimersByTime(300);
    } finally {
      vi.useRealTimers();
    }
    fixture.detectChanges();
  };

  /**
   * Drives a full ingredient pick: type, answer the catalog search, click the
   * option, then answer the detail read that resolves the internal id. The
   * recipe search this triggers is left for the caller to answer.
   */
  const pickIngredient = async (
    fixture: ComponentFixture<RecipesPage>,
    hit: IngredientSearchHit,
    detail: IngredientDetail,
  ): Promise<void> => {
    typeIngredient(fixture, hit.name);

    const catalogReq = http.expectOne((req) => req.url === '/api/catalog/ingredients');
    expect(catalogReq.request.params.get('query')).toBe(hit.name);
    catalogReq.flush([hit]);
    fixture.detectChanges();
    await fixture.whenStable();

    const option = document.querySelector('.mat-mdc-autocomplete-panel mat-option') as HTMLElement;
    option.click();
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectOne(`/api/catalog/ingredients/${hit.fdcId}`).flush(detail);
    fixture.detectChanges();
  };

  /**
   * Picks a diet by the label the select shows through the overlay.
   *
   * The panel is an overlay (like the autocomplete's), so the option is read
   * from `document` once the trigger has opened it; the request the change
   * triggers is left for the caller to answer.
   */
  const pickDiet = async (fixture: ComponentFixture<RecipesPage>, label: string): Promise<void> => {
    const trigger = nodes(fixture, '.diet-field .mat-mdc-select-trigger')[0] as HTMLElement;
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const option = [...document.querySelectorAll('.mat-mdc-select-panel mat-option')].find(
      (node) => text(node) === label,
    ) as HTMLElement;
    option.click();
    fixture.detectChanges();
    await fixture.whenStable();
  };

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('renders the grid in coverage order with the badges of each recipe', async () => {
    const fixture = await render();
    // The default bar is 70% and the last fixture recipe has no score at all,
    // so the bar opens (0%) for the full grid to sit on screen.
    fixture.componentInstance.filters.controls.threshold.setValue(0);
    fixture.detectChanges();

    // Best covered first: 100%, then 70%, and the unknown one last.
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara', 'Beef Stew', 'Side Salad']);
    // Order on screen is the client's to keep, but the percentages are the
    // server's score written out verbatim.
    expect(nodes(fixture, '.recipe-card .coverage-badge').map((node) => text(node))).toEqual([
      '100%',
      '70%',
      'No coverage',
    ]);
    expect(nodes(fixture, '.recipe-card .recipe-source').map((node) => text(node))).toEqual([
      'TheMealDB',
      'Own',
      'Own',
    ]);
    // Servings show only where the API says there are any, pluralized to match.
    expect(nodes(fixture, '.recipe-card .recipe-servings').map((node) => text(node))).toEqual([
      'restaurant 4 servings',
      'restaurant 1 serving',
    ]);
    // A zero missing count is skipped: "0 ingredients short" is not news.
    expect(nodes(fixture, '.recipe-card .recipe-missing').map((node) => text(node))).toEqual([
      '2 ingredients short of full coverage',
    ]);
    expect(nodes(fixture, '.recipe-allergens mat-chip').map((node) => text(node))).toEqual([
      'EGG',
      'MILK',
    ]);
  });

  it('shows the image where the API knows one and an icon where it does not', async () => {
    const fixture = await render();
    fixture.componentInstance.filters.controls.threshold.setValue(0);
    fixture.detectChanges();

    expect(nodes(fixture, '.recipe-card img').map((img) => img.getAttribute('alt'))).toEqual([
      'Spaghetti alla Carbonara',
      'Beef Stew',
    ]);
    // Salad has no image URL, so its card replaces it with an icon instead of
    // letting the browser break a reference.
    expect(nodes(fixture, '.recipe-image-fallback').length).toBe(1);
    expect(nodes(fixture, '.recipe-image-fallback mat-icon').length).toBe(1);
  });

  it('filters the grid on the slider without asking the server again', async () => {
    const fixture = await render();

    fixture.componentInstance.filters.controls.threshold.setValue(71);
    fixture.detectChanges();

    // Beef Stew sits exactly at 70, so 71 drops it; the slider promised to
    // recompute over the rows already on screen, and it spent no request.
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
    expect(text(root(fixture).querySelector('.threshold-value'))).toBe('71%');
    expect(http.match(() => true)).toEqual([]);
  });

  it('reports an empty answer as "no recipes", not as a grid with nothing', async () => {
    const fixture = await render([]);

    expect(text(root(fixture).querySelector('.recipes-empty h2'))).toBe('No recipes found');
    expect(nodes(fixture, '.recipe-card').length).toBe(0);
    expect(text(root(fixture).querySelector('.recipes-empty'))).toContain(
      'Nothing matches this search yet',
    );
  });

  it('tells a grid emptied by the slider to lower the bar', async () => {
    // Side Salad has no coverage at all, and the default bar is 70%.
    const fixture = await render([SALAD]);

    expect(text(root(fixture).querySelector('.recipes-empty h2'))).toBe('Nothing reaches the bar');
    expect(text(root(fixture).querySelector('.recipes-empty'))).toContain('below 70%');
  });

  it('searches once per pause in typing, with the name trimmed', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    http.expectOne('/api/users/preferences').flush({
      filterMode: 'LAX',
      coverageThreshold: 70,
      diet: 'BALANCED',
    });
    http
      .expectOne((req) => req.urlWithParams.startsWith('/api/recipes'))
      .flush([PASTA, STEW, SALAD]);
    fixture.detectChanges();

    vi.useFakeTimers();
    try {
      const input = nodes(fixture, '.search-field input')[0] as HTMLInputElement;

      input.value = 'pa';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      input.value = 'pasta';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // Nothing yet — the user has typed two letters but is still thinking.
      expect(searchReqs(fixture).length).toBe(0);
      vi.advanceTimersByTime(299);
      fixture.detectChanges();
      expect(searchReqs(fixture).length).toBe(0);

      vi.advanceTimersByTime(2);
      fixture.detectChanges();
      const reqs = searchReqs(fixture);
      expect(reqs.length).toBe(1);
      expect(reqs[0].request.params.get('name')).toBe('pasta');
      reqs[0].flush([PASTA]);
      fixture.detectChanges();
    } finally {
      vi.useRealTimers();
    }

    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('treats a name that is only spaces as an empty box and re-searches without it', async () => {
    const fixture = await render();

    const input = nodes(fixture, '.search-field input')[0] as HTMLInputElement;
    vi.useFakeTimers();
    try {
      input.value = '   ';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // Once the pause ends, the box is empty; an empty box means "no name
      // filter", and that is exactly what the search says — no name param.
      vi.advanceTimersByTime(300);
      fixture.detectChanges();
      const reqs = searchReqs(fixture);
      expect(reqs.length).toBe(1);
      const params = reqs[0].request.params;
      expect(params.get('threshold')).toBe('0');
      expect(params.keys().filter((k) => k !== 'threshold')).toEqual([]);
      reqs[0].flush([PASTA, STEW, SALAD]);
      fixture.detectChanges();
    } finally {
      vi.useRealTimers();
    }

    // Side Salad carries no score, so the default 70% bar drops it and only
    // its card's fallback icon stays in the DOM.
    expect(cardNames(fixture).length).toBe(2);
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara', 'Beef Stew']);
  });

  it('locks the slider at 100 and searches with strict=true while strict is on', async () => {
    const fixture = await render();

    click(fixture, '.toggle-strict button');

    const req = expectSearch();
    const keys = req.request.params.keys().sort();
    expect(keys).toEqual(['strict', 'threshold']);
    expect(req.request.params.get('strict')).toBe('true');
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([PASTA]);
    fixture.detectChanges();

    // Strict mode means "fully covered only", so the bar is 100 and locked:
    // a movable slider under it could only contradict the mode itself.
    expect(text(root(fixture).querySelector('.threshold-value'))).toBe('100%');
    expect((nodes(fixture, '.threshold-slider input')[0] as HTMLInputElement).disabled).toBe(true);
    expect(text(root(fixture).querySelector('.strict-hint'))).toContain('slider is locked');
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);

    // Off again: the bar unlocks where strict left it, never reset to 70.
    click(fixture, '.toggle-strict button');
    const plain = expectSearch();
    const plainKeys = plain.request.params.keys();
    expect(plainKeys).toEqual(['threshold']);
    expect(plain.request.params.get('threshold')).toBe('0');
    plain.flush([PASTA, STEW, SALAD]);
    fixture.detectChanges();

    expect((nodes(fixture, '.threshold-slider input')[0] as HTMLInputElement).disabled).toBe(false);
    expect(text(root(fixture).querySelector('.threshold-value'))).toBe('100%');
    expect(root(fixture).querySelector('.strict-hint')).toBeNull();
  });

  it('searches with allergenSafe=true when the toggle is flipped', async () => {
    const fixture = await render();

    click(fixture, '.toggle-allergen-safe button');

    const req = expectSearch();
    // The allergen filter reads the user's exclusions, which only the server
    // holds, so it has to travel and the page re-searches.
    expect(req.request.params.get('allergenSafe')).toBe('true');
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([PASTA, STEW]);
    fixture.detectChanges();

    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara', 'Beef Stew']);
  });

  it('takes the saved diet as the page default and sends it on the first search', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    http.expectOne('/api/users/preferences').flush({
      filterMode: 'LAX',
      coverageThreshold: 70,
      diet: 'LOW_CARB',
    });

    // The first request already carries the stored diet: the grid waits for
    // the preferences rather than showing rows it would have to replace.
    const req = expectSearch();
    expect(req.request.params.get('diet')).toBe('LOW_CARB');
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([PASTA, STEW]);
    await fixture.whenStable();
    fixture.detectChanges();

    // And the select shows the stored choice instead of the balanced default.
    expect(text(root(fixture).querySelector('.diet-field mat-select'))).toContain('Low carb');
  });

  it('re-searches with the new diet when the select changes', async () => {
    const fixture = await render();

    // The balanced default means "every diet", so the first search stayed bare.
    await pickDiet(fixture, 'Low fat');

    const req = expectSearch();
    expect(req.request.params.get('diet')).toBe('LOW_FAT');
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([PASTA]);
    fixture.detectChanges();

    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('re-runs suggest with the new diet when the select changes in suggest mode', async () => {
    const fixture = await render();
    click(fixture, '.suggest-recipes');
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend')).flush([PASTA]);
    fixture.detectChanges();

    await pickDiet(fixture, 'High protein');

    // The diet belongs to the suggestion too: the page asks again rather than
    // leaving rows that the chosen diet would have filtered out.
    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.get('diet')).toBe('HIGH_PROTEIN');
    req.flush([STEW]);
    fixture.detectChanges();

    expect(cardNames(fixture)).toEqual(['Beef Stew']);
  });

  it('drops the answer of a search the user has already moved past', async () => {
    const fixture = await render([PASTA, STEW, SALAD]);

    // Two searches in flight: strict first, then allergen-safe.
    click(fixture, '.toggle-strict button');
    click(fixture, '.toggle-allergen-safe button');

    const [older, newer] = searchReqs(fixture);
    expect(older.request.params.has('strict')).toBe(true);
    expect(newer.request.params.has('allergenSafe')).toBe(true);

    // The strict answer arrives late: it must not draw its rows, or the page
    // would show the answer to the question that stopped being asked.
    older.flush([STEW]);
    fixture.detectChanges();
    expect(nodes(fixture, '.recipe-card').length).toBe(0);
    expect(root(fixture).querySelector('.recipes-status mat-spinner')).toBeTruthy();

    newer.flush([PASTA]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('shows the backend message when the search fails and retries on demand', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();

    http.expectOne('/api/users/preferences').flush({
      filterMode: 'LAX',
      coverageThreshold: 70,
      diet: 'BALANCED',
    });

    const failedReq = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    failedReq.flush(errorResponse(500, 'Recipe engine is down'), {
      status: 500,
      statusText: 'Server Error',
    });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.recipes-error-message'))).toBe(
      'Recipe engine is down',
    );
    // A search that failed must not look like a search with no matches.
    expect(root(fixture).querySelector('.recipes-empty')).toBeNull();

    click(fixture, 'button.retry-recipes');
    const retryReq = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    retryReq.flush([PASTA]);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.recipes-error')).toBeNull();
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('takes the user to the detail of the card they click', async () => {
    const fixture = await render();
    const router = TestBed.inject(Router);
    // `vi.spyOn` alone still runs the real router, and the test config has no
    // route for /recipes/1 — it would flood the run with an unhandled
    // rejection. Mocking the resolved value stubs the navigation out.
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const urlTree = router.createUrlTree(['/recipes', 1]);

    (nodes(fixture, '.recipe-card')[0] as HTMLElement).click();
    fixture.detectChanges();

    // The whole card is the click target, and it lands on the recipe's detail.
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate.mock.calls[0][0]).toEqual(urlTree);
    expect(navigate.mock.calls[0][1]).toMatchObject({ skipLocationChange: false });
  });

  it('initializes slider and strict from user preferences', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    http.expectOne('/api/users/preferences').flush({
      filterMode: 'STRICT',
      coverageThreshold: 85,
      diet: 'BALANCED',
    });
    const recipesReq = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    recipesReq.flush([PASTA]);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.threshold-value'))).toBe('100%');
    expect((nodes(fixture, '.threshold-slider input')[0] as HTMLInputElement).disabled).toBe(true);
  });

  it('falls back to defaults when profile fails to load', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    http.expectOne('/api/users/preferences').flush(errorResponse(500, 'Error'), {
      status: 500,
      statusText: 'Error',
    });
    const recipesReq = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    recipesReq.flush([PASTA, STEW, SALAD]);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.threshold-value'))).toBe('70%');
    expect((nodes(fixture, '.threshold-slider input')[0] as HTMLInputElement).disabled).toBe(false);
  });

  it('sends threshold=0 in every search request', async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipesPage);
    fixture.detectChanges();
    http.expectOne('/api/users/preferences').flush({
      filterMode: 'LAX',
      coverageThreshold: 70,
      diet: 'BALANCED',
    });
    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes'));
    expect(req.request.params.get('threshold')).toBe('0');
    req.flush([PASTA]);
    fixture.detectChanges();
    http.verify();
  });

  it('calls recommend with strict and allergenSafe when Suggest is clicked', async () => {
    const fixture = await render();
    click(fixture, '.suggest-recipes');

    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.get('strict')).toBeNull();
    expect(req.request.params.get('allergenSafe')).toBeNull();
    req.flush([PASTA]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('calls recommend with strict=true when Suggest clicked in strict mode', async () => {
    const fixture = await render();
    click(fixture, '.toggle-strict button');
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes')).flush([PASTA]);
    fixture.detectChanges();
    click(fixture, '.suggest-recipes');

    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.get('strict')).toBe('true');
    expect(req.request.params.get('allergenSafe')).toBeNull();
    req.flush([PASTA]);
    fixture.detectChanges();
  });

  it('calls recommend with allergenSafe=true when Suggest clicked', async () => {
    const fixture = await render();
    click(fixture, '.toggle-allergen-safe button');
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes')).flush([PASTA, STEW]);
    fixture.detectChanges();
    click(fixture, '.suggest-recipes');

    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.get('allergenSafe')).toBe('true');
    req.flush([STEW]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Beef Stew']);
  });

  it('shows differentiated empty state when suggest returns empty', async () => {
    const fixture = await render();
    click(fixture, '.suggest-recipes');
    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    req.flush([]);
    fixture.detectChanges();
    expect(text(root(fixture).querySelector('.recipes-empty h2'))).toBe('Nothing to suggest yet');
    expect(text(root(fixture).querySelector('.recipes-empty'))).toContain(
      'Nothing to suggest yet — stock some ingredients or nothing matched them.',
    );
  });

  it('shows suggest error with retry button', async () => {
    const fixture = await render();
    click(fixture, '.suggest-recipes');
    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    req.flush(errorResponse(500, 'Suggest failed'), { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(text(root(fixture).querySelector('.recipes-error-message'))).toBe('Suggest failed');
    click(fixture, '.retry-suggest');
    const retryReq = http.expectOne((req) =>
      req.urlWithParams.startsWith('/api/recipes/recommend'),
    );
    retryReq.flush([PASTA]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('allergenSafe toggle re-executes suggest when in suggest mode', async () => {
    const fixture = await render();
    click(fixture, '.suggest-recipes');
    http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend')).flush([PASTA]);
    fixture.detectChanges();
    click(fixture, '.toggle-allergen-safe button');
    const req = http.expectOne((req) => req.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.get('allergenSafe')).toBe('true');
    req.flush([STEW]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Beef Stew']);
  });

  it('searches the food catalog from the ingredient box and selects a hit', async () => {
    const fixture = await render();

    typeIngredient(fixture, 'cabb');

    const catalogReq = http.expectOne((req) => req.url === '/api/catalog/ingredients');
    expect(catalogReq.request.params.get('query')).toBe('cabb');
    catalogReq.flush([CABBAGE_HIT]);
    fixture.detectChanges();
    await fixture.whenStable();

    const option = document.querySelector('.mat-mdc-autocomplete-panel mat-option') as HTMLElement;
    expect(text(option)).toBe('Cabbage');

    option.click();
    fixture.detectChanges();
    await fixture.whenStable();

    // The hit's fdcId is not the recipe endpoint's ingredientId; only the detail
    // read exposes the internal id (and materializes the food).
    const detailReq = http.expectOne('/api/catalog/ingredients/170000');
    detailReq.flush(CABBAGE);
    fixture.detectChanges();

    const search = expectSearch();
    expect(search.request.params.get('ingredientId')).toBe('12');
    search.flush([CABBAGE_DISH]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Cabbage Roll']);
  });

  it('sends ingredientId together with name, strict, threshold and allergenSafe', async () => {
    const fixture = await render();
    await pickIngredient(fixture, CABBAGE_HIT, CABBAGE);

    // The pick alone narrows the search: no name, no toggles, just the ingredient.
    const selected = expectSearch();
    expect(selected.request.params.keys().sort()).toEqual(['ingredientId', 'threshold']);
    expect(selected.request.params.get('ingredientId')).toBe('12');
    selected.flush([CABBAGE_DISH]);
    fixture.detectChanges();

    // The name box is independent and adds its filter on its own debounce.
    const nameInput = nodes(fixture, '.search-field input')[0] as HTMLInputElement;
    vi.useFakeTimers();
    try {
      nameInput.value = 'roll';
      nameInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      vi.advanceTimersByTime(300);
    } finally {
      vi.useRealTimers();
    }
    fixture.detectChanges();

    const named = expectSearch();
    expect(named.request.params.get('ingredientId')).toBe('12');
    expect(named.request.params.get('name')).toBe('roll');
    named.flush([CABBAGE_DISH]);
    fixture.detectChanges();

    // Strict travels beside the ingredient, not instead of it.
    click(fixture, '.toggle-strict button');
    const strictReq = expectSearch();
    expect(strictReq.request.params.keys().sort()).toEqual([
      'ingredientId',
      'name',
      'strict',
      'threshold',
    ]);
    strictReq.flush([CABBAGE_DISH]);
    fixture.detectChanges();

    // Allergen-safe too: every current filter rides along.
    click(fixture, '.toggle-allergen-safe button');
    const allergenReq = expectSearch();
    expect(allergenReq.request.params.keys().sort()).toEqual([
      'allergenSafe',
      'ingredientId',
      'name',
      'strict',
      'threshold',
    ]);
    allergenReq.flush([CABBAGE_DISH]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Cabbage Roll']);
  });

  it('clears the ingredient with a single search that drops ingredientId', async () => {
    const fixture = await render();
    await pickIngredient(fixture, CABBAGE_HIT, CABBAGE);
    expectSearch().flush([CABBAGE_DISH]);
    fixture.detectChanges();

    click(fixture, '.clear-ingredient');

    // One clear, one request: the control reset is silent so the reload below is
    // not doubled by a `valueChanges` search.
    const searches = searchReqs(fixture);
    expect(searches.length).toBe(1);
    expect(searches[0].request.params.has('ingredientId')).toBe(false);
    expect(searches[0].request.params.get('threshold')).toBe('0');
    searches[0].flush([PASTA, STEW, SALAD]);
    fixture.detectChanges();

    expect(cardNames(fixture).length).toBeGreaterThan(0);
  });

  it('shows a stale-ingredient error on a 404 instead of an empty grid', async () => {
    const fixture = await render();
    await pickIngredient(fixture, CABBAGE_HIT, CABBAGE);
    expectSearch().flush([CABBAGE_DISH]);
    fixture.detectChanges();

    click(fixture, '.toggle-strict button');
    expectSearch().flush(errorResponse(404, 'Ingredient 12 not found'), {
      status: 404,
      statusText: 'Not Found',
    });
    fixture.detectChanges();

    // A 404 about the ingredient is its own error state, not a grid that could
    // be mistaken for "nothing matched"; its repair is clearing, not retrying.
    expect(root(fixture).querySelector('.recipes-error.ingredient-stale')).toBeTruthy();
    expect(text(root(fixture).querySelector('.recipes-error-message'))).toBe(
      'Ingredient 12 not found',
    );
    expect(root(fixture).querySelector('.recipes-empty')).toBeNull();
    expect(root(fixture).querySelector('button.retry-recipes')).toBeNull();
    expect(text(root(fixture).querySelector('.recipes-error-hint'))).toContain(
      'no longer in the catalog',
    );

    click(fixture, '.clear-stale-ingredient');
    const repair = expectSearch();
    expect(repair.request.params.has('ingredientId')).toBe(false);
    expect(repair.request.params.has('strict')).toBe(true);
    repair.flush([PASTA]);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.recipes-error')).toBeNull();
    expect(cardNames(fixture)).toEqual(['Spaghetti alla Carbonara']);
  });

  it('does not send the selected ingredient to the suggest endpoint', async () => {
    const fixture = await render();
    await pickIngredient(fixture, CABBAGE_HIT, CABBAGE);
    expectSearch().flush([CABBAGE_DISH]);
    fixture.detectChanges();

    click(fixture, '.suggest-recipes');

    // Suggest is driven by the pantry, not by the ingredient box: the filter
    // stays behind so it cannot narrow a recommendation the user did not scope.
    const req = http.expectOne((r) => r.urlWithParams.startsWith('/api/recipes/recommend'));
    expect(req.request.params.has('ingredientId')).toBe(false);
    expect(req.request.params.keys()).toEqual([]);
    req.flush([CABBAGE_DISH]);
    fixture.detectChanges();
    expect(cardNames(fixture)).toEqual(['Cabbage Roll']);
  });

  it('shows a catalog failure on the ingredient box and offers no option', async () => {
    const fixture = await render();
    typeIngredient(fixture, 'cabb');

    const catalogReq = http.expectOne((req) => req.url === '/api/catalog/ingredients');
    catalogReq.flush(errorResponse(502, 'USDA rejected the request'), {
      status: 502,
      statusText: 'Bad Gateway',
    });
    fixture.detectChanges();

    // The failure belongs to the box, not to the grid: it renders as the
    // field's error and no option is offered under a search that answered
    // with an error instead of foods.
    expect(text(root(fixture).querySelector('.ingredient-field mat-error'))).toBe(
      'USDA rejected the request',
    );
    expect(document.querySelector('.mat-mdc-autocomplete-panel mat-option')).toBeNull();
    // A failed catalog search is not a failed recipe search: the grid is intact.
    expect(root(fixture).querySelector('.recipes-error')).toBeNull();
  });

  it('offers the way into the create page from the heading', async () => {
    const fixture = await render();

    // Creating a recipe is an action inside the area, so it lives on the
    // heading rather than in the shell navigation.
    const link = root(fixture).querySelector('a.create-recipe');
    expect(link?.getAttribute('href')).toBe(
      TestBed.inject(Router).createUrlTree(['/recipes/new']).toString(),
    );
    expect(text(link)).toContain('Create recipe');
  });
});
