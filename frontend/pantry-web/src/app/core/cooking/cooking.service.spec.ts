import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CompletionDeduction, CookingSession } from './cooking.models';
import { CookingService } from './cooking.service';

const SESSION: CookingSession = {
  id: 4,
  recipeId: 1,
  status: 'IN_PROGRESS',
  startedAt: '2026-10-12T10:00:00Z',
  completedAt: null,
  deduction: null,
};

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

describe('CookingService', () => {
  let service: CookingService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(CookingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('starts a session with the recipe id alone', async () => {
    const result = firstValueFrom(service.createSession(1));

    const req = http.expectOne('/api/cooking/sessions');
    expect(req.request.method).toBe('POST');
    // The owner and the startedAt stamp are the server's to set, so the body
    // carries the recipe and nothing else.
    expect(req.request.body).toEqual({ recipeId: 1 });
    req.flush(SESSION);

    await expect(result).resolves.toEqual(SESSION);
  });

  it('surfaces a 404 of a missing recipe for the page to render', async () => {
    const result = firstValueFrom(service.createSession(999));

    http.expectOne('/api/cooking/sessions').flush(
      {
        timestamp: '2026-10-12T10:00:00Z',
        status: 404,
        error: 'Not Found',
        message: 'Recipe 999 not found',
        path: '/api/cooking/sessions',
      },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(result).rejects.toMatchObject({ status: 404 });
  });

  it('lists the sessions as the API orders them', async () => {
    const result = firstValueFrom(service.listSessions());

    const req = http.expectOne('/api/cooking/sessions');
    expect(req.request.method).toBe('GET');
    req.flush([SESSION]);

    await expect(result).resolves.toEqual([SESSION]);
  });

  it('reads one session by id', async () => {
    const result = firstValueFrom(service.getSession(4));

    const req = http.expectOne('/api/cooking/sessions/4');
    expect(req.request.method).toBe('GET');
    req.flush(SESSION);

    await expect(result).resolves.toEqual(SESSION);
  });

  it('sends a plain status and no acceptShortage when it was not asked for', async () => {
    const result = firstValueFrom(service.updateState(4, 'PAUSED'));

    const req = http.expectOne('/api/cooking/sessions/4/state');
    expect(req.request.method).toBe('PUT');
    // Absence is what asks the backend to check the pantry; `acceptShortage:
    // false` would be the same request written a different way.
    expect(req.request.body).toEqual({ status: 'PAUSED' });
    expect(req.request.body).not.toHaveProperty('acceptShortage');
    req.flush({ ...SESSION, status: 'PAUSED' });

    await expect(result).resolves.toMatchObject({ status: 'PAUSED' });
  });

  it('sends acceptShortage only when it is true', async () => {
    const result = firstValueFrom(service.updateState(4, 'COMPLETED', true));

    const req = http.expectOne('/api/cooking/sessions/4/state');
    expect(req.request.body).toEqual({ status: 'COMPLETED', acceptShortage: true });
    req.flush({ ...SESSION, status: 'COMPLETED', completedAt: '2026-10-12T11:00:00Z' });

    await expect(result).resolves.toMatchObject({ status: 'COMPLETED' });
  });

  it('leaves acceptShortage out when it is false', async () => {
    const result = firstValueFrom(service.updateState(4, 'COMPLETED', false));

    const req = http.expectOne('/api/cooking/sessions/4/state');
    expect(req.request.body).toEqual({ status: 'COMPLETED' });
    req.flush(SESSION);

    await expect(result).resolves.toEqual(SESSION);
  });

  it('passes a 409 shortage plan through untouched for the page to interpret', async () => {
    const result = firstValueFrom(service.updateState(4, 'COMPLETED'));

    http
      .expectOne('/api/cooking/sessions/4/state')
      .flush(SHORTAGE, { status: 409, statusText: 'Conflict' });

    // The service does not decide what a 409 means; it hands the body to the
    // caller, which is the only layer that can tell a plan from an error.
    await expect(result).rejects.toMatchObject({ status: 409, error: SHORTAGE });
  });
});
