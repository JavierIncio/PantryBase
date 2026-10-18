import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter, Router } from '@angular/router';
import { RecipeDetail, RecipeSummary } from '../../core/recipes/recipes.models';
import { CollectionPage } from './collection-page';

/** A recipe with an image, as `GET /api/recipes/{id}` answers it. */
const CARBONARA: RecipeDetail = {
  id: 1,
  source: 'THE_MEAL_DB',
  externalId: '52771',
  name: 'Spaghetti alla Carbonara',
  category: 'Pasta',
  area: 'Italian',
  instructions: 'Boil the pasta.\nMix the eggs.',
  imageUrl: 'https://www.themealdb.com/images/media/meals/llcbn01574260722.jpg',
  videoUrl: null,
  tags: ['Pasta'],
  servings: 4,
  sourceUrl: null,
  ingredients: [],
  nutrients: null,
  allergenCodes: ['EGG', 'MILK'],
  matchScore: 1,
  coverage: [],
  favorite: true,
  saved: false,
  owned: false,
  published: false,
};

/** A recipe without an image, so its tile has to fall back to an icon. */
const STEW: RecipeDetail = {
  ...CARBONARA,
  id: 2,
  source: 'USER',
  externalId: null,
  name: 'Beef Stew',
  category: 'Main Course',
  area: '',
  imageUrl: null,
  favorite: false,
  saved: true,
  owned: true,
};

/** A summary of the caller's own recipe, as `GET /api/recipes/mine` answers it. */
const OWNED_CARBONARA: RecipeSummary = {
  id: 11,
  source: 'USER',
  externalId: null,
  name: 'My Carbonara',
  category: 'Pasta',
  area: 'Italian',
  imageUrl: 'https://example.com/my-carbonara.jpg',
  servings: 4,
  matchScore: null,
  missingCount: null,
  allergenCodes: ['EGG', 'MILK'],
  favorite: false,
  saved: false,
  owned: true,
  published: true,
};

/** An own recipe without an image, so the Created tab falls back to the icon. */
const OWNED_STEW: RecipeSummary = {
  ...OWNED_CARBONARA,
  id: 12,
  name: 'Beef Stew',
  category: 'Main Course',
  area: '',
  imageUrl: null,
  published: false,
};

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-16T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/favorites',
});

