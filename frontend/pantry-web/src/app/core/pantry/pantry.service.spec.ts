import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ConsumptionEntry, PantryItem } from './pantry.models';
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
