import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { IngredientDetail, IngredientSearchHit, Unit } from '../../core/catalog/catalog.models';
import { RecipeDetail } from '../../core/recipes/recipes.models';
import { RecipeCreatePage } from './recipe-create-page';

/** The three units the fixture offers; the endpoint has twelve. */
const UNITS: Unit[] = [
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'ML', name: 'Milliliter', category: 'VOLUME', canonical: true },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

/** A catalog hit, exactly as `GET /api/catalog/ingredients?query=` answers it. */
const CHICKEN_HIT: IngredientSearchHit = {
  fdcId: 170000,
  name: 'Chicken, broilers or fryers, breast, meat only',
  dataType: 'Foundation',
};

/**
 * The detail of that hit: its `id` is the internal key `POST /api/recipes`
 * addresses the line by, which the hit itself does not carry.
 */
const CHICKEN: IngredientDetail = {
  id: 42,
  fdcId: CHICKEN_HIT.fdcId,
  name: CHICKEN_HIT.name,
  dataType: CHICKEN_HIT.dataType,
  category: 'Poultry Products',
  nutrients: { energyKcal: 120, proteinG: 22.5, fatG: 2.6, carbsG: 0 },
  densityClass: null,
  measures: [],
};

/** What the create endpoint answers: the stored, private recipe. */
const CREATED: RecipeDetail = {
  id: 55,
  source: 'USER',
  externalId: null,
  name: 'Pollo al horno',
  category: 'Main Course',
  area: '',
  instructions: 'Hornea el pollo.',
  imageUrl: null,
  videoUrl: null,
  tags: [],
  servings: 2,
  sourceUrl: null,
  ingredients: [
    {
      ingredientId: 42,
      ingredientName: CHICKEN.name,
      ingredientCategory: CHICKEN.category,
      originalAmount: 300,
      originalUnitCode: 'GRAM',
      canonicalAmount: 300,
      canonicalUnitCode: 'GRAM',
      canonicalUnitCategory: 'WEIGHT',
      matched: true,
      position: 1,
    },
  ],
  nutrients: null,
  allergenCodes: [],
  matchScore: null,
  coverage: [],
  favorite: false,
  saved: false,
  owned: true,
  published: false,
};

/** Backend error body, as `ErrorResponse` serializes it on the wire. */
const errorResponse = (status: number, message: string) => ({
  timestamp: '2026-10-16T09:00:00Z',
  status,
  error: 'Error',
  message,
  path: '/api/recipes',
});