describe('CollectionPage', () => {
  let http: HttpTestingController;

  const configure = async () => {
    await TestBed.configureTestingModule({
      imports: [CollectionPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // A hidden tab attaches its body on the next transition. jsdom never
        // fires one, and Material caches the reduced-motion preference in a
        // module-level variable shared by every test file, so that fallback is
        // not reliable here. Disabling animations by DI makes the body attach
        // synchronously, which is exactly the state the tab switcher produces.
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Creates the page and answers the three lists; each detail is left to the caller. */
  const render = async (
    lists: { favorites?: number[]; saved?: number[]; mine?: RecipeSummary[] } = {},
  ) => {
    await configure();
    const fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();
    http.expectOne('/api/favorites').flush(lists.favorites ?? []);
    http.expectOne('/api/saved').flush(lists.saved ?? []);
    http.expectOne('/api/recipes/mine').flush(lists.mine ?? []);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<CollectionPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<CollectionPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<CollectionPage>, selector: string, index = 0): void => {
    (nodes(fixture, selector)[index] as HTMLElement).click();
    fixture.detectChanges();
  };

  /** Selects a tab by its label, the way a user reaches the hidden list. */
  const activateTab = (fixture: ComponentFixture<CollectionPage>, label: string): void => {
    const tab = nodes(fixture, '[role="tab"]').find((node) => text(node) === label);
    (tab as HTMLElement | undefined)?.click();
    fixture.detectChanges();
    fixture.detectChanges();
  };

  /** The tile titles of the active tab, in the order they render. */
  const titles = (fixture: ComponentFixture<CollectionPage>): string[] =>
    nodes(fixture, '.collection-card mat-card-title').map((node) => text(node));

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('resolves the favorite ids into tiles, keeping the API order', async () => {
    const fixture = await render({ favorites: [CARBONARA.id, STEW.id] });

    // Each id is read individually, so the grid waits for the whole batch.
    expect(root(fixture).querySelector('.collection-status mat-spinner')).toBeTruthy();
    http.expectOne('/api/recipes/1').flush(CARBONARA);
    http.expectOne('/api/recipes/2').flush(STEW);
    fixture.detectChanges();

    expect(titles(fixture)).toEqual(['Spaghetti alla Carbonara', 'Beef Stew']);
    expect(nodes(fixture, '.collection-card img').map((img) => img.getAttribute('alt'))).toEqual([
      'Spaghetti alla Carbonara',
    ]);
    // The card without an image degrades to an icon rather than a broken frame.
    expect(nodes(fixture, '.collection-image-fallback').length).toBe(1);
  });

  it('invites the user to favorite a recipe when there are none', async () => {
    const fixture = await render({ favorites: [] });

    expect(text(root(fixture).querySelector('.collection-empty h2'))).toBe(
      'No favorite recipes yet',
    );
    expect(root(fixture).querySelector('.collection-status')).toBeNull();
    expect(root(fixture).querySelector('.collection-empty a.browse-recipes')).toBeTruthy();
  });

  it('shows the backend message when the favorite load fails and retries on demand', async () => {
    await configure();
    const fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();

    http
      .expectOne('/api/favorites')
      .flush(errorResponse(500, 'Favorites are down'), { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/saved').flush([]);
    http.expectOne('/api/recipes/mine').flush([]);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.collection-error-message'))).toBe(
      'Favorites are down',
    );
    // A failed load is not an empty list.
    expect(root(fixture).querySelector('.collection-empty')).toBeNull();

    click(fixture, 'button.retry-collection');
    http.expectOne('/api/favorites').flush([CARBONARA.id]);
    http.expectOne('/api/recipes/1').flush(CARBONARA);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.collection-error')).toBeNull();
    expect(nodes(fixture, '.collection-card').length).toBe(1);
  });

  it('skips a favorite id whose recipe no longer exists instead of a broken tile', async () => {
    const fixture = await render({ favorites: [CARBONARA.id, 2, 3] });

    http.expectOne('/api/recipes/1').flush(CARBONARA);
    http
      .expectOne('/api/recipes/2')
      .flush(errorResponse(404, 'Recipe 2 not found'), { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/recipes/3').flush({ ...STEW, id: 3, name: 'Side Salad' });
    fixture.detectChanges();

    // The orphan is dropped; the surviving tiles keep the id order.
    expect(titles(fixture)).toEqual(['Spaghetti alla Carbonara', 'Side Salad']);
    expect(root(fixture).querySelector('.collection-error')).toBeNull();
  });

  it('loads the saved tab in the background and renders its own grid when activated', async () => {
    const fixture = await render({ saved: [CARBONARA.id, STEW.id] });

    http.expectOne('/api/recipes/1').flush(CARBONARA);
    http.expectOne('/api/recipes/2').flush(STEW);
    activateTab(fixture, 'Saved');

    expect(titles(fixture)).toEqual(['Spaghetti alla Carbonara', 'Beef Stew']);
  });

  it('shows the saved tab its own empty state when nothing was saved', async () => {
    const fixture = await render({ saved: [] });

    activateTab(fixture, 'Saved');

    expect(text(root(fixture).querySelector('.collection-empty h2'))).toBe('No saved recipes yet');
  });

  it('keeps the saved tab error independent from the favorites tab', async () => {
    await configure();
    const fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();

    http.expectOne('/api/favorites').flush([]);
    http
      .expectOne('/api/saved')
      .flush(errorResponse(500, 'Saved list is down'), { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/recipes/mine').flush([]);
    fixture.detectChanges();

    // The active tab never consumed the failure meant for the hidden one.
    expect(root(fixture).querySelector('.collection-error')).toBeNull();
    expect(text(root(fixture).querySelector('.collection-empty h2'))).toBe(
      'No favorite recipes yet',
    );

    activateTab(fixture, 'Saved');

    expect(text(root(fixture).querySelector('.collection-error-message'))).toBe(
      'Saved list is down',
    );
  });

  it('renders the Created tab from /api/recipes/mine with its own rows', async () => {
    const fixture = await render({ mine: [OWNED_CARBONARA, OWNED_STEW] });

    // The endpoint answers full summaries, so the grid renders with no
    // per-id detail read behind it.
    activateTab(fixture, 'Created');

    expect(titles(fixture)).toEqual(['My Carbonara', 'Beef Stew']);
    const images = nodes(fixture, '.collection-card img') as HTMLImageElement[];
    expect(images.map((img) => img.getAttribute('src'))).toEqual([OWNED_CARBONARA.imageUrl]);
    // The own recipe without an image degrades to the icon, like every tab.
    expect(nodes(fixture, '.collection-image-fallback').length).toBe(1);
  });

  it('shows the Created tab its own empty state when nothing was created', async () => {
    const fixture = await render({ mine: [] });

    activateTab(fixture, 'Created');

    expect(text(root(fixture).querySelector('.collection-empty h2'))).toBe(
      'No created recipes yet',
    );
  });

  it('keeps the Created tab error independent from the other tabs and retries on demand', async () => {
    await configure();
    const fixture = TestBed.createComponent(CollectionPage);
    fixture.detectChanges();

    http.expectOne('/api/favorites').flush([]);
    http.expectOne('/api/saved').flush([]);
    http.expectOne('/api/recipes/mine').flush(errorResponse(500, 'Own recipes are down'), {
      status: 500,
      statusText: 'Server Error',
    });
    fixture.detectChanges();

    // The active tab never consumed the failure meant for the hidden one.
    expect(root(fixture).querySelector('.collection-error')).toBeNull();
    expect(text(root(fixture).querySelector('.collection-empty h2'))).toBe(
      'No favorite recipes yet',
    );

    activateTab(fixture, 'Created');

    expect(text(root(fixture).querySelector('.collection-error-message'))).toBe(
      'Own recipes are down',
    );

    click(fixture, 'button.retry-collection');
    http.expectOne('/api/recipes/mine').flush([OWNED_STEW]);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.collection-error')).toBeNull();
    expect(titles(fixture)).toEqual(['Beef Stew']);
  });

  it('navigates to the recipe detail when a tile is clicked', async () => {
    const fixture = await render({ favorites: [CARBONARA.id, STEW.id] });
    http.expectOne('/api/recipes/1').flush(CARBONARA);
    http.expectOne('/api/recipes/2').flush(STEW);
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    // The test config has no /recipes/:id route, so the real navigation would
    // reject; stubbing the resolved value keeps the href click honest.
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    click(fixture, '.collection-card');

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate.mock.calls[0][0]).toEqual(router.createUrlTree(['/recipes', 1]));
  });
});
