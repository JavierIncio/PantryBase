import {
  IngredientCoverage,
  RecipeDetail,
  RecipeIngredient,
  RecipeSummary,
} from './recipes.models';
import {
  coverageClass,
  coveragePercent,
  formatAmount,
  orderByCoverage,
  pairCoverage,
  passesThreshold,
  recipeAvailability,
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
  favorite: false,
  saved: false,
  owned: true,
  published: false,
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
    matched: true,
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
    matched: true,
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

  it('never pairs an unmatched line, which has no id the coverage could match', () => {
    const uncatalogued: RecipeIngredient = {
      ...egg,
      ingredientId: null,
      ingredientName: 'Apples',
      matched: false,
      position: 2,
    };

    const rows = pairCoverage([uncatalogued], [covered(10)]);

    expect(rows[0].coverage).toBeNull();
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

describe('recipeAvailability', () => {
  const cov = (
    ingredientId: number,
    fullyCovered: boolean,
    availableAmount: number,
  ): IngredientCoverage => ({
    ingredientId,
    ingredientName: `ing-${ingredientId}`,
    neededAmount: 100,
    neededUnitCode: 'GRAM',
    availableAmount,
    availableUnitCode: 'GRAM',
    fullyCovered,
  });

  const unmatched = (position: number): RecipeIngredient => ({
    ingredientId: null,
    ingredientName: 'Mystery Powder',
    ingredientCategory: null,
    originalAmount: null,
    originalUnitCode: null,
    canonicalAmount: null,
    canonicalUnitCode: null,
    canonicalUnitCategory: null,
    matched: false,
    position,
  });

  const detail = (
    coverage: IngredientCoverage[],
    ingredients: RecipeIngredient[] = [],
  ): RecipeDetail => ({
    id: 1,
    source: 'USER',
    externalId: null,
    name: 'Test',
    category: '',
    area: '',
    instructions: '',
    imageUrl: null,
    videoUrl: null,
    tags: [],
    servings: null,
    sourceUrl: null,
    ingredients,
    nutrients: null,
    allergenCodes: [],
    matchScore: null,
    coverage,
    favorite: false,
    saved: false,
    owned: true,
    published: false,
  });

  it('splits matched lines into covered, low and missing by stock', () => {
    // 100/100 covered; 40/100 present but short; 0/100 a true absence.
    const result = recipeAvailability(
      detail([cov(1, true, 100), cov(2, false, 40), cov(3, false, 0)]),
    );

    expect(result.covered.map((row) => row.ingredientId)).toEqual([1]);
    expect(result.low.map((row) => row.ingredientId)).toEqual([2]);
    expect(result.missing.map((row) => row.ingredientId)).toEqual([3]);
    expect(result.uncatalogued).toEqual([]);
    expect(result.hasGaps).toBe(true);
  });

  it('tells a low line from a missing one by the stock boundary at zero', () => {
    // Exactly zero available is "missing", any positive leftover is "low": the
    // two prompt different actions, so the boundary must not blur.
    const result = recipeAvailability(detail([cov(1, false, 0.0001), cov(2, false, 0)]));

    expect(result.low.map((row) => row.ingredientId)).toEqual([1]);
    expect(result.missing.map((row) => row.ingredientId)).toEqual([2]);
  });

  it('never reclassifies a fully covered line even with zero available', () => {
    // `fullyCovered` is the server's verdict and wins over the amount read.
    const result = recipeAvailability(detail([cov(1, true, 0)]));

    expect(result.covered).toHaveLength(1);
    expect(result.low).toHaveLength(0);
    expect(result.missing).toHaveLength(0);
    expect(result.hasGaps).toBe(false);
  });

  it('lists uncatalogued lines and counts them as gaps on their own', () => {
    const result = recipeAvailability(
      detail([cov(1, true, 100)], [unmatched(1), { ...unmatched(2), matched: true }]),
    );

    // Only `matched: false` lines qualify, whatever the coverage says.
    expect(result.uncatalogued.map((line) => line.ingredientName)).toEqual(['Mystery Powder']);
    expect(result.hasGaps).toBe(true);
  });

  it('reports no gaps when every line is covered and catalogued', () => {
    const result = recipeAvailability(detail([cov(1, true, 100), cov(2, true, 200)]));

    expect(result.covered).toHaveLength(2);
    expect(result.hasGaps).toBe(false);
    expect(result.low).toEqual([]);
    expect(result.missing).toEqual([]);
    expect(result.uncatalogued).toEqual([]);
  });
});
