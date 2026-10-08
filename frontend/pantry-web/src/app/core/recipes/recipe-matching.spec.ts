import { IngredientCoverage, RecipeIngredient, RecipeSummary } from './recipes.models';
import {
  coverageClass,
  coveragePercent,
  formatAmount,
  orderByCoverage,
  pairCoverage,
  passesThreshold,
} from './recipe-matching';

const recipe = (id: number, matchScore: number | null): RecipeSummary => ({
  id,
  source: 'USER',
  externalId: null,
  name: `Recipe ${id}`,
  category: '',
  area: '',
  imageUrl: null,
  servings: null,
  matchScore,
  missingCount: null,
  allergenCodes: [],
});

describe('coveragePercent', () => {
  it('reads a score as the percentage the badges show', () => {
    expect(coveragePercent(1)).toBe(100);
    expect(coveragePercent(0.6666)).toBe(67);
    expect(coveragePercent(0)).toBe(0);
  });

  it('keeps a missing score missing instead of inventing a zero', () => {
    // 0% would claim the pantry covers nothing; null says nobody counted.
    expect(coveragePercent(null)).toBeNull();
  });
});

describe('passesThreshold', () => {
  it('keeps a recipe whose percentage reaches the bar', () => {
    expect(passesThreshold(1, 70)).toBe(true);
    expect(passesThreshold(0.7, 70)).toBe(true);
    expect(passesThreshold(0.699, 70)).toBe(false);
  });

  it('treats an exactly covered recipe as the only one passing 100', () => {
    expect(passesThreshold(1, 100)).toBe(true);
    expect(passesThreshold(0.999, 100)).toBe(false);
  });

  it('lets a recipe without computed coverage through only at 0%', () => {
    // A score nobody computed cannot be claimed to reach any goal above zero;
    // the "show everything" position is the one position that asks nothing.
    expect(passesThreshold(null, 0)).toBe(true);
    expect(passesThreshold(null, 1)).toBe(false);
    expect(passesThreshold(null, 100)).toBe(false);
  });
});

describe('orderByCoverage', () => {
  it('sorts best covered first and recipes without a score last', () => {
    const a = recipe(1, 0.4);
    const b = recipe(2, 1);
    const c = recipe(3, null);
    const d = recipe(4, 0.8);

    expect(orderByCoverage([a, b, c, d]).map((r) => r.id)).toEqual([2, 4, 1, 3]);
  });

  it('is a stable copy: ties keep their arrival order and the input is untouched', () => {
    const a = recipe(1, 0.5);
    const b = recipe(2, 0.5);
    const c = recipe(3, 0.5);

    const sorted = orderByCoverage([a, b, c]);
    expect(sorted.map((r) => r.id)).toEqual([1, 2, 3]);
    expect(sorted).not.toBe([a, b, c]);
    expect([a, b, c].map((r) => r.id)).toEqual([1, 2, 3]);
  });
});

describe('pairCoverage', () => {
  const egg: RecipeIngredient = {
    ingredientId: 10,
    ingredientName: 'Egg',
    ingredientCategory: 'Dairy and Egg Products',
    originalAmount: 2,
    originalUnitCode: 'UNIT',
    canonicalAmount: 2,
    canonicalUnitCode: 'UNIT',
    canonicalUnitCategory: 'COUNT',
    position: 0,
  };
  const flour: RecipeIngredient = {
    ingredientId: 11,
    ingredientName: 'Flour',
    ingredientCategory: 'Grains',
    originalAmount: 1,
    originalUnitCode: 'CUP',
    canonicalAmount: 150,
    canonicalUnitCode: 'GRAM',
    canonicalUnitCategory: 'WEIGHT',
    position: 1,
  };

  const covered = (ingredientId: number): IngredientCoverage => ({
    ingredientId,
    ingredientName: 'x',
    neededAmount: 1,
    neededUnitCode: 'UNIT',
    availableAmount: 3,
    availableUnitCode: 'UNIT',
    fullyCovered: true,
  });

  it('joins by ingredientId so the recipe order is the one rendered', () => {
    // Coverage arrives shuffled; the join must not care about its order.
    const rows = pairCoverage([egg, flour], [covered(11), covered(10)]);

    expect(rows.map((row) => row.ingredient.ingredientName)).toEqual(['Egg', 'Flour']);
    expect(rows.map((row) => row.coverage?.ingredientId)).toEqual([10, 11]);
  });

  it('keeps a line whose ingredient has no coverage, with null attached', () => {
    const rows = pairCoverage([egg, flour], [covered(10)]);

    expect(rows[0].coverage?.ingredientId).toBe(10);
    expect(rows[1].coverage).toBeNull();
  });

  it('drops a coverage entry without an ingredient line to attach to', () => {
    const rows = pairCoverage([egg], [covered(10), covered(99)]);

    expect(rows).toHaveLength(1);
    expect(rows[0].coverage?.ingredientId).toBe(10);
  });

  it('attaches the one coverage row to every line of a repeated ingredient', () => {
    const secondEgg = { ...egg, position: 2 };
    const rows = pairCoverage([egg, secondEgg], [covered(10)]);

    expect(rows.every((row) => row.coverage?.ingredientId === 10)).toBe(true);
  });
});

describe('coverageClass', () => {
  it('maps the three states a score can be in', () => {
    expect(coverageClass(1)).toBe('coverage-full');
    expect(coverageClass(0.7)).toBe('coverage-partial');
    expect(coverageClass(null)).toBe('coverage-none');
  });
});

describe('formatAmount', () => {
  it('strips the floating-point noise of a JSON double', () => {
    expect(formatAmount(150)).toBe('150');
    expect(formatAmount(244.57000000000002)).toBe('244.57');
    expect(formatAmount(0.1 + 0.2)).toBe('0.3');
  });
});
