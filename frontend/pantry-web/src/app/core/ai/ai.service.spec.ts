import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { Recommendation } from './ai.models';
import { AiService } from './ai.service';

const RECOMMENDATION: Recommendation = {
  recipeId: 1,
  title: 'Spaghetti alla Carbonara',
  coveragePercentage: 80,
  missingIngredients: ['Pancetta'],
  reasoning: 'Your pantry covers most of it; only the cured meat is missing.',
};

describe('AiService', () => {
  let service: AiService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(AiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('recommend POSTs to the AI endpoint with no body', async () => {
    const result = firstValueFrom(service.recommend());

    const req = http.expectOne('/api/ai/recommend');
    expect(req.request.method).toBe('POST');
    // The pantry, catalog and useAi preference are read server-side from the
    // principal, so nothing about the prompt travels from the client.
    expect(req.request.body).toBeNull();
    req.flush([RECOMMENDATION]);

    await expect(result).resolves.toEqual([RECOMMENDATION]);
  });

  it('treats the empty array of a disabled recommender as a normal answer', async () => {
    const result = firstValueFrom(service.recommend());

    const req = http.expectOne('/api/ai/recommend');
    // `useAi=false` is not an error: the backend answers 200 with [].
    req.flush([]);

    await expect(result).resolves.toEqual([]);
  });

  it('surfaces a provider failure as an HTTP error for the page to render', async () => {
    const result = firstValueFrom(service.recommend());

    http.expectOne('/api/ai/recommend').flush(
      {
        timestamp: '2026-10-14T00:00:00Z',
        status: 502,
        error: 'Bad Gateway',
        message: 'All AI providers failed',
        path: '/api/ai/recommend',
      },
      { status: 502, statusText: 'Bad Gateway' },
    );

    await expect(result).rejects.toMatchObject({
      status: 502,
      error: { message: 'All AI providers failed' },
    });
  });
});
