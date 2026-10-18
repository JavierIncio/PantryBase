-- Diet filtering (completes the H8 diet preference). A recipe carries the restrictive
-- diets it satisfies, derived from its per-serving nutrients at materialisation time
-- (never BALANCED, which is the absence of a filter). Materialised like
-- recipe_allergens so the filter stays a single indexed lookup.
CREATE TABLE recipe_diets
(
    recipe_id BIGINT      NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    diet      VARCHAR(16) NOT NULL,
    PRIMARY KEY (recipe_id, diet)
);

CREATE INDEX idx_recipe_diets_diet ON recipe_diets (diet);

-- HIGH_FIBER and LOW_SODIUM are dropped from the enum: the stored nutrient profile
-- (energy, protein, fat, carbs) cannot derive fibre or sodium. Legacy preference
-- rows pointing at either fall back to the neutral BALANCED so the enum read never
-- fails on an unknown name.
UPDATE user_preferences SET diet = 'BALANCED' WHERE diet IN ('HIGH_FIBER', 'LOW_SODIUM');
