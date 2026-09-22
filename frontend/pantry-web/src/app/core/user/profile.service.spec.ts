import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { Allergen, AllergyExclusions, UserPreferences } from './profile.models';
import { ProfileService } from './profile.service';

const PREFERENCES: UserPreferences = {
  filterMode: 'STRICT',
  coverageThreshold: 80,
  diet: 'LOW_FAT',
};

const EXCLUSIONS: AllergyExclusions = {
  exclusions: [{ id: 11, code: 'PEANUT', name: 'Peanut' }],
};

const CATALOG: Allergen[] = [
  { id: 1, code: 'EGG', name: 'Egg' },
  { id: 2, code: 'FISH', name: 'Fish' },
];

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('getPreferences fetches the current preferences', async () => {
    const result = firstValueFrom(service.getPreferences());

    const req = http.expectOne('/api/users/preferences');
    expect(req.request.method).toBe('GET');
    req.flush(PREFERENCES);

    await expect(result).resolves.toEqual(PREFERENCES);
  });

  it('updatePreferences PUTs the three fields and returns the stored values', async () => {
    const result = firstValueFrom(service.updatePreferences(PREFERENCES));

    const req = http.expectOne('/api/users/preferences');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(PREFERENCES);
    req.flush(PREFERENCES);

    await expect(result).resolves.toEqual(PREFERENCES);
  });

  it('getAllergyExclusions returns the current exclusion list', async () => {
    const result = firstValueFrom(service.getAllergyExclusions());

    const req = http.expectOne('/api/users/allergy-exclusions');
    expect(req.request.method).toBe('GET');
    req.flush(EXCLUSIONS);

    await expect(result).resolves.toEqual(EXCLUSIONS);
  });

  it('updateAllergyExclusions replaces the whole list with the given codes', async () => {
    const result = firstValueFrom(service.updateAllergyExclusions(['EGG', 'PEANUT']));

    const req = http.expectOne('/api/users/allergy-exclusions');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ codes: ['EGG', 'PEANUT'] });
    req.flush(EXCLUSIONS);

    await expect(result).resolves.toEqual(EXCLUSIONS);
  });

  it('updateAllergyExclusions clears the list when codes is empty', async () => {
    const result = firstValueFrom(service.updateAllergyExclusions([]));

    const req = http.expectOne('/api/users/allergy-exclusions');
    expect(req.request.body).toEqual({ codes: [] });
    req.flush({ exclusions: [] });

    await expect(result).resolves.toEqual({ exclusions: [] });
  });

  it('surfaces the ErrorResponse body when the backend rejects unknown codes', async () => {
    const result = firstValueFrom(service.updateAllergyExclusions(['peanut']));

    const req = http.expectOne('/api/users/allergy-exclusions');
    req.flush(
      {
        timestamp: '2026-01-01T00:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'Unknown allergen code: peanut',
        path: '/api/users/allergy-exclusions',
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(result).rejects.toMatchObject({
      status: 400,
      error: { message: 'Unknown allergen code: peanut' },
    });
  });

  it('getAllergenCatalog returns the full allergen catalog', async () => {
    const result = firstValueFrom(service.getAllergenCatalog());

    const req = http.expectOne('/api/users/allergens');
    expect(req.request.method).toBe('GET');
    req.flush(CATALOG);

    await expect(result).resolves.toEqual(CATALOG);
  });
});
