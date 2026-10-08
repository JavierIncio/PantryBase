import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { Unit } from '../../core/catalog/catalog.models';
import { RecipeDetail } from '../../core/recipes/recipes.models';
import { RecipeDetailPage } from './recipe-detail-page';

/** The three units the fixture lines use; the endpoint has twelve. */
const UNITS: Unit[] = [
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'ML', name: 'Milliliter', category: 'VOLUME', canonical: true },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

/**
 * Carbonara with three ingredient lines and a coverage list that is deliberately
 * NOT in the recipe's order: the join by ingredientId must re-establish it.
 * Flour has no coverage row at all; Salt is partially covered.
 */
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
  ingredients: [
    {
      ingredientId: 10,
      ingredientName: 'Eggs',
      ingredientCategory: 'Dairy and Egg Products',
      originalAmount: 2,
      originalUnitCode: 'UNIT',
      canonicalAmount: 2,
      canonicalUnitCode: 'UNIT',
      canonicalUnitCategory: 'COUNT',
      position: 1,
    },
    {
      ingredientId: 11,
      ingredientName: 'Flour',
      ingredientCategory: 'Grain Products',
      originalAmount: 200,
      originalUnitCode: 'GRAM',
      canonicalAmount: 200,
      canonicalUnitCode: 'GRAM',
      canonicalUnitCategory: 'WEIGHT',
      position: 2,
    },
    {
      ingredientId: 12,
      ingredientName: 'Salt',
      ingredientCategory: 'Spices',
      originalAmount: 1,
      originalUnitCode: 'PINCH',
      canonicalAmount: 1,
      canonicalUnitCode: 'GRAM',
      canonicalUnitCategory: 'WEIGHT',
      position: 3,
    },
  ],
  coverage: [
    {
      ingredientId: 12,
      ingredientName: 'Salt',
      neededAmount: 1,
      neededUnitCode: 'GRAM',
      availableAmount: 0.5,
      availableUnitCode: 'GRAM',
      fullyCovered: false,
    },
    {
      ingredientId: 10,
      ingredientName: 'Eggs',
      neededAmount: 2,
      neededUnitCode: 'UNIT',
      availableAmount: 3,
      availableUnitCode: 'UNIT',
      fullyCovered: true,
    },
  ],
  nutrients: { energyKcal: 500, proteinG: 20, fatG: 11.5, carbsG: 40 },
  allergenCodes: ['EGG', 'MILK'],
  matchScore: 1,
};

/**
 * A local recipe with no coverage, no image, no source page, no allergens, and
 * nutrients with two values the provider never measured.
 */
const OWN_RECIPE: RecipeDetail = {
  ...DETAIL,
  id: 2,
  source: 'USER',
  externalId: null,
  name: 'Beef Stew',
  category: 'Main Course',
  area: '',
  instructions: 'Simmer for two hours.',
  imageUrl: null,
  videoUrl: 'https://example.com/videos/stew.mp4',
  sourceUrl: null,
  servings: 1,
  tags: [],
  nutrients: { energyKcal: null, proteinG: 8, fatG: null, carbsG: 30 },
  allergenCodes: [],
  matchScore: null,
};

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-08T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/recipes/1',
});

