import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { IngredientDetail, IngredientSearchHit, Unit } from '../../core/catalog/catalog.models';
import { PantryItem } from '../../core/pantry/pantry.models';
import { AddPantryItemDialog, AddPantryItemResult } from './add-pantry-item-dialog';

/** The catalog as `GET /api/units` answers it: five rows, sorted by code. */
const UNITS: Unit[] = [
  { code: 'CUP', name: 'Cup', category: 'VOLUME', canonical: false },
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'ML', name: 'Milliliter', category: 'VOLUME', canonical: true },
  { code: 'PINT', name: 'Pint', category: 'VOLUME', canonical: false },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

const MILK_HIT: IngredientSearchHit = {
  fdcId: 171265,
  name: 'Milk, whole',
  dataType: 'Foundation',
};
const FLOUR_HIT: IngredientSearchHit = {
  fdcId: 168917,
  name: 'Flour, white',
  dataType: 'SR Legacy',
};

/** A food the provider weighed two household measures of. */
const MILK: IngredientDetail = {
  id: 5,
  fdcId: MILK_HIT.fdcId,
  name: MILK_HIT.name,
  dataType: MILK_HIT.dataType,
  category: 'Dairy and Egg Products',
  nutrients: { energyKcal: 61, proteinG: 3.15, fatG: 3.25, carbsG: 4.8 },
  densityClass: 'MILK',
  measures: [
    { unitCode: 'CUP', gramPerUnit: 244 },
    { unitCode: 'PINT', gramPerUnit: 489 },
  ],
};

/** A food the provider weighed no household measure for. */
const FLOUR: IngredientDetail = {
  id: 6,
  fdcId: FLOUR_HIT.fdcId,
  name: FLOUR_HIT.name,
  dataType: FLOUR_HIT.dataType,
  category: 'Cereal Grains and Products',
  nutrients: { energyKcal: 364, proteinG: 10.33, fatG: 0.98, carbsG: 76.31 },
  densityClass: 'FLOUR',
  measures: [],
};

/** The line the API answers with, in the unit it normalized the amount to. */
const STORED: PantryItem = {
  id: 11,
  fdcId: MILK.fdcId,
  name: MILK.name,
  category: MILK.category,
  quantity: 488,
  unitCode: 'GRAM',
  location: null,
  note: null,
  version: 0,
  updatedAt: '2026-10-05T08:00:00Z',
};

const CONFLICT =
  'Ingredient 171265 is already in the pantry; use the adjustment endpoint to change its quantity';

const NOT_MATERIALIZED =
  'Ingredient 171265 is not in the local catalog; read /api/catalog/ingredients/171265 before adding it to the pantry';

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string, path = '/api/pantry') => ({
  timestamp: '2026-10-05T09:00:00Z',
  status,
  error: 'Error',
  message,
  path,
});

