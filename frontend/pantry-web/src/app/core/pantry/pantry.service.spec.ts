import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ConsumptionEntry, PantryItem, PantrySummary } from './pantry.models';
import { PantryService } from './pantry.service';

const ITEM: PantryItem = {
  id: 7,
  fdcId: 171265,
  name: 'Milk, whole',
  category: 'Dairy and Egg Products',
  quantity: 244,
  unitCode: 'GRAM',
  location: 'Fridge door',
  note: null,
  version: 0,
  updatedAt: '2026-10-01T10:00:00Z',
};

const ENTRIES: ConsumptionEntry[] = [
  { id: 1, deltaQuantity: 244, origin: 'MANUAL', createdAt: '2026-10-01T10:00:00Z' },
  { id: 2, deltaQuantity: -44.5, origin: 'MANUAL', createdAt: '2026-10-03T18:30:00Z' },
];

const SUMMARY: PantrySummary = {
  totalItems: 34,
  zeroItems: 3,
  // Largest first, then alphabetical: the order the server chose, not one this
  // client is allowed to have a say in.
  byCategory: [
    { category: 'Grains', items: 12 },
    { category: 'Uncategorized', items: 1 },
  ],
  byUnitType: [
    { category: 'WEIGHT', items: 20 },
    { category: 'VOLUME', items: 9 },
    { category: 'COUNT', items: 5 },
  ],
};

describe('PantryService', () => {
  let service: PantryService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    service = TestBed.inject(PantryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('listItems GETs the pantry without touching it any further', async () => {
    const result = firstValueFrom(service.listItems());

    const req = http.expectOne('/api/pantry');
    expect(req.request.method).toBe('GET');
    req.flush([ITEM]);

    await expect(result).resolves.toEqual([ITEM]);
  });

  it('updateItem PUTs both editable fields, null included', async () => {
    const cleared = { ...ITEM, location: null, note: null, version: 1 };
    const result = firstValueFrom(service.updateItem(7, { location: null, note: null }));

    const req = http.expectOne('/api/pantry/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ location: null, note: null });
    req.flush(cleared);

    await expect(result).resolves.toEqual(cleared);
  });

  it('deleteItem DELETEs the line and expects no body back', async () => {
    const result = firstValueFrom(service.deleteItem(7));

    const req = http.expectOne('/api/pantry/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(result).resolves.toBeNull();
  });

  it('createItem POSTs the catalog reference and nothing invented', async () => {
    const result = firstValueFrom(
      service.createItem({ fdcId: 171265, amount: 2, unitCode: 'PINT' }),
    );

    const req = http.expectOne('/api/pantry');
    expect(req.request.method).toBe('POST');
    // The backend answers 409 when the ingredient is already there, which it can only
    // tell from the fdcId: a local id would be a contract this client invented.
    expect(req.request.body).toEqual({ fdcId: 171265, amount: 2, unitCode: 'PINT' });
    expect(req.request.body).not.toHaveProperty('ingredientId');
    req.flush({ ...ITEM, quantity: 488, unitCode: 'GRAM' });

    await expect(result).resolves.toMatchObject({ quantity: 488, unitCode: 'GRAM' });
  });

  it('adjustQuantity POSTs the signed amount and the unit it was typed in', async () => {
    const adjusted = { ...ITEM, quantity: 199.5, version: 1 };
    const result = firstValueFrom(service.adjustQuantity(7, { amount: -44.5, unitCode: 'ML' }));

    const req = http.expectOne('/api/pantry/7/adjustments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: -44.5, unitCode: 'ML' });
    req.flush(adjusted);

    await expect(result).resolves.toEqual(adjusted);
  });

  it('listEntries GETs the ledger of one line', async () => {
    const result = firstValueFrom(service.listEntries(7));

    const req = http.expectOne('/api/pantry/7/entries');
    expect(req.request.method).toBe('GET');
    req.flush(ENTRIES);

    await expect(result).resolves.toEqual(ENTRIES);
  });

  it('getSummary GETs the aggregate and hands the totals back exactly as counted', async () => {
    const result = firstValueFrom(service.getSummary());

    // Its own path: /api/pantry is the list, and one string prefix is not one
    // endpoint. The breakdown arrives with the totals so no view has to fold a list
    // it did not fetch into an aggregate.
    const req = http.expectOne('/api/pantry/summary');
    expect(req.request.method).toBe('GET');
    req.flush(SUMMARY);

    await expect(result).resolves.toEqual(SUMMARY);
  });

  it('getSummary answers an empty pantry with zeros and no breakdown', async () => {
    const result = firstValueFrom(service.getSummary());

    http.expectOne('/api/pantry/summary').flush({
      totalItems: 0,
      zeroItems: 0,
      byCategory: [],
      byUnitType: [],
    });

    // Not a 404 and not an error: an inventory with nothing in it is a fact the
    // caller renders, so the empty shapes must survive the trip untouched.
    await expect(result).resolves.toEqual({
      totalItems: 0,
      zeroItems: 0,
      byCategory: [],
      byUnitType: [],
    });
  });

  it('getSummary rejects an unauthenticated read with the message the API sent', async () => {
    const result = firstValueFrom(service.getSummary());

    http.expectOne('/api/pantry/summary').flush(
      {
        timestamp: '2026-10-05T12:00:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Full authentication is required to access this resource',
        path: '/api/pantry/summary',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    // The aggregate gets no special treatment on the way out: the JWT interceptor
    // and toErrorResponse already turn it into the one shape every caller reads.
    await expect(result).rejects.toMatchObject({
      status: 401,
      error: { message: 'Full authentication is required to access this resource' },
    });
  });

  it('surfaces the ErrorResponse body so the caller can read its message', async () => {
    const result = firstValueFrom(service.adjustQuantity(7, { amount: -900, unitCode: 'GRAM' }));

    const req = http.expectOne('/api/pantry/7/adjustments');
    req.flush(
      {
        timestamp: '2026-10-04T09:00:00Z',
        status: 409,
        error: 'Conflict',
        message:
          'Pantry item 7: an adjustment of -900 GRAM would leave -656 GRAM, but it holds 244 GRAM',
        path: '/api/pantry/7/adjustments',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(result).rejects.toMatchObject({
      status: 409,
      error: {
        message:
          'Pantry item 7: an adjustment of -900 GRAM would leave -656 GRAM, but it holds 244 GRAM',
      },
    });
  });

  it('reports a line that is gone as a 404, never as an empty success', async () => {
    const result = firstValueFrom(service.deleteItem(999));

    const req = http.expectOne('/api/pantry/999');
    req.flush(
      {
        timestamp: '2026-10-04T09:00:00Z',
        status: 404,
        error: 'Not Found',
        message: 'Pantry item 999 not found',
        path: '/api/pantry/999',
      },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(result).rejects.toMatchObject({ status: 404 });
  });
});
