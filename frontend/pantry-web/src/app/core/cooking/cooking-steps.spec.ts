import { splitSteps } from './cooking-steps';

describe('splitSteps', () => {
  it('keeps one step per line, trimmed, with blank lines dropped', () => {
    expect(splitSteps('Boil the pasta.\n\n    Fry the bacon.\nPlate it.')).toEqual([
      'Boil the pasta.',
      'Fry the bacon.',
      'Plate it.',
    ]);
  });

  it('splits a single paragraph at sentence ends', () => {
    expect(splitSteps('Boil the pasta. Fry the bacon. Plate it.')).toEqual([
      'Boil the pasta.',
      'Fry the bacon.',
      'Plate it.',
    ]);
  });

  it('keeps a text with neither line breaks nor punctuation as one step', () => {
    expect(splitSteps('Simmer for two hours')).toEqual(['Simmer for two hours']);
  });

  it('answers an empty list for an empty or blank text', () => {
    expect(splitSteps('')).toEqual([]);
    expect(splitSteps('   \n  ')).toEqual([]);
  });
});
