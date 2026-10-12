import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnitService } from '../catalog/unit.service';
import { RecipeAvailabilityPanel } from './recipe-availability-panel';
import { RecipeAvailability } from './recipe-matching';
import { IngredientCoverage, RecipeIngredient } from './recipes.models';

/** Stub that names the two units the fixtures use; the panel needs no HTTP. */
const UNITS: Pick<UnitService, 'label'> = {
  label: (code) => ({ GRAM: 'Gram', UNIT: 'Unit' })[code] ?? code,
};

const coverage = (
  ingredientId: number,
  ingredientName: string,
  neededAmount: number,
  availableAmount: number,
  fullyCovered: boolean,
): IngredientCoverage => ({
  ingredientId,
  ingredientName,
  neededAmount,
  neededUnitCode: 'GRAM',
  availableAmount,
  availableUnitCode: 'GRAM',
  fullyCovered,
});

const unmatched: RecipeIngredient = {
  ingredientId: null,
  ingredientName: 'Bicarbonate Of Soda',
  ingredientCategory: null,
  originalAmount: null,
  originalUnitCode: null,
  canonicalAmount: null,
  canonicalUnitCode: null,
  canonicalUnitCategory: null,
  matched: false,
  position: 3,
};

const availability = (overrides: Partial<RecipeAvailability> = {}): RecipeAvailability => ({
  covered: [],
  low: [],
  missing: [],
  uncatalogued: [],
  hasGaps: false,
  ...overrides,
});

describe('RecipeAvailabilityPanel', () => {
  const render = async (
    value: RecipeAvailability,
  ): Promise<ComponentFixture<RecipeAvailabilityPanel>> => {
    await TestBed.configureTestingModule({
      imports: [RecipeAvailabilityPanel],
      providers: [{ provide: UnitService, useValue: UNITS }],
    }).compileComponents();

    const fixture = TestBed.createComponent(RecipeAvailabilityPanel);
    fixture.componentRef.setInput('availability', value);
    fixture.detectChanges();
    return fixture;
  };

  const root = (fixture: ComponentFixture<RecipeAvailabilityPanel>): HTMLElement =>
    fixture.nativeElement as HTMLElement;

  const text = (node: Element | null | undefined): string =>
    (node?.textContent ?? '').replace(/\s+/g, ' ').trim();

  it('shows the positive state when nothing is missing', async () => {
    const fixture = await render(availability({ hasGaps: false }));

    expect(root(fixture).querySelector('.availability-clear')).toBeTruthy();
    expect(text(root(fixture).querySelector('.availability-clear-head'))).toContain(
      'You have everything you need for this recipe',
    );
    expect(root(fixture).querySelector('.availability-group')).toBeNull();
  });

  it('lists a low line with what it needs and what is stored', async () => {
    const fixture = await render(
      availability({
        hasGaps: true,
        low: [coverage(1, 'Flour', 200, 40, false)],
      }),
    );

    const group = root(fixture).querySelector('.availability-group');
    expect(text(group?.querySelector('.availability-group-title'))).toBe('Not enough (1)');
    expect(text(group?.querySelector('.availability-name'))).toBe('Flour');
    expect(text(group?.querySelector('.availability-figures'))).toBe(
      'need 200 Gram · have 40 Gram',
    );
  });

  it('names a missing line as absent from the pantry, with no figures', async () => {
    const fixture = await render(
      availability({
        hasGaps: true,
        missing: [coverage(1, 'Salt', 5, 0, false)],
      }),
    );

    const group = root(fixture).querySelector('.availability-group');
    expect(text(group?.querySelector('.availability-group-title'))).toBe('Missing (1)');
    expect(text(group?.querySelector('.availability-name'))).toBe('Salt');
    expect(text(group?.querySelector('.availability-figures'))).toBe('not in your pantry');
  });

  it('marks uncatalogued lines as impossible to verify', async () => {
    const fixture = await render(availability({ hasGaps: true, uncatalogued: [unmatched] }));

    const group = root(fixture).querySelector('.availability-group');
    expect(text(group?.querySelector('.availability-group-title'))).toBe('Not in the catalog (1)');
    expect(text(group?.querySelector('.availability-name'))).toBe('Bicarbonate Of Soda');
    expect(text(group?.querySelector('.availability-figures'))).toBe(
      "can't be verified against your pantry",
    );
  });

  it('renders the three gap groups at once, each with its own count', async () => {
    const fixture = await render(
      availability({
        hasGaps: true,
        low: [coverage(1, 'Flour', 200, 40, false)],
        missing: [coverage(2, 'Salt', 5, 0, false), coverage(3, 'Pepper', 5, 0, false)],
        uncatalogued: [unmatched],
      }),
    );

    expect(
      [...root(fixture).querySelectorAll('.availability-group-title')].map((node) => text(node)),
    ).toEqual(['Not enough (1)', 'Missing (2)', 'Not in the catalog (1)']);
  });
});
