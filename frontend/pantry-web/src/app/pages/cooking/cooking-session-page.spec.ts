import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Subject } from 'rxjs';
import { Unit } from '../../core/catalog/catalog.models';
import { CompletionDeduction, CookingSession } from '../../core/cooking/cooking.models';
import { RecipeDetail } from '../../core/recipes/recipes.models';
import { CookingSessionPage } from './cooking-session-page';
import { ShortageDialog } from './shortage-dialog';

const UNITS: Unit[] = [
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

const LIVE: CookingSession = {
  id: 4,
  recipeId: 1,
  status: 'IN_PROGRESS',
  startedAt: '2026-10-12T10:00:00Z',
  completedAt: null,
  deduction: null,
};

const COMPLETED: CookingSession = {
  id: 4,
  recipeId: 1,
  status: 'COMPLETED',
  startedAt: '2026-10-12T10:00:00Z',
  completedAt: '2026-10-12T11:00:00Z',
  deduction: {
    hasShortage: true,
    lines: [
      {
        ingredientId: 10,
        ingredientName: 'Eggs',
        unitCode: 'UNIT',
        needed: 3,
        available: 1,
        deducted: 1,
        remaining: 0,
      },
      {
        ingredientId: 11,
        ingredientName: 'Bacon',
        unitCode: 'GRAM',
        needed: 150,
        available: 200,
        deducted: 150,
        remaining: 50,
      },
    ],
  },
};

/** The plan a completion is refused with: exactly what the dialog shows. */
const SHORTAGE: CompletionDeduction = {
  hasShortage: true,
  lines: [
    {
      ingredientId: 10,
      ingredientName: 'Eggs',
      unitCode: 'UNIT',
      needed: 3,
      available: 1,
      deducted: 1,
      remaining: 0,
    },
  ],
};

const RECIPE: RecipeDetail = {
  id: 1,
  source: 'THE_MEAL_DB',
  externalId: '52771',
  name: 'Spaghetti alla Carbonara',
  category: 'Pasta',
  area: 'Italian',
  instructions: 'Boil the pasta.\nFry the bacon.\nMix and serve.',
  imageUrl: null,
  videoUrl: null,
  tags: [],
  servings: 4,
  sourceUrl: null,
  ingredients: [],
  nutrients: null,
  allergenCodes: [],
  matchScore: 1,
  coverage: [],
};

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-12T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/cooking/sessions/4/state',
});

