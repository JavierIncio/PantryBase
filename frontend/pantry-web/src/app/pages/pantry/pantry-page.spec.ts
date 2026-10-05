import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConsumptionEntry, PantryItem } from '../../core/pantry/pantry.models';
import { PantryPage } from './pantry-page';

/** Two categories, in the order the API is documented to answer them. */
const MILK: PantryItem = {
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

const YOGURT: PantryItem = {
  id: 8,
  fdcId: 172180,
  name: 'Yogurt, plain',
  category: 'Dairy and Egg Products',
  quantity: 2,
  unitCode: 'UNIT',
  location: null,
  note: 'Big tub',
  version: 1,
  updatedAt: '2026-10-02T10:00:00Z',
};

const LENTILS: PantryItem = {
  id: 9,
  fdcId: 172852,
  name: 'Lentils, raw',
  category: 'Legumes',
  quantity: 500,
  unitCode: 'GRAM',
  location: 'Cupboard',
  note: null,
  version: 3,
  updatedAt: '2026-10-03T10:00:00Z',
};

const LEDGER: ConsumptionEntry[] = [
  { id: 1, deltaQuantity: 244, origin: 'MANUAL', createdAt: '2026-10-01T10:00:00Z' },
  { id: 2, deltaQuantity: -44.5, origin: 'MANUAL', createdAt: '2026-10-03T18:30:00Z' },
];

const INSUFFICIENT_STOCK =
  'Pantry item 7: an adjustment of -900 GRAM would leave -656 GRAM, but it holds 244 GRAM';

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-04T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/pantry',
});

