import { PantryItem } from '../../core/pantry/pantry.models';
import {
  LOW_STOCK_THRESHOLD,
  deltaToTarget,
  filterPantryItems,
  groupByCategory,
  stepForUnit,
} from './pantry-helpers';

/** A minimal pantry line; every test overrides only the fields it cares about. */
const item = (overrides: Partial<PantryItem>): PantryItem => ({
  id: 1,
  fdcId: 2,
  name: 'Thing',
  category: 'COUNT',
  quantity: 5,
  unitCode: 'UNIT',
  location: null,
  note: null,
  version: 0,
  updatedAt: '2026-10-01T00:00:00Z',
  ...overrides,
});

describe('pantry helpers', () => {
  it('returns sensible steps per unit', () => {
    expect(stepForUnit('UNIT')).toBe(1);
    expect(stepForUnit('gram')).toBe(10);
    expect(stepForUnit('ML')).toBe(10);
    expect(stepForUnit('kg')).toBe(50);
    expect(stepForUnit('L')).toBe(50);
    expect(stepForUnit('tsp')).toBe(1);
    expect(stepForUnit('tablespoon')).toBe(1);
    expect(stepForUnit('unknown-code')).toBe(1);
  });

  it('computes a signed delta to a target', () => {
    expect(deltaToTarget(244, 250)).toBe(6);
    expect(deltaToTarget(500, 450)).toBe(-50);
    expect(deltaToTarget(10, 10)).toBe(0);
    expect(deltaToTarget(NaN, 10)).toBe(0);
    expect(deltaToTarget(10, Infinity)).toBe(0);
  });

  describe('filterPantryItems', () => {
    it('matches the name case-insensitively, ignoring surrounding spaces', () => {
      const items = [item({ id: 1, name: 'Milk, whole' }), item({ id: 2, name: 'Yogurt, plain' })];

      expect(
        filterPantryItems(items, { query: '  MILK  ', category: null, lowStockOnly: false }).map(
          (line) => line.id,
        ),
      ).toEqual([1]);
      // An empty query is no filter at all; the whole list comes back intact.
      expect(filterPantryItems(items, { query: '', category: null, lowStockOnly: false })).toEqual(
        items,
      );
    });

    it('narrows by category without re-sorting', () => {
      const items = [
        item({ id: 1, category: 'Dairy', name: 'Milk' }),
        item({ id: 2, category: 'Legumes', name: 'Lentils' }),
        item({ id: 3, category: 'Dairy', name: 'Yogurt' }),
      ];

      const dairy = filterPantryItems(items, {
        query: '',
        category: 'Dairy',
        lowStockOnly: false,
      });
      expect(dairy.map((line) => line.name)).toEqual(['Milk', 'Yogurt']);
    });

    it('keeps only zeroed or low lines when lowStockOnly is on', () => {
      const items = [
        item({ id: 1, quantity: 0 }),
        item({ id: 2, quantity: LOW_STOCK_THRESHOLD }),
        item({ id: 3, quantity: LOW_STOCK_THRESHOLD + 1 }),
      ];

      expect(
        filterPantryItems(items, { query: '', category: null, lowStockOnly: true }).map(
          (line) => line.id,
        ),
      ).toEqual([1, 2]);
    });

    it('combines the filters with AND', () => {
      const items = [
        item({ id: 1, name: 'Salt', quantity: 2 }),
        item({ id: 2, name: 'Milk, whole', quantity: 244 }),
      ];

      const narrow = filterPantryItems(items, {
        query: 's',
        category: 'COUNT',
        lowStockOnly: true,
      });
      expect(narrow.map((line) => line.name)).toEqual(['Salt']);
    });
  });

  describe('groupByCategory', () => {
    it('groups consecutive lines into one section per category', () => {
      const items = [item({ category: 'A' }), item({ category: 'A' }), item({ category: 'B' })];

      expect(groupByCategory(items)).toEqual([
        { category: 'A', items: [item({ category: 'A' }), item({ category: 'A' })] },
        { category: 'B', items: [item({ category: 'B' })] },
      ]);
    });

    it('never merges two runs of the same category', () => {
      const items = [
        item({ id: 1, category: 'A' }),
        item({ id: 2, category: 'B' }),
        item({ id: 3, category: 'A' }),
      ];
      const sections = groupByCategory(items);

      // The order the API chose survives: A, B, A is three sections, because the
      // server decided the middle run belongs between the two A rows.
      expect(sections.map((section) => section.category)).toEqual(['A', 'B', 'A']);
    });

    it('answers an empty list with no sections', () => {
      expect(groupByCategory([])).toEqual([]);
    });
  });
});
