-- The density registry is keyed by our own density class, not by the provider category.
-- USDA FDC categories are top level only: 0100 "Dairy and Egg Products" holds both milk
-- (~1.03 g/ml) and cheddar (~0.40 g/ml), so a density per provider category would be
-- confidently wrong. The name is corrected here so nobody tries to seed it that way.
ALTER TABLE ingredient_densities RENAME TO density_classes;
ALTER TABLE density_classes RENAME COLUMN ingredient_category TO code;

-- An ingredient may be assigned to a density class. This is curated data, not provider
-- data: materialization only ever inserts (ON CONFLICT DO NOTHING), so the assignment
-- survives every later provider lookup.
ALTER TABLE ingredients
    ADD COLUMN density_class VARCHAR(50) REFERENCES density_classes (code);