describe('RecipeDetailPage', () => {
  let http: HttpTestingController;
  let routeId = '1';

  const configure = async (id: string) => {
    routeId = id;
    await TestBed.configureTestingModule({
      imports: [RecipeDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Answers the unit catalog every page asks for on init. */
  const flushUnits = (): void => {
    http.expectOne('/api/units').flush(UNITS);
  };

  /** Creates the page and answers both initial reads. */
  const render = async (recipe: RecipeDetail = DETAIL, id = '1') => {
    await configure(id);
    const fixture = TestBed.createComponent(RecipeDetailPage);
    fixture.detectChanges();
    flushUnits();
    http.expectOne(`/api/recipes/${id}`).flush(recipe);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<RecipeDetailPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<RecipeDetailPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  afterEach(() => {
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('renders the header, allergies and source link of a provider recipe', async () => {
    const fixture = await render();
    const h1 = root(fixture).querySelector('.detail-heading h1');

    expect(text(h1)).toBe('Spaghetti alla Carbonara');
    expect(text(root(fixture).querySelector('.detail-origin-text'))).toBe('Pasta · Italian');
    // The percentage is the server's score, written out verbatim.
    expect(text(root(fixture).querySelector('.coverage-badge'))).toBe('100% covered');
    expect(nodes(fixture, '.detail-allergens mat-chip').map((node) => text(node))).toEqual([
      'EGG',
      'MILK',
    ]);
    // Only the source page is known for this recipe, so only that link is drawn.
    const sourceLink = root(fixture).querySelector('.recipe-source-link');
    expect(sourceLink?.getAttribute('href')).toBe('https://www.themealdb.com/meal/52771');
    expect(text(sourceLink)).toContain('See original recipe');
    expect(nodes(fixture, '.recipe-source-link').length).toBe(1);
    // The instructions keep the provider's line breaks.
    expect(text(root(fixture).querySelector('.instructions-text'))).toBe(
      'Boil the pasta. Fry the bacon.',
    );
    expect((root(fixture).querySelector('.detail-image') as HTMLImageElement).alt).toBe(
      'Spaghetti alla Carbonara',
    );
  });

  it('joins coverage back to the ingredient lines in the recipe order', async () => {
    const fixture = await render();

    // Coverage is answered last-two-first in the fixture; the list must still
    // follow the recipe's own order.
    expect(nodes(fixture, '.ingredient-name').map((node) => text(node))).toEqual([
      'Eggs',
      'Flour',
      'Salt',
    ]);
    expect(nodes(fixture, '.ingredient-state-icon').map((node) => text(node))).toEqual([
      'check_circle',
      'help_outline',
      'warning',
    ]);
    // Each amount names its own unit; covered lines carry a check, lines the
    // pantry cannot place carry an explicit gap instead of a blank.
    expect(nodes(fixture, '.ingredient-amount').map((node) => text(node))).toEqual([
      'need 2 Unit · have 3 Unit',
      'need 200 Gram · have not in pantry',
      'need 1 Gram · have 0.5 Gram',
    ]);
    expect(text(root(fixture).querySelector('.ingredient-gap'))).toBe('0.5 short');
    expect(nodes(fixture, '.ingredient-row.ingredient-covered').length).toBe(1);
    expect(text(nodes(fixture, '.ingredient-row.ingredient-covered')[0])).toContain('Eggs');
  });

  it('draws only the nutrients the provider actually measured', async () => {
    const fixture = await render(OWN_RECIPE);

    // An absent figure is an em dash, never a zero: no measured energy is not
    // the same as a zero-calorie recipe.
    expect(nodes(fixture, '.nutrient-value').map((node) => text(node))).toEqual([
      '—',
      '8',
      '—',
      '30',
    ]);
  });

  it('leaves the nutrients card out when the provider computed none', async () => {
    const fixture = await render({ ...DETAIL, nutrients: null });

    expect(root(fixture).querySelector('.nutrients-card')).toBeNull();
  });

  it('falls back to an icon and single-serving wording for a local recipe', async () => {
    const fixture = await render(OWN_RECIPE);

    // No image to break and no half-empty origin line: the area is dropped
    // along with its separator.
    expect(nodes(fixture, '.detail-image-fallback mat-icon').length).toBe(1);
    expect(text(root(fixture).querySelector('.detail-origin-text'))).toBe('Main Course');
    expect(text(root(fixture).querySelector('.coverage-badge'))).toBe('No coverage computed');
    // The rest of the badge row: the origin, and the singular serving count.
    const badges = text(root(fixture).querySelector('.detail-badges'));
    expect(badges).toContain('Own');
    // Singular serves, and the video is the only fan-out link there is.
    expect(badges).toContain('1 serving');
    expect(text(root(fixture).querySelector('.recipe-source-link'))).toContain('Watch video');
    expect(root(fixture).querySelector('.recipe-source-link')?.getAttribute('href')).toBe(
      'https://example.com/videos/stew.mp4',
    );
  });

  it('treats an id the URL cannot hold as a not-found without asking the server', async () => {
    await configure('abc');
    const fixture = TestBed.createComponent(RecipeDetailPage);
    fixture.detectChanges();
    flushUnits();

    expect(text(root(fixture).querySelector('.detail-not-found h2'))).toBe('Recipe not found');
    // A route that cannot exist spends no request on being asked.
    expect(http.match(() => true)).toEqual([]);
  });

  it('shows the not-found card for a 404 and links back to the grid', async () => {
    await configure('999');
    const fixture = TestBed.createComponent(RecipeDetailPage);
    fixture.detectChanges();
    flushUnits();

    http
      .expectOne('/api/recipes/999')
      .flush(errorResponse(404, 'Recipe 999 not found'), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.detail-not-found h2'))).toBe('Recipe not found');
    expect(text(root(fixture).querySelector('.detail-not-found'))).toContain('no longer available');
    expect(root(fixture).querySelector('.detail-error')).toBeNull();
    // The way back is wired to the grid, exactly like the bottom of a recipe.
    const back = root(fixture).querySelector('.detail-not-found .back-to-recipes');
    expect(back?.getAttribute('href')).toBe(
      TestBed.inject(Router).createUrlTree(['/recipes']).toString(),
    );
  });

  it('shows the backend message when the read fails and retries on demand', async () => {
    await configure('1');
    const fixture = TestBed.createComponent(RecipeDetailPage);
    fixture.detectChanges();
    flushUnits();

    http
      .expectOne('/api/recipes/1')
      .flush(errorResponse(500, 'Recipe engine is down'), {
        status: 500,
        statusText: 'Server Error',
      });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.detail-error-message'))).toBe(
      'Recipe engine is down',
    );
    // A recipe that failed to load must not look like a recipe that is gone.
    expect(root(fixture).querySelector('.detail-not-found')).toBeNull();

    (nodes(fixture, 'button.retry-detail')[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    // The unit catalog was already answered, so a retry asks only for the recipe.
    http.expectOne('/api/recipes/1').flush(DETAIL);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.detail-error')).toBeNull();
    expect(text(root(fixture).querySelector('.detail-heading h1'))).toBe(
      'Spaghetti alla Carbonara',
    );
  });

  it('wires the actions back to the grid and on to cooking', async () => {
    const fixture = await render();
    const router = TestBed.inject(Router);

    // The same pair of ways out works from a loaded recipe; the 404 card uses
    // the back one alone.
    expect(fixture.nativeElement.querySelector('a.back-to-recipes').getAttribute('href')).toBe(
      router.createUrlTree(['/recipes']).toString(),
    );
    expect(fixture.nativeElement.querySelector('a.start-cooking').getAttribute('href')).toBe(
      router.createUrlTree(['/cooking']).toString(),
    );
  });
});
