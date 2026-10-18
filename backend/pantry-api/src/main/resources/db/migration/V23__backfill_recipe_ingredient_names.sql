-- Backfill the display name of matched recipe lines.
--
-- recipe_ingredients.ingredient_name was only written for the provider lines that did
-- not match the catalog (matched = false), so every matched line (catalog id set) was
-- left NULL. Consumers that render a line straight from the row —cooking deductions and
-- profile stats— read this column and showed a blank label. The write paths now store
-- the catalog name for matched lines; this backfills the rows materialised before the
-- fix, taking the name from the catalog by ingredient id.
UPDATE recipe_ingredients ri
SET ingredient_name = i.name
FROM ingredients i
WHERE ri.ingredient_id = i.id
  AND ri.ingredient_name IS NULL;
