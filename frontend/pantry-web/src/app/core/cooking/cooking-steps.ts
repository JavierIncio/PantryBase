/**
 * Turns a recipe's free-text instructions into the steps of a cooking checklist.
 *
 * The backend models no structured steps: a recipe keeps `instructions` as
 * prose (TheMealDB writes one step per line) and a cooking session stores only
 * its status. The checklist the cooking page shows is therefore a local reading
 * of the text, and its progress is **not persisted anywhere** — ticking a step
 * is a convenience for the cook, not a fact the server knows.
 *
 * The visual order of these steps is stable for a given text, so the page can
 * key its local progress by index without the server ever agreeing to it.
 */

/** One non-empty, trimmed line of text. */
const clean = (parts: readonly string[]): string[] =>
  parts.map((part) => part.trim()).filter((part) => part !== '');

/**
 * Splits the instructions into steps.
 *
 * Line breaks come first because that is how the provider writes steps; a
 * single block of prose is then split at sentence ends, because a one-paragraph
 * recipe would otherwise become a checklist with a single item. A text with
 * neither line breaks nor sentence punctuation stays one step, which is the
 * honest reading of it.
 */
export function splitSteps(instructions: string): string[] {
  const lines = clean(instructions.split(/\r?\n/));
  if (lines.length > 1) {
    return lines;
  }
  return clean(instructions.split(/(?<=[.!?])\s+/));
}
