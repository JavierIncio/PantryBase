import { DeductionLine, runsOut } from './cooking.models';

const line = (deducted: number, remaining: number): DeductionLine => ({
  ingredientId: 10,
  ingredientName: 'Eggs',
  unitCode: 'UNIT',
  needed: 3,
  available: deducted + remaining,
  deducted,
  remaining,
});

describe('runsOut', () => {
  it('flags an empty shelf that stock actually left', () => {
    expect(runsOut(line(1, 0))).toBe(true);
    expect(runsOut(line(3, 0))).toBe(true);
  });

  it('stays quiet while any amount survives', () => {
    expect(runsOut(line(3, 1))).toBe(false);
    expect(runsOut(line(1, 0.5))).toBe(false);
  });

  it('does not flag an already-empty shelf nothing was taken from', () => {
    // deducted 0 means there was no stock to take: the zero remaining is the
    // starting state, not a consequence of cooking, so there is nothing to warn
    // about.
    expect(runsOut(line(0, 0))).toBe(false);
  });
});