describe('PantryPage', () => {
  let http: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const configure = async () => {
    snackBar = { open: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [PantryPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Creates the page and answers the initial list request with `items`. */
  const render = async (items: PantryItem[] = [MILK, YOGURT, LENTILS]) => {
    await configure();
    const fixture = TestBed.createComponent(PantryPage);
    fixture.detectChanges();
    http.expectOne('/api/pantry').flush(items);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<PantryPage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed, icon ligatures included. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<PantryPage>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<PantryPage>, selector: string, index = 0): void => {
    (nodes(fixture, selector)[index] as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  /** Types into a reactive input and leaves the field, as a user does. */
  const type = (
    fixture: ComponentFixture<PantryPage>,
    selector: string,
    value: string,
    index = 0,
  ): void => {
    const input = nodes(fixture, selector)[index] as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  };

  const inputValue = (fixture: ComponentFixture<PantryPage>, selector: string, index = 0) =>
    (nodes(fixture, selector)[index] as HTMLInputElement).value;

  /** Answers every pending request with an empty ledger, so a test can ignore it. */
  const flushEntries = (fixture: ComponentFixture<PantryPage>): void => {
    for (const req of http.match('/api/pantry/7/entries')) {
      req.flush([]);
    }
    fixture.detectChanges();
  };

  /** Yields the microtasks a failed response needs before it triggers a follow-up request. */
  const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('groups the lines by category in the order the API answered them', async () => {
    const fixture = await render();

    expect(nodes(fixture, '.mat-mdc-card-title').map((node) => text(node))).toEqual([
      'Dairy and Egg Products',
      'Legumes',
    ]);
    // Order inside a category is the server's, not the template's: sorting here
    // could only disagree with the backend's collation.
    expect(nodes(fixture, '.item-name').map((node) => text(node))).toEqual([
      'Milk, whole',
      'Yogurt, plain',
      'Lentils, raw',
    ]);
    expect(text(root(fixture).querySelector('.pantry-summary'))).toBe('3 items in 2 categories');
  });

  it('shows the quantity of every line with its unit resolved to a symbol', async () => {
    const fixture = await render();

    // Quantity first, unit second: "244 g" rather than "g 244". The two live in
    // their own spans so the row can weight them differently.
    expect(nodes(fixture, '.item-amount').map((node) => text(node))).toEqual(['244', '2', '500']);
    expect(nodes(fixture, '.item-unit').map((node) => text(node))).toEqual(['g', 'units', 'g']);
    expect(nodes(fixture, '.item-detail').map((node) => text(node))).toEqual([
      'place Fridge door',
      'notes Big tub',
      'place Cupboard',
    ]);
  });

  it('shows the empty state instead of an empty list', async () => {
    const fixture = await render([]);

    expect(text(root(fixture).querySelector('.pantry-empty'))).toContain('Your pantry is empty');
    expect(nodes(fixture, '.pantry-category').length).toBe(0);
    expect(root(fixture).querySelector('.pantry-summary')).toBeNull();
  });

  it('keeps a spinner up while the pantry is still loading', async () => {
    await configure();
    const fixture = TestBed.createComponent(PantryPage);
    fixture.detectChanges();

    // The list request is still in flight: nothing flushed yet.
    expect(root(fixture).querySelector('.pantry-status mat-spinner')).toBeTruthy();

    http.expectOne('/api/pantry').flush([MILK]);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.pantry-status')).toBeNull();
    expect(nodes(fixture, '.pantry-item').length).toBe(1);
  });

  it('shows the backend message when the load fails and retries on demand', async () => {
    await configure();
    const fixture = TestBed.createComponent(PantryPage);
    fixture.detectChanges();

    http
      .expectOne('/api/pantry')
      .flush(errorResponse(500, 'Database is down'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.pantry-error-message'))).toBe('Database is down');
    // An inventory that failed to load must not look like an empty pantry.
    expect(root(fixture).querySelector('.pantry-empty')).toBeNull();

    click(fixture, 'button.retry-pantry');
    http.expectOne('/api/pantry').flush([MILK]);
    fixture.detectChanges();

    expect(root(fixture).querySelector('.pantry-error')).toBeNull();
    expect(nodes(fixture, '.pantry-item').length).toBe(1);
  });

  it('prefills the inline editor from the line and saves both fields', async () => {
    const fixture = await render();
    click(fixture, '.action-edit', 0);

    expect(inputValue(fixture, '.edit-panel input')).toBe('Fridge door');

    type(fixture, '.edit-panel input', 'Top shelf');
    click(fixture, 'button.save-details');

    const req = http.expectOne('/api/pantry/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ location: 'Top shelf', note: null });
    req.flush({ ...MILK, location: 'Top shelf' });
    fixture.detectChanges();

    expect(root(fixture).querySelector('.edit-panel')).toBeNull();
    expect(text(root(fixture).querySelector('.item-detail'))).toBe('place Top shelf');
    expect(snackBar.open).toHaveBeenCalledWith('Item updated', 'OK', { duration: 3000 });
  });

  it('encodes an emptied location and note as null, which is how the API clears them', async () => {
    const fixture = await render([YOGURT]);
    click(fixture, '.action-edit');

    // A line with a note but no location: clearing both has to travel as nulls,
    // because the endpoint replaces the two fields instead of merging them.
    expect(inputValue(fixture, '.edit-panel input')).toBe('');
    expect(inputValue(fixture, '.edit-panel input', 1)).toBe('Big tub');

    type(fixture, '.edit-panel input', '', 0);
    type(fixture, '.edit-panel input', '', 1);
    click(fixture, 'button.save-details');

    const req = http.expectOne('/api/pantry/8');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ location: null, note: null });
    req.flush({ ...YOGURT, location: null, note: null });
    fixture.detectChanges();

    expect(nodes(fixture, '.item-detail').length).toBe(0);
  });

  it('keeps a too long field local and never sends it', async () => {
    const fixture = await render();
    click(fixture, '.action-edit');
    type(fixture, '.edit-panel input', 'a'.repeat(101));

    expect(text(root(fixture).querySelector('.edit-panel mat-error'))).toContain(
      'Use at most 100 characters',
    );
    expect((root(fixture).querySelector('button.save-details') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(http.match(() => true)).toEqual([]);
  });

  it('keeps the typed values and shows the message when an update is rejected', async () => {
    const fixture = await render();
    click(fixture, '.action-edit');
    type(fixture, '.edit-panel input', 'Top shelf');
    click(fixture, 'button.save-details');

    http
      .expectOne('/api/pantry/7')
      .flush(errorResponse(400, 'Location rejected by the catalogue check'), {
        status: 400,
        statusText: 'Bad Request',
      });
    fixture.detectChanges();

    // The edit survives so the user can fix it and try again.
    expect(inputValue(fixture, '.edit-panel input')).toBe('Top shelf');
    expect(text(root(fixture).querySelector('.panel-error'))).toBe(
      'Location rejected by the catalogue check',
    );
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('sends the signed amount as typed and takes the quantity from the response', async () => {
    const fixture = await render();
    click(fixture, '.action-adjust', 0);

    // The form starts on the line's own unit: the answer for most adjustments.
    expect(fixture.componentInstance.adjustForm.controls.unitCode.value).toBe('GRAM');
    expect(text(root(fixture).querySelector('.adjust-current'))).toBe('In stock: 244 g');

    type(fixture, '.amount-field input', '-44.5');
    click(fixture, 'button.apply-adjustment');

    const req = http.expectOne('/api/pantry/7/adjustments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: -44.5, unitCode: 'GRAM' });
    req.flush({ ...MILK, quantity: 199.5, version: 1 });
    fixture.detectChanges();

    expect(root(fixture).querySelector('.adjust-panel')).toBeNull();
    expect(text(root(fixture).querySelector('.item-amount'))).toBe('199.5');
    expect(text(root(fixture).querySelector('.item-unit'))).toBe('g');
    expect(snackBar.open).toHaveBeenCalledWith('Quantity updated', 'OK', { duration: 3000 });
  });

  it('refuses a zero adjustment without calling the API', async () => {
    const fixture = await render();
    click(fixture, '.action-adjust');
    type(fixture, '.amount-field input', '0');

    expect(fixture.componentInstance.adjustForm.controls.amount.hasError('zero')).toBe(true);
    expect(text(root(fixture).querySelector('.amount-field mat-error'))).toContain(
      'Zero is not a movement',
    );
    expect(
      (root(fixture).querySelector('button.apply-adjustment') as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(http.match(() => true)).toEqual([]);
  });

  it('shows the 409 of an insufficient adjustment inside the panel and keeps the amount', async () => {
    const fixture = await render();
    click(fixture, '.action-adjust');
    type(fixture, '.amount-field input', '-900');
    click(fixture, 'button.apply-adjustment');

    http
      .expectOne('/api/pantry/7/adjustments')
      .flush(errorResponse(409, INSUFFICIENT_STOCK), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    // The message carries the three figures the API computed, so it is shown
    // verbatim instead of being replaced with a generic one.
    expect(text(root(fixture).querySelector('.panel-error'))).toBe(INSUFFICIENT_STOCK);
    // The amount survives: the fix is a smaller number than the one typed.
    expect(inputValue(fixture, '.amount-field input')).toBe('-900');
    expect(root(fixture).querySelector('.adjust-panel')).toBeTruthy();
    expect(text(root(fixture).querySelector('.item-amount'))).toBe('244');
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('reloads the list and says so when the line is gone', async () => {
    const fixture = await render();
    click(fixture, '.action-adjust');
    type(fixture, '.amount-field input', '10');
    click(fixture, 'button.apply-adjustment');

    http
      .expectOne('/api/pantry/7/adjustments')
      .flush(errorResponse(404, 'Pantry item 7 not found'), {
        status: 404,
        statusText: 'Not Found',
      });

    // Nothing local can be corrected about a line the server does not have: the
    // panel is dropped and the list is asked for again.
    await settle();
    const reload = http.expectOne('/api/pantry');
    expect(reload.request.method).toBe('GET');
    reload.flush([YOGURT, LENTILS]);
    fixture.detectChanges();

    expect(nodes(fixture, '.item-name').map((node) => text(node))).toEqual([
      'Yogurt, plain',
      'Lentils, raw',
    ]);
    expect(snackBar.open).toHaveBeenCalledWith('Pantry item 7 not found', 'OK', { duration: 8000 });
  });

  it('deletes a line only after the confirmation', async () => {
    const fixture = await render();
    click(fixture, '.action-delete', 1);

    expect(root(fixture).querySelector('.delete-panel')).toBeTruthy();
    expect(http.match(() => true)).toEqual([]);

    click(fixture, 'button.confirm-delete');
    const req = http.expectOne('/api/pantry/8');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(nodes(fixture, '.item-name').map((node) => text(node))).toEqual([
      'Milk, whole',
      'Lentils, raw',
    ]);
    expect(snackBar.open).toHaveBeenCalledWith('Item removed', 'OK', { duration: 3000 });
  });

  it('drops the category block when its last line is removed', async () => {
    const fixture = await render([MILK, LENTILS]);
    click(fixture, '.action-delete', 1);
    click(fixture, 'button.confirm-delete');
    http.expectOne('/api/pantry/9').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(nodes(fixture, '.pantry-category').length).toBe(1);
    expect(text(root(fixture).querySelector('.pantry-summary'))).toBe('1 item in 1 category');
  });

  it('cancels a pending deletion without touching the API', async () => {
    const fixture = await render();
    click(fixture, '.action-delete');
    click(fixture, 'button.cancel-delete');

    expect(root(fixture).querySelector('.delete-panel')).toBeNull();
    expect(nodes(fixture, '.pantry-item').length).toBe(3);
    expect(http.match(() => true)).toEqual([]);
  });

  it('opens the ledger with its signed movements, oldest first', async () => {
    const fixture = await render();
    click(fixture, '.action-history');

    expect(root(fixture).querySelector('.history-panel mat-spinner')).toBeTruthy();

    const req = http.expectOne('/api/pantry/7/entries');
    expect(req.request.method).toBe('GET');
    req.flush(LEDGER);
    fixture.detectChanges();

    const rows = nodes(fixture, '.entry-row');
    expect(rows.length).toBe(2);
    // The sign is always visible, so a movement cannot be skimmed as a quantity.
    expect(text(rows[0].querySelector('.entry-delta'))).toBe('+244');
    expect(text(rows[1].querySelector('.entry-delta'))).toBe('-44.5');
    expect(rows[1].className).toContain('entry-consumed');
    expect(rows.map((row) => text(row.querySelector('.entry-origin')))).toEqual([
      'Manual',
      'Manual',
    ]);
    expect(text(root(fixture).querySelector('.history-note'))).toContain(
      'Movements in g, oldest first',
    );
  });

  it('collapses the open panel when its own action is used again', async () => {
    const fixture = await render();
    click(fixture, '.action-history');
    http.expectOne('/api/pantry/7/entries').flush(LEDGER);
    fixture.detectChanges();
    expect(root(fixture).querySelector('.history-panel')).toBeTruthy();

    click(fixture, '.action-history');

    expect(root(fixture).querySelector('.history-panel')).toBeNull();
    expect(nodes(fixture, '.pantry-item').length).toBe(3);
    // Re-opening refetches instead of reusing a cached answer: any other writer
    // may have moved the same stock since the panel was last looked at.
    flushEntries(fixture);
  });

  it('keeps only one row expanded at a time', async () => {
    const fixture = await render();
    click(fixture, '.action-edit', 0);
    expect(inputValue(fixture, '.edit-panel input')).toBe('Fridge door');

    click(fixture, '.action-edit', 1);

    expect(nodes(fixture, '.item-panel').length).toBe(1);
    // The panel that is now open belongs to the second line, so it shows that
    // line's values (no location) rather than the first one's.
    expect(inputValue(fixture, '.edit-panel input')).toBe('');
  });

  it('drops a pending delete confirmation when another panel is opened', async () => {
    const fixture = await render();
    click(fixture, '.action-delete');
    expect(root(fixture).querySelector('.delete-panel')).toBeTruthy();

    click(fixture, '.action-history');
    flushEntries(fixture);

    // A confirmation the user wandered away from must not stay one click away
    // from a request that cannot be undone.
    expect(root(fixture).querySelector('.delete-panel')).toBeNull();
    expect(root(fixture).querySelector('.history-panel')).toBeTruthy();
  });

  it('disables the row actions while a write is in flight', async () => {
    const fixture = await render();
    click(fixture, '.action-edit');
    type(fixture, '.edit-panel input', 'Top shelf');
    click(fixture, 'button.save-details');

    // The quantity is a shared fact: no second write may be started meanwhile.
    const rowActions = [
      ...root(fixture).querySelector('.pantry-item')!.querySelectorAll('.item-actions button'),
    ];
    expect(rowActions.length).toBe(4);
    expect(rowActions.every((button) => (button as HTMLButtonElement).disabled)).toBe(true);

    http.expectOne('/api/pantry/7').flush({ ...MILK, location: 'Top shelf' });
    fixture.detectChanges();

    const afterwards = [
      ...root(fixture).querySelector('.pantry-item')!.querySelectorAll('.item-actions button'),
    ];
    expect(afterwards.some((button) => (button as HTMLButtonElement).disabled)).toBe(false);
  });
});
