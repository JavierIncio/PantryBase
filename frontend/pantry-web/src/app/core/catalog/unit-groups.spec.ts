import { IngredientMeasure, Unit } from './catalog.models';
import { buildUnitGroups } from './unit-groups';

/** Catalog order is by code, which is the order the groups keep. */
const UNITS: Unit[] = [
  { code: 'CUP', name: 'Cup', category: 'VOLUME', canonical: false },
  { code: 'FLOZ', name: 'Fluid ounce', category: 'VOLUME', canonical: false },
  { code: 'GRAM', name: 'Gram', category: 'WEIGHT', canonical: true },
  { code: 'KG', name: 'Kilogram', category: 'WEIGHT', canonical: false },
  { code: 'LB', name: 'Pound', category: 'WEIGHT', canonical: false },
  { code: 'UNIT', name: 'Unit', category: 'COUNT', canonical: true },
];

/** What the provider weighed for a cup of flour, in cups and pounds. */
const FLOUR_MEASURES: IngredientMeasure[] = [
  { unitCode: 'CUP', gramPerUnit: 125 },
  { unitCode: 'LB', gramPerUnit: 454 },
];

describe('buildUnitGroups', () => {
  it('groups every unit of the catalog by its category, in the declared order', () => {
    const groups = buildUnitGroups(UNITS);

    expect(groups.map((group) => [group.category, group.title])).toEqual([
      ['WEIGHT', 'Weight'],
      ['VOLUME', 'Volume'],
      ['COUNT', 'Count'],
    ]);
    // Not one unit is dropped: whether a pair of units converts depends on the
    // density of the ingredient, which only the server knows, so hiding "just in
    // case" would hide pairs that do work.
    expect(groups.flatMap((group) => group.options.map((option) => option.code))).toEqual([
      'GRAM',
      'KG',
      'LB',
      'CUP',
      'FLOZ',
      'UNIT',
    ]);
  });

  it('puts the units the provider weighed first, with their weight, and keeps the rest after', () => {
    const weight = buildUnitGroups(UNITS, FLOUR_MEASURES).find(
      (group) => group.category === 'WEIGHT',
    );

    expect(weight?.options).toEqual([
      { code: 'LB', label: 'Pound', gramPerUnit: 454 },
      { code: 'GRAM', label: 'Gram', gramPerUnit: null },
      { code: 'KG', label: 'Kilogram', gramPerUnit: null },
    ]);
  });

  it('leaves the catalog order untouched inside the unmeasured tail', () => {
    const volume = buildUnitGroups(UNITS, FLOUR_MEASURES).find(
      (group) => group.category === 'VOLUME',
    );

    // A stable partition, not a sort: one measured measure does not reorder the
    // rest of the catalog by accident.
    expect(volume?.options.map((option) => option.code)).toEqual(['CUP', 'FLOZ']);
  });

  it('carries no weight at all when the provider weighed no measure', () => {
    const groups = buildUnitGroups(UNITS);

    expect(
      groups.flatMap((group) => group.options).every((option) => option.gramPerUnit === null),
    ).toBe(true);
    expect(groups.flatMap((group) => group.options).map((option) => option.code)).toContain('CUP');
  });

  it('ignores a measure of a unit the catalog does not have', () => {
    const groups = buildUnitGroups(UNITS, [{ unitCode: 'TBSP', gramPerUnit: 15 }]);

    // The unit catalog is the one truth about which units exist; an extra measure
    // cannot add a unit nobody can select.
    expect(groups.flatMap((group) => group.options.map((option) => option.code))).not.toContain(
      'TBSP',
    );
  });

  it('shows an unnamed unit as its code rather than as nothing', () => {
    const unnamed: Unit[] = [{ code: 'XXX', name: '', category: 'COUNT', canonical: false }];

    expect(buildUnitGroups(unnamed)[0].options[0].label).toBe('XXX');
  });

  it('offers nothing when the catalog has not loaded, instead of failing', () => {
    // The selector reads this before the catalog answers; an exception here would
    // take the dialog down over a catalog of labels.
    expect(buildUnitGroups([])).toEqual([]);
  });
});
