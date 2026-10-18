import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { CookingSession } from '../../core/cooking/cooking.models';
import { RecipeSummary } from '../../core/recipes/recipes.models';
import { HistoryPage } from './history-page';

/** The same recipe cooked twice: one session still live, one finished. */
const PASTA_SUMMARIES: RecipeSummary[] = [
  {
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
    allergenCodes: [],
    favorite: false,
    saved: false,
    owned: false,
    published: false,
  },
  {
    // No image: its rows must fall back to the icon, not a broken frame.
    id: 2,
    source: 'USER',
    externalId: null,
    name: 'Beef Stew',
    category: 'Main Course',
    area: '',
    imageUrl: null,
    servings: null,
    matchScore: null,
    missingCount: null,
    allergenCodes: [],
    favorite: false,
    saved: false,
    owned: true,
    published: false,
  },
];

/** Live, paused, completed and abandoned — one of each, most recent first. */
const SESSIONS: CookingSession[] = [
  {
    id: 4,
    recipeId: 1,
    status: 'IN_PROGRESS',
    startedAt: '2026-10-12T10:00:00Z',
    completedAt: null,
    deduction: null,
  },
  {
    id: 3,
    recipeId: 2,
    status: 'PAUSED',
    startedAt: '2026-10-12T09:00:00Z',
    completedAt: null,
    deduction: null,
  },
  {
    id: 2,
    recipeId: 1,
    status: 'COMPLETED',
    startedAt: '2026-10-11T10:00:00Z',
    completedAt: '2026-10-11T11:00:00Z',
    deduction: null,
  },
  {
    id: 1,
    recipeId: 2,
    status: 'ABANDONED',
    startedAt: '2026-10-10T10:00:00Z',
    completedAt: '2026-10-10T10:30:00Z',
    deduction: null,
  },
];

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-12T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/cooking/sessions',
});

describe('HistoryPage', () => {
  let http: HttpTestingController;

  const configure = async () => {
    await TestBed.configureTestingModule({
      imports: [HistoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Creates the page and answers both initial reads. */
  const render = async (sessions: CookingSession[] = SESSIONS) => {
    await configure();
    const fixture = TestBed.createComponent(HistoryPage);
    fixture.detectChanges();
    http.expectOne('/api/cooking/sessions').flush(sessions);
    // The name lookup is separate and best-effort: it is answered, never
    // assumed by whichever test cares about names.
    if (sessions.length > 0) {
      http.expectOne('/api/recipes').flush(PASTA_SUMMARIES);
    }
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<HistoryPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<HistoryPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<HistoryPage>, selector: string, index = 0): void => {
    (nodes(fixture, selector)[index] as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('splits the list into live sessions and past ones, keeping the server order', async () => {
    const fixture = await render();

    // The two active sessions lead, in the order they arrived; the finished
    // and abandoned ones follow.
    expect(nodes(fixture, '.history-group mat-card-title').map((node) => text(node))).toEqual([
      'In progress',
      'Past sessions',
    ]);
    expect(nodes(fixture, '.history-group .session-name').map((node) => text(node))).toEqual([
      'Spaghetti alla Carbonara',
      'Beef Stew',
      'Spaghetti alla Carbonara',
      'Beef Stew',
    ]);
    // The status labels come from the page's own table, not from the raw codes.
    expect(nodes(fixture, '.session-status').map((node) => text(node))).toEqual([
      'In progress',
      'Paused',
      'Completed',
      'Abandoned',
    ]);
    // Only the live group gets the tinted badge; the past keeps the neutral one.
    expect(nodes(fixture, '.status-active').length).toBe(2);
  });

  it('links every row to its own session', async () => {
    const fixture = await render();
    const router = TestBed.inject(Router);

    // Live rows are the whole link; past rows wrap theirs in a link so the
    // "Cook again" button stays outside it.
    const liveLinks = nodes(fixture, 'a.session-row');
    const historyLinks = nodes(fixture, 'a.session-link');
    expect(liveLinks.length + historyLinks.length).toBe(4);
    expect(liveLinks[0].getAttribute('href')).toBe(
      router.createUrlTree(['/cooking', 4]).toString(),
    );
  });

  it('names a recipe the catalog does not know as its id and falls back to the icon', async () => {
    const fixture = await render([{ ...SESSIONS[0], recipeId: 99 }]);

    expect(text(nodes(fixture, '.session-name')[0])).toBe('Recipe #99');
    // No catalog summary means no image: the row keeps its thumbnail slot.
    expect(nodes(fixture, '.session-thumb-fallback').length).toBe(1);
    expect(root(fixture).querySelector('.session-thumb:not(.session-thumb-fallback)')).toBeNull();
  });

  it('shows the recipe image in both groups and the icon when there is none', async () => {
    const fixture = await render();

    // Carbonara has an image (live + past rows); Beef Stew has none, so its
    // two rows carry the fallback icon instead of a broken frame.
    const images = nodes(fixture, 'img.session-thumb') as HTMLImageElement[];
    expect(images.length).toBe(2);
    expect(images.map((img) => img.getAttribute('src'))).toEqual([
      PASTA_SUMMARIES[0].imageUrl,
      PASTA_SUMMARIES[0].imageUrl,
    ]);
    expect(images.map((img) => img.getAttribute('alt'))).toEqual([
      'Spaghetti alla Carbonara',
      'Spaghetti alla Carbonara',
    ]);
    expect(nodes(fixture, '.session-thumb-fallback').length).toBe(2);
  });

  it('invites the user to browse recipes when there are no sessions', async () => {
    const fixture = await render([]);

    expect(text(root(fixture).querySelector('.history-empty h2'))).toBe(
      "You haven't cooked anything yet",
    );
    const browse = root(fixture).querySelector('a.browse-recipes');
    expect(browse?.getAttribute('href')).toBe(
      TestBed.inject(Router).createUrlTree(['/recipes']).toString(),
    );
  });

  it('shows the backend message when the load fails and retries on demand', async () => {
    await configure();
    const fixture = TestBed.createComponent(HistoryPage);
    fixture.detectChanges();

    http
      .expectOne('/api/cooking/sessions')
      .flush(errorResponse(500, 'Sessions are down'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.history-error-message'))).toBe('Sessions are down');
    expect(root(fixture).querySelector('.history-empty')).toBeNull();

    click(fixture, 'button.retry-history');
    http.expectOne('/api/cooking/sessions').flush(SESSIONS);
    http.expectOne('/api/recipes').flush(PASTA_SUMMARIES);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.history-error')).toBeNull();
    expect(nodes(fixture, '.session-row').length).toBe(4);
  });

  it('starts a fresh session from a previous one and opens it', async () => {
    const fixture = await render();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    click(fixture, 'button.cook-again', 0);

    // "Cook again" is on the past rows only, so it is the completed one
    // (same recipe as the first live session: Spaghetti alla Carbonara).
    const req = http.expectOne('/api/cooking/sessions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ recipeId: 1 });
    req.flush({
      id: 5,
      recipeId: 1,
      status: 'IN_PROGRESS',
      startedAt: '2026-10-12T12:00:00Z',
      completedAt: null,
      deduction: null,
    });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/cooking', 5]);
  });

  it('keeps the list usable when starting again is refused', async () => {
    const fixture = await render();

    click(fixture, 'button.cook-again', 0);
    http
      .expectOne('/api/cooking/sessions')
      .flush(errorResponse(404, 'Recipe 1 not found'), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.history-action-error'))).toBe('Recipe 1 not found');
    // Nothing navigated and the rows are still all there.
    expect(nodes(fixture, '.session-row').length).toBe(4);
  });
});