describe('RecipeCreatePage', () => {
  let http: HttpTestingController;

  const configure = async () => {
    await TestBed.configureTestingModule({
      imports: [RecipeCreatePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  };

  /** Creates the page and answers the unit catalog it asks for on init. */
  const render = async () => {
    await configure();
    const fixture = TestBed.createComponent(RecipeCreatePage);
    fixture.detectChanges();
    http.expectOne('/api/units').flush(UNITS);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<RecipeCreatePage>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  const row = (fixture: ComponentFixture<RecipeCreatePage>, index = 0): HTMLElement =>
    root(fixture).querySelectorAll('.ingredient-row')[index] as HTMLElement;

  const submit = (fixture: ComponentFixture<RecipeCreatePage>): void => {
    root(fixture).querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  /**
   * Types into a row's ingredient box and lets its debounce fire the catalog
   * search, the same fake-timer dance the recipe grid's filter uses.
   */
  const typeIngredient = (fixture: ComponentFixture<RecipeCreatePage>, value: string): void => {
    const input = row(fixture).querySelector('.ingredient-field input') as HTMLInputElement;
    input.focus();
    vi.useFakeTimers();
    try {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      vi.advanceTimersByTime(300);
    } finally {
      vi.useRealTimers();
    }
    fixture.detectChanges();
  };

  /** Drives a full pick: type, answer the search, click the option, resolve it. */
  const pickChicken = async (fixture: ComponentFixture<RecipeCreatePage>): Promise<void> => {
    typeIngredient(fixture, 'chicken');

    const catalogReq = http.expectOne((req) => req.url === '/api/catalog/ingredients');
    expect(catalogReq.request.params.get('query')).toBe('chicken');
    catalogReq.flush([CHICKEN_HIT]);
    fixture.detectChanges();
    await fixture.whenStable();

    const option = document.querySelector('.mat-mdc-autocomplete-panel mat-option') as HTMLElement;
    option.click();
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectOne(`/api/catalog/ingredients/${CHICKEN_HIT.fdcId}`).flush(CHICKEN);
    fixture.detectChanges();
  };

  afterEach(() => {
    const pending = http?.match(() => true) ?? [];
    expect(pending.map((req) => req.request.urlWithParams)).toEqual([]);
  });

  it('starts with one empty ingredient row and no way to remove it', async () => {
    const fixture = await render();

    expect(root(fixture).querySelectorAll('.ingredient-row')).toHaveLength(1);
    // A recipe needs at least one line, so the only remove button is disabled.
    expect((row(fixture).querySelector('.remove-row') as HTMLButtonElement).disabled).toBe(true);
  });

  it('adds a row and removes a non-last one', async () => {
    const fixture = await render();

    (root(fixture).querySelector('.add-row') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root(fixture).querySelectorAll('.ingredient-row')).toHaveLength(2);

    (row(fixture, 0).querySelector('.remove-row') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root(fixture).querySelectorAll('.ingredient-row')).toHaveLength(1);

    // The remaining row is the last one again: its remove button locks.
    expect((row(fixture).querySelector('.remove-row') as HTMLButtonElement).disabled).toBe(true);
  });

  it('keeps the submit button disabled until a row is fully resolved', async () => {
    const fixture = await render();
    const button = root(fixture).querySelector('.submit-create') as HTMLButtonElement;
    expect(button.disabled).toBe(true);

    // Name alone is not enough: the ingredient row is still empty.
    fixture.componentInstance.form.controls.name.setValue('Pollo al horno');
    fixture.detectChanges();
    expect(button.disabled).toBe(true);

    await pickChicken(fixture);
    const rowGroup = fixture.componentInstance.ingredients.at(0);
    // The pick resolved to the internal id, and the row says so.
    expect(rowGroup.controls.ingredient.value).toEqual({ id: 42, name: CHICKEN_HIT.name });
    expect(rowGroup.controls.ingredient.invalid).toBe(false);
    expect(button.disabled).toBe(true);

    rowGroup.controls.amount.setValue(300);
    rowGroup.controls.unitCode.setValue('GRAM');
    fixture.detectChanges();
    expect(button.disabled).toBe(false);
  });

  it('sends the exact CreateRecipeRequest and opens the new recipe', async () => {
    const fixture = await render();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const form = fixture.componentInstance.form;
    form.controls.name.setValue('  Pollo al horno  ');
    form.controls.category.setValue('  Main Course  ');
    form.controls.area.setValue('   ');
    form.controls.servings.setValue(2);
    form.controls.instructions.setValue('Hornea el pollo.');
    await pickChicken(fixture);
    const rowGroup = fixture.componentInstance.ingredients.at(0);
    rowGroup.controls.amount.setValue(300);
    rowGroup.controls.unitCode.setValue('GRAM');
    fixture.detectChanges();

    submit(fixture);

    const req = http.expectOne('/api/recipes');
    expect(req.request.method).toBe('POST');
    // Optional text is trimmed and a blank is null (replacement semantics);
    // positions are the row order; fields this page does not collect travel
    // as explicit nulls rather than absent keys.
    expect(req.request.body).toEqual({
      name: 'Pollo al horno',
      category: 'Main Course',
      area: null,
      instructions: 'Hornea el pollo.',
      imageUrl: null,
      videoUrl: null,
      tags: null,
      servings: 2,
      sourceUrl: null,
      ingredients: [{ ingredientId: 42, amount: 300, unitCode: 'GRAM', position: 1 }],
    });
    req.flush(CREATED);
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/recipes', 55]);
  });

  it('numbers the lines by row order when several are sent', async () => {
    const fixture = await render();
    // The create succeeds here, so the page navigates: the spy keeps that
    // intent out of the route-less test router.
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (root(fixture).querySelector('.add-row') as HTMLButtonElement).click();
    fixture.detectChanges();

    fixture.componentInstance.form.controls.name.setValue('Guiso');
    // Fill the first row directly; the second is filled through the full pick
    // so its internal id comes from the catalog the way it does for a user.
    const first = fixture.componentInstance.ingredients.at(0);
    first.controls.ingredient.setValue({ id: 42, name: CHICKEN.name });
    first.controls.amount.setValue(300);
    first.controls.unitCode.setValue('GRAM');
    const second = fixture.componentInstance.ingredients.at(1);
    second.controls.ingredient.setValue({ id: 7, name: 'Salt' });
    second.controls.amount.setValue(1);
    second.controls.unitCode.setValue('GRAM');
    fixture.detectChanges();

    submit(fixture);

    const req = http.expectOne('/api/recipes');
    expect(req.request.body.ingredients).toEqual([
      { ingredientId: 42, amount: 300, unitCode: 'GRAM', position: 1 },
      { ingredientId: 7, amount: 1, unitCode: 'GRAM', position: 2 },
    ]);
    req.flush(CREATED);
  });

  it('marks the rows touched and sends nothing while the form is invalid', async () => {
    const fixture = await render();

    submit(fixture);

    expect(http.match(() => true)).toEqual([]);
    // The untouched error text appears only after the failed submit, the same
    // contract the register page keeps.
    expect(root(fixture).textContent).toContain('The recipe needs a name.');
    expect(root(fixture).textContent).toContain('Enter an amount.');
  });

  it('keeps the form and shows the backend message when the create is refused', async () => {
    const fixture = await render();

    fixture.componentInstance.form.controls.name.setValue('Pollo');
    const rowGroup = fixture.componentInstance.ingredients.at(0);
    rowGroup.controls.ingredient.setValue({ id: 999, name: 'Ghost' });
    rowGroup.controls.amount.setValue(1);
    rowGroup.controls.unitCode.setValue('UNIT');
    fixture.detectChanges();

    submit(fixture);

    http.expectOne('/api/recipes').flush(errorResponse(400, 'Some ingredientIds do not exist'), {
      status: 400,
      statusText: 'Bad Request',
    });
    fixture.detectChanges();

    expect(textOf(root(fixture).querySelector('.create-error'))).toBe(
      'Some ingredientIds do not exist',
    );
    // The refusal belongs to the API, not to the fields: every edit survives.
    expect(fixture.componentInstance.form.controls.name.value).toBe('Pollo');
    expect(root(fixture).querySelectorAll('.ingredient-row')).toHaveLength(1);
  });

  it('clears the pick and names the failure when a detail read cannot resolve it', async () => {
    const fixture = await render();
    typeIngredient(fixture, 'chicken');

    const catalogReq = http.expectOne((req) => req.url === '/api/catalog/ingredients');
    catalogReq.flush([CHICKEN_HIT]);
    fixture.detectChanges();
    await fixture.whenStable();

    (document.querySelector('.mat-mdc-autocomplete-panel mat-option') as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    http
      .expectOne(`/api/catalog/ingredients/${CHICKEN_HIT.fdcId}`)
      .flush(errorResponse(502, 'The food provider could not answer.'), {
        status: 502,
        statusText: 'Bad Gateway',
      });
    fixture.detectChanges();

    // The unusable pick is dropped from the box and the row says why; the
    // control is back to "unresolved", so the line can never be sent.
    const rowGroup = fixture.componentInstance.ingredients.at(0);
    expect(rowGroup.controls.ingredient.value).toBeNull();
    expect(textOf(row(fixture).querySelector('.mat-mdc-form-field-error'))).toBe(
      'The food provider could not answer.',
    );
  });
});

/** Rendered text with the template's whitespace collapsed. */
function textOf(node: Element | null | undefined): string {
  return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}