describe('AddPantryItemDialog', () => {
  let http: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  const configure = async () => {
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [AddPantryItemDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Stands in for the reference the overlay would inject, so a spec can read
        // what the dialog reported without moving a single DOM node.
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Opens the dialog with the unit catalog answered and the search step up. */
  const open = async (): Promise<ComponentFixture<AddPantryItemDialog>> => {
    await configure();
    const fixture = TestBed.createComponent(AddPantryItemDialog);
    fixture.detectChanges();
    http.expectOne('/api/units').flush(UNITS);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<AddPantryItemDialog>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  /** Rendered text with the template's whitespace collapsed. */
  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  const nodes = (fixture: ComponentFixture<AddPantryItemDialog>, selector: string): Element[] => [
    ...root(fixture).querySelectorAll(selector),
  ];

  const click = (fixture: ComponentFixture<AddPantryItemDialog>, selector: string): void => {
    (root(fixture).querySelector(selector) as HTMLButtonElement).click();
    fixture.detectChanges();
  };

  /** Types into a reactive input and leaves the field, as a user does. */
  const type = (
    fixture: ComponentFixture<AddPantryItemDialog>,
    selector: string,
    value: string,
  ): void => {
    const input = root(fixture).querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  };

  const form = (fixture: ComponentFixture<AddPantryItemDialog>) =>
    fixture.componentInstance.quantityForm;

  /** Searches for `query` and answers with `hits`, leaving the results on screen. */
  const search = async (
    fixture: ComponentFixture<AddPantryItemDialog>,
    query: string,
    hits: IngredientSearchHit[] = [MILK_HIT, FLOUR_HIT],
  ): Promise<void> => {
    type(fixture, '.search-field input', query);
    click(fixture, 'button.run-search');
    const req = http.expectOne((candidate) => candidate.url === '/api/catalog/ingredients');
    expect(req.request.params.get('query')).toBe(query);
    req.flush(hits);
    fixture.detectChanges();
  };

  /** Picks the first result and answers the detail read with `detail`. */
  const chooseFirst = async (
    fixture: ComponentFixture<AddPantryItemDialog>,
    detail: IngredientDetail = MILK,
  ): Promise<void> => {
    click(fixture, '.search-result');
    http.expectOne(`/api/catalog/ingredients/${detail.fdcId}`).flush(detail);
    fixture.detectChanges();
  };

  /** Everything needed to reach the quantity step of `detail`. */
  const reachQuantity = async (
    detail: IngredientDetail = MILK,
  ): Promise<ComponentFixture<AddPantryItemDialog>> => {
    const fixture = await open();
    // A single result for the food under test, so "pick the first one" always
    // means this detail and no test depends on the order of two foods.
    await search(fixture, 'food', [
      { fdcId: detail.fdcId, name: detail.name, dataType: detail.dataType },
    ]);
    await chooseFirst(fixture, detail);
    return fixture;
  };

  /** Reaches the quantity step, types `amount` and leaves the unit as chosen. */
  const readyToAdd = async (amount = '2'): Promise<ComponentFixture<AddPantryItemDialog>> => {
    const fixture = await reachQuantity();
    type(fixture, '.amount-field input', amount);
    return fixture;
  };

  /**
   * Opens the unit selector and returns the options it rendered, in order.
   *
   * The options live in the select's overlay until it opens, and that overlay is
   * projected onto the document, not inside the fixture.
   */
  const openUnitOptions = async (
    fixture: ComponentFixture<AddPantryItemDialog>,
  ): Promise<Element[]> => {
    (root(fixture).querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    return [...document.querySelectorAll('mat-option')];
  };

  /** What the dialog handed back to the page that opened it. */
  const closed = (): AddPantryItemResult | undefined => dialogRef.close.mock.calls[0]?.[0];

  /**
   * The dialog's own methods, for the overlaps the template already prevents.
   *
   * A search in flight disables the search button, and a detail read in flight
   * disables the results, so no user can start two of either at once. These two
   * tests call the methods directly to pin that the methods do not lean on that
   * arrangement for their own safety: a method that is only correct while a
   * button happens to be disabled stops being correct the moment it is wired to a
   * key shortcut, another template, or a call the page makes for itself.
   */
  const internals = (fixture: ComponentFixture<AddPantryItemDialog>) =>
    fixture.componentInstance as unknown as {
      search(): void;
      choose(hit: IngredientSearchHit): void;
      changeFood(): void;
    };

  afterEach(() => {
    // A fixture still alive here is a test that did not clean up its own
    // requests; the backend is the one that refuses to let it pass.
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('searches the catalog with the typed query and lists what comes back', async () => {
    const fixture = await open();

    type(fixture, '.search-field input', '  milk  ');
    click(fixture, 'button.run-search');

    // Trimmed: the spaces the user typed are not part of the food they are after,
    // and an untrimmed query is a cache key nobody asks for twice.
    const req = http.expectOne((candidate) => candidate.url === '/api/catalog/ingredients');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('query')).toBe('milk');
    req.flush([MILK_HIT, FLOUR_HIT]);
    fixture.detectChanges();

    // Name and type sit at both ends of one row, so they are two pieces of the
    // button rather than one sentence.
    expect(
      nodes(fixture, '.search-result').map((node) => [
        text(node.querySelector('.result-name')),
        text(node.querySelector('.result-type')),
      ]),
    ).toEqual([
      ['Milk, whole', 'Foundation'],
      ['Flour, white', 'SR Legacy'],
    ]);
  });

  it('keeps a blank query local and never asks the API', async () => {
    const fixture = await open();

    type(fixture, '.search-field input', '   ');
    click(fixture, 'button.run-search');

    expect(text(root(fixture).querySelector('.search-field mat-error'))).toContain(
      'Type something to search for',
    );
    expect(http.match(() => true)).toEqual([]);
  });

  it('answers the search asked last even when the first is still in flight', async () => {
    const fixture = await open();
    const dialog = internals(fixture);

    type(fixture, '.search-field input', 'milk');
    dialog.search();
    type(fixture, '.search-field input', 'flour');
    dialog.search();

    // Both answers are now in the air. The slow one is the obsolete one, and
    // letting it land last would put foods named "milk" under a query that says
    // "flour" -- a list the user never asked for, and would pick from.
    const inFlight = http.match((candidate) => candidate.url === '/api/catalog/ingredients');
    expect(inFlight.length).toBe(2);

    inFlight[1].flush([FLOUR_HIT]);
    fixture.detectChanges();
    inFlight[0].flush([MILK_HIT]);
    fixture.detectChanges();

    expect(
      nodes(fixture, '.search-result').map((node) => text(node.querySelector('.result-name'))),
    ).toEqual(['Flour, white']);
  });

  it('ignores a detail read still in flight when the food is changed', async () => {
    const fixture = await open();
    await search(fixture, 'food');
    const dialog = internals(fixture);

    dialog.choose(MILK_HIT);
    dialog.changeFood();

    http.expectOne('/api/catalog/ingredients/171265').flush(MILK);
    fixture.detectChanges();

    // Still on the search step: the user had already said no to this food, and
    // answering now would drag the dialog into the quantity step behind their
    // back, for a food they had just removed from the results.
    expect(root(fixture).querySelector('.search-field')).toBeTruthy();
    expect(root(fixture).querySelector('.amount-field')).toBeNull();
  });

  it('reads the detail of the picked food and offers its measures first', async () => {
    const fixture = await open();
    await search(fixture, 'milk');

    click(fixture, '.search-result');
    // The detail read is not optional: it is what materializes the ingredient and
    // what knows the measures, so the quantity step waits for it.
    const req = http.expectOne('/api/catalog/ingredients/171265');
    expect(req.request.method).toBe('GET');
    expect(root(fixture).querySelector('.dialog-status mat-spinner')).toBeTruthy();
    req.flush(MILK);
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.chosen-name'))).toBe('Milk, whole');
    expect(form(fixture).controls.unitCode.value).toBe('CUP');
  });

  it('offers every catalog unit, grouped, with the measured ones first and their weight', async () => {
    const fixture = await reachQuantity();

    const options = await openUnitOptions(fixture);
    // Nothing is dropped: two of the five units were weighed for this food and the
    // other three are still offered, because they convert too.
    expect(options.length).toBe(UNITS.length);
    expect(options.map((node) => text(node))).toEqual([
      'Gram',
      'Cup · 244 g',
      'Pint · 489 g',
      'Milliliter',
      'Unit',
    ]);
    // Weight before volume before count, each one labelled in the UI.
    expect(
      [...document.querySelectorAll('.mat-mdc-optgroup-label')].map((node) => text(node)),
    ).toEqual(['Weight', 'Volume', 'Count']);
  });

  it('offers no default unit when the provider weighed no measure for the food', async () => {
    const fixture = await reachQuantity(FLOUR);

    // Every unit is still there; the control is simply empty, because choosing one
    // for the user would mean guessing what gets stored.
    expect((await openUnitOptions(fixture)).length).toBe(UNITS.length);
    expect(form(fixture).controls.unitCode.value).toBe('');
    expect(form(fixture).invalid).toBe(true);
  });

  it('creates the line with the fdcId of the picked food and reports what was stored', async () => {
    const fixture = await readyToAdd();
    form(fixture).controls.unitCode.setValue('PINT');

    click(fixture, 'button.confirm-add');

    const req = http.expectOne('/api/pantry');
    expect(req.request.method).toBe('POST');
    // The catalog speaks in fdcIds: an internal ingredient id in this body would be
    // a contract this client invented.
    expect(req.request.body).toEqual({ fdcId: 171265, amount: 2, unitCode: 'PINT' });
    expect(req.request.body).not.toHaveProperty('ingredientId');
    // Two pints of milk are stored as 488 g, and 488 g is what the page shows.
    req.flush(STORED);
    fixture.detectChanges();

    expect(closed()).toEqual({ kind: 'created', item: STORED });
  });

  it('cannot be asked to add twice while the first request is in flight', async () => {
    const fixture = await readyToAdd('1');

    click(fixture, 'button.confirm-add');

    expect((root(fixture).querySelector('button.confirm-add') as HTMLButtonElement).disabled).toBe(
      true,
    );
    click(fixture, 'button.confirm-add');

    // One request, so one line: a second bottle is never folded in silently.
    // `match` hands the request over and takes it out of the pending list, so it
    // is answered from here instead of with an `expectOne` that would find none.
    const inFlight = http.match('/api/pantry');
    expect(inFlight.length).toBe(1);
    inFlight[0].flush(STORED);
  });

  it.each([
    ['zero', '0'],
    ['negative', '-3'],
  ])('keeps a %s amount local and never sends it', async (_case, value) => {
    const fixture = await reachQuantity();

    type(fixture, '.amount-field input', value);

    expect(form(fixture).controls.amount.hasError('positive')).toBe(true);
    expect(text(root(fixture).querySelector('.amount-field mat-error'))).toContain(
      'More than zero',
    );
    expect((root(fixture).querySelector('button.confirm-add') as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(http.match(() => true)).toEqual([]);
  });

  it('asks for an amount without pretending it is a wrong one', async () => {
    const fixture = await reachQuantity();

    type(fixture, '.amount-field input', '');

    // Only the missing value is reported: an empty field is not a zero.
    expect(form(fixture).controls.amount.hasError('required')).toBe(true);
    expect(form(fixture).controls.amount.hasError('positive')).toBe(false);
    expect(text(root(fixture).querySelector('.amount-field mat-error'))).toContain(
      'Enter an amount',
    );
    expect(http.match(() => true)).toEqual([]);
  });

  it('shows the 409 with a way out to the line that already holds the food', async () => {
    const fixture = await readyToAdd('1');
    click(fixture, 'button.confirm-add');

    http
      .expectOne('/api/pantry')
      .flush(errorResponse(409, CONFLICT), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    // The backend's own wording, which is what points at the adjustment.
    expect(text(root(fixture).querySelector('.add-error'))).toBe(CONFLICT);
    // A duplicate is not a dead end: the dialog can hand the user over.
    expect(root(fixture).querySelector('button.goto-existing')).toBeTruthy();

    click(fixture, 'button.goto-existing');

    expect(closed()).toEqual({ kind: 'existing', fdcId: 171265, message: CONFLICT });
  });

  it('shows the 404 of a missing local copy and offers the call that repairs it', async () => {
    const fixture = await readyToAdd('1');
    click(fixture, 'button.confirm-add');

    http
      .expectOne('/api/pantry')
      .flush(errorResponse(404, NOT_MATERIALIZED), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    // The message names the very endpoint that materializes the ingredient.
    expect(text(root(fixture).querySelector('.add-error'))).toBe(NOT_MATERIALIZED);
    expect(root(fixture).querySelector('button.goto-existing')).toBeNull();

    click(fixture, 'button.reload-ingredient');
    http.expectOne('/api/catalog/ingredients/171265').flush(MILK);
    fixture.detectChanges();

    // The typed amount survives: what was broken was the ingredient, not the
    // quantity, and the second attempt is the user's decision.
    expect(root(fixture).querySelector('.add-error')).toBeNull();
    expect(form(fixture).controls.amount.value).toBe(1);
  });

  it('shows a 502 as a provider failure and retries nothing on its own', async () => {
    const fixture = await open();

    type(fixture, '.search-field input', 'milk');
    click(fixture, 'button.run-search');
    http
      .expectOne((candidate) => candidate.url === '/api/catalog/ingredients')
      .flush(
        errorResponse(
          502,
          'USDA FoodData Central rejected the request: quota exceeded',
          '/api/catalog/ingredients',
        ),
        { status: 502, statusText: 'Bad Gateway' },
      );
    fixture.detectChanges();

    const message = text(root(fixture).querySelector('.add-error'));
    // Both readings of a 502 are named, because the client cannot tell them apart
    // and they call for different patience.
    expect(message).toContain('quota is spent (30 requests an hour without an API key)');
    expect(message).toContain('quota exceeded');
    // Nothing to pick: the results of a previous query are not results of this one.
    expect(nodes(fixture, '.search-result')).toEqual([]);
    // A silent retry would spend a quota that is already gone.
    expect(http.match(() => true)).toEqual([]);
  });

  it('puts a 400 on the amount field and keeps the typed amount', async () => {
    const fixture = await readyToAdd();
    click(fixture, 'button.confirm-add');

    http.expectOne('/api/pantry').flush(errorResponse(400, 'amount must be greater than 0'), {
      status: 400,
      statusText: 'Bad Request',
    });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.amount-field mat-error'))).toBe(
      'amount must be greater than 0',
    );
    expect(form(fixture).controls.amount.value).toBe(2);
  });

  it('keeps a failed detail read on the results so another food can be picked', async () => {
    const fixture = await open();
    await search(fixture, 'milk');

    click(fixture, '.search-result');
    http
      .expectOne('/api/catalog/ingredients/171265')
      .flush(errorResponse(404, 'Ingredient 171265 not found', '/api/catalog/ingredients/171265'), {
        status: 404,
        statusText: 'Not Found',
      });
    fixture.detectChanges();

    expect(text(root(fixture).querySelector('.add-error'))).toBe('Ingredient 171265 not found');
    // The results are still there: this food is a dead end, the search is not.
    expect(nodes(fixture, '.search-result').length).toBe(2);
    expect(root(fixture).querySelector('.chosen-name')).toBeNull();
  });

  it('goes back to the results keeping the query when the food is changed', async () => {
    const fixture = await readyToAdd();

    click(fixture, 'button.change-food');

    // The query and the results survive: coming back is not a second search, and
    // picking another food is what resets the quantity, not this step.
    expect((root(fixture).querySelector('.search-field input') as HTMLInputElement).value).toBe(
      'food',
    );
    expect(nodes(fixture, '.search-result').length).toBe(1);
    expect(root(fixture).querySelector('.amount-field')).toBeNull();
  });

  it('closes with nothing when the dialog is cancelled', async () => {
    const fixture = await open();

    click(fixture, 'button.cancel-add');

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