describe('CookingSessionPage', () => {
  let http: HttpTestingController;
  let routeId = '4';
  let dialogOpen: ReturnType<typeof vi.fn>;
  let shortageClosed: Subject<boolean | undefined>;

  const configure = async (id: string) => {
    routeId = id;
    shortageClosed = new Subject<boolean | undefined>();
    dialogOpen = vi.fn().mockReturnValue({ afterClosed: () => shortageClosed });
    await TestBed.configureTestingModule({
      imports: [CookingSessionPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // The page's wiring is what is under test: what the dialog shows is
        // covered by its own spec, so opening one reports back to a subject.
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Answers the unit catalog every page asks for on init. */
  const flushUnits = (): void => {
    http.expectOne('/api/units').flush(UNITS);
  };

  /** Creates the page and answers the session and its recipe. */
  const render = async (session: CookingSession = LIVE, recipe: RecipeDetail = RECIPE) => {
    await configure(String(session.id));
    const fixture = TestBed.createComponent(CookingSessionPage);
    fixture.detectChanges();
    flushUnits();
    http.expectOne(`/api/cooking/sessions/${session.id}`).flush(session);
    fixture.detectChanges();
    http.expectOne(`/api/recipes/${session.recipeId}`).flush(recipe);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<CookingSessionPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<CookingSessionPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<CookingSessionPage>, selector: string): void => {
    (root(fixture).querySelector(selector) as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('renders a live session with its recipe, steps and controls', async () => {
    const fixture = await render();

    expect(text(root(fixture).querySelector('.session-heading h1'))).toBe(
      'Spaghetti alla Carbonara',
    );
    expect(text(root(fixture).querySelector('.session-status-badge'))).toBe('In progress');
    expect(nodes(fixture, '.step-row')).toHaveLength(3);
    expect(root(fixture).querySelector('button.pause-session')).toBeTruthy();
    expect(root(fixture).querySelector('button.complete-session')).toBeTruthy();
    expect(root(fixture).querySelector('button.abandon-session')).toBeTruthy();
    // Paused is not the state, so the resume path is a different control.
    expect(root(fixture).querySelector('button.resume-session')).toBeNull();
  });

  it('ticks steps locally without asking the server', async () => {
    const fixture = await render();

    (root(fixture).querySelector('.step-check input') as HTMLInputElement).click();
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.session-steps mat-card-subtitle'))).toBe(
      '1 of 3 done',
    );
    // The checklist is local by design (the session stores its status, not the
    // steps), so ticking one must cost no request at all.
    expect(http.match(() => true)).toEqual([]);
  });

  it('pauses the session and swaps the control to resume', async () => {
    const fixture = await render();

    click(fixture, 'button.pause-session');
    const pause = http.expectOne('/api/cooking/sessions/4/state');
    expect(pause.request.body).toEqual({ status: 'PAUSED' });
    pause.flush({ ...LIVE, status: 'PAUSED' });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.session-status-badge'))).toBe('Paused');
    expect(root(fixture).querySelector('button.pause-session')).toBeNull();
    expect(root(fixture).querySelector('button.resume-session')).toBeTruthy();
  });

  it('resumes a paused session', async () => {
    const fixture = await render({ ...LIVE, status: 'PAUSED' });

    click(fixture, 'button.resume-session');
    const resume = http.expectOne('/api/cooking/sessions/4/state');
    expect(resume.request.body).toEqual({ status: 'IN_PROGRESS' });
    resume.flush(LIVE);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.session-status-badge'))).toBe('In progress');
  });

  it('turns a 409 shortage into the dialog, not an error, and re-sends on accept', async () => {
    const fixture = await render();

    click(fixture, 'button.complete-session');
    const refused = http.expectOne('/api/cooking/sessions/4/state');
    expect(refused.request.body).toEqual({ status: 'COMPLETED' });
    refused.flush(SHORTAGE, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    // The plan is not a failure: nothing has changed, and the dialog is the
    // question the backend is asking.
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    expect(dialogOpen.mock.calls[0][0]).toBe(ShortageDialog);
    expect(dialogOpen.mock.calls[0][1].data).toEqual(SHORTAGE);
    expect(root(fixture).querySelector('.session-transition-error')).toBeNull();

    shortageClosed.next(true);
    fixture.detectChanges();

    // "Cook anyway" re-sends the completion, this time accepting the shortage.
    const accepted = http.expectOne('/api/cooking/sessions/4/state');
    expect(accepted.request.body).toEqual({ status: 'COMPLETED', acceptShortage: true });
    accepted.flush(COMPLETED);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.session-deduction'))).toContain(
      'Deducted from your pantry',
    );
    expect(dialogOpen).toHaveBeenCalledTimes(1);
  });

  it('leaves everything untouched when the shortage dialog is cancelled', async () => {
    const fixture = await render();

    click(fixture, 'button.complete-session');
    http
      .expectOne('/api/cooking/sessions/4/state')
      .flush(SHORTAGE, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(dialogOpen).toHaveBeenCalledTimes(1);

    shortageClosed.next(false);
    fixture.detectChanges();

    // No second request: cancelling is the same "nothing changes" the refused
    // completion already promised.
    expect(http.match(() => true)).toEqual([]);
    expect(text(root(fixture).querySelector('.session-status-badge'))).toBe('In progress');
  });

  it('shows an illegal-transition 409 as the error it is, without a dialog', async () => {
    const fixture = await render();

    click(fixture, 'button.complete-session');
    http
      .expectOne('/api/cooking/sessions/4/state')
      .flush(errorResponse(409, 'Cannot move from IN_PROGRESS to COMPLETED twice'), {
        status: 409,
        statusText: 'Conflict',
      });
    fixture.detectChanges();

    // This 409 carries the standard error body, so it is an error — the page
    // must not mistake it for a shortage plan.
    expect(text(root(fixture).querySelector('.session-transition-error'))).toBe(
      'Cannot move from IN_PROGRESS to COMPLETED twice',
    );
    expect(dialogOpen).not.toHaveBeenCalled();
  });

  it('renders a completed session read-only with what was deducted', async () => {
    const fixture = await render({ ...COMPLETED, deduction: COMPLETED.deduction });

    expect(text(root(fixture).querySelector('.session-status-badge'))).toBe('Completed');
    expect(text(root(fixture).querySelector('.session-readonly'))).toContain(
      'can no longer change',
    );
    expect(root(fixture).querySelector('button.complete-session')).toBeNull();
    expect(root(fixture).querySelector('button.pause-session')).toBeNull();
    expect(root(fixture).querySelector('button.abandon-session')).toBeNull();

    const lines = nodes(fixture, '.deduction-line .line-name').map((node) => text(node));
    expect(lines).toEqual(['Eggs', 'Bacon']);
  });

  it('shows the not-found card for a 404 without asking for a recipe', async () => {
    await configure('999');
    const fixture = TestBed.createComponent(CookingSessionPage);
    fixture.detectChanges();
    flushUnits();

    http
      .expectOne('/api/cooking/sessions/999')
      .flush(errorResponse(404, 'Cooking session 999 not found'), {
        status: 404,
        statusText: 'Not Found',
      });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.session-not-found h2'))).toBe(
      'Cooking session not found',
    );
    // No recipe read was ever triggered: a missing session has no recipe.
    expect(http.match(() => true)).toEqual([]);
  });

  it('keeps the session usable when its recipe cannot be read', async () => {
    await configure('4');
    const fixture = TestBed.createComponent(CookingSessionPage);
    fixture.detectChanges();
    flushUnits();

    http.expectOne('/api/cooking/sessions/4').flush(LIVE);
    fixture.detectChanges();
    http
      .expectOne('/api/recipes/1')
      .flush(errorResponse(404, 'Recipe 1 not found'), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    // The session is the subject of the page: it stays on screen with its
    // controls, the recipe fails into its own small note.
    expect(text(root(fixture).querySelector('.session-heading h1'))).toBe('Recipe #1');
    expect(text(root(fixture).querySelector('.steps-error'))).toBe('Recipe 1 not found');
    expect(root(fixture).querySelector('button.complete-session')).toBeTruthy();
    expect(root(fixture).querySelector('.session-error-message')).toBeNull();
  });
});
