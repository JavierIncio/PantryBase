-- H4 amendment: coverage is measured over ALL provider lines, not just the ones
-- that matched the catalog. A line with no counterpart used to be dropped, which
-- inflated the match score: a stew of 20 ingredients with only one understood
-- claimed 1/1 = 100 %. Unmatched lines are now stored with matched=false and no
-- catalog id, so the denominator reflects the real dish and 100% requires every
-- line to match AND be stocked.

ALTER TABLE recipe_ingredients ALTER COLUMN ingredient_id DROP NOT NULL;
ALTER TABLE recipe_ingredients ALTER COLUMN original_amount DROP NOT NULL;
ALTER TABLE recipe_ingredients ALTER COLUMN original_unit_code DROP NOT NULL;
ALTER TABLE recipe_ingredients ALTER COLUMN canonical_amount DROP NOT NULL;
ALTER TABLE recipe_ingredients ALTER COLUMN canonical_unit_code DROP NOT NULL;

ALTER TABLE recipe_ingredients ADD COLUMN matched BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE recipe_ingredients ADD COLUMN ingredient_name VARCHAR(255);

-- matched:      this line links to a catalog ingredient and can therefore be covered.
--               false = the provider wording is kept as-is but the line contributes
--               nothing to coverage.
-- ingredient_name: provider ingredient text when matched=false; null on matched
--                  lines, where the catalog row is the source of truth.