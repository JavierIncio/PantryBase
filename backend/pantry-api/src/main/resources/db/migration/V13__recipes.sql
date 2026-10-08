-- Recipes and ingredients, with coverage support
-- H4: recipes, filtering by availability

CREATE TABLE recipes
(
    id              BIGSERIAL     PRIMARY KEY,
    source          VARCHAR(32)   NOT NULL,              -- THE_MEAL_DB | USER
    external_id     VARCHAR(64)   NOT NULL,              -- provider id (mealDB id) or UUID for USER
    name            VARCHAR(255)  NOT NULL,
    category        VARCHAR(100),                        -- provider category (Beef, Chicken, etc.)
    area            VARCHAR(100),                        -- cuisine area (Italian, Mexican, etc.)
    instructions    TEXT,
    image_url       VARCHAR(512),
    video_url       VARCHAR(512),
    tags            VARCHAR(512),                        -- comma-separated
    servings        INTEGER,
    source_url      VARCHAR(512),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (source, external_id)
);

CREATE INDEX idx_recipes_source ON recipes (source);
CREATE INDEX idx_recipes_name ON recipes (name);

CREATE TABLE recipe_ingredients
(
    id                    BIGSERIAL     PRIMARY KEY,
    recipe_id             BIGINT        NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    ingredient_id         BIGINT        NOT NULL REFERENCES ingredients (id),
    original_amount       DECIMAL(14,6) NOT NULL,
    original_unit_code    VARCHAR(16)   NOT NULL REFERENCES units (code),
    canonical_amount      DECIMAL(14,6) NOT NULL,
    canonical_unit_code   VARCHAR(16)   NOT NULL REFERENCES units (code),
    position              INTEGER       NOT NULL DEFAULT 0,
    UNIQUE (recipe_id, ingredient_id)
);

CREATE INDEX idx_recipe_ingredients_recipe ON recipe_ingredients (recipe_id);
CREATE INDEX idx_recipe_ingredients_ingredient ON recipe_ingredients (ingredient_id);

CREATE TABLE recipe_nutrients
(
    recipe_id      BIGINT        PRIMARY KEY REFERENCES recipes (id) ON DELETE CASCADE,
    energy_kcal    DECIMAL(10,3),
    protein_g      DECIMAL(10,3),
    fat_g          DECIMAL(10,3),
    carbs_g        DECIMAL(10,3)
);

-- Allergen mapping: curated seed, not from provider
CREATE TABLE allergen_mapping
(
    ingredient_id  BIGINT       NOT NULL REFERENCES ingredients (id) ON DELETE CASCADE,
    allergen_code  VARCHAR(32)  NOT NULL,   -- matches allergens.code (EU convention), so it can be
                                            -- compared directly against user exclusions
    PRIMARY KEY (ingredient_id, allergen_code)
);

-- Seed: common allergens mapped to ingredient categories / names.
-- Codes MUST come from the allergens catalog (V1/V3): EGG, FISH, CRUSTACEAN, NUT, ...
-- This is a starter set; extend as needed.
INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'GLUTEN'
FROM ingredients i
WHERE i.category ILIKE '%cereal%' OR i.name ILIKE '%wheat%' OR i.name ILIKE '%flour%' OR i.name ILIKE '%bread%' OR i.name ILIKE '%pasta%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'MILK'
FROM ingredients i
WHERE i.category ILIKE '%dairy%' OR i.name ILIKE '%milk%' OR i.name ILIKE '%cheese%' OR i.name ILIKE '%yogurt%' OR i.name ILIKE '%cream%' OR i.name ILIKE '%butter%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'EGG'
FROM ingredients i
WHERE (i.name ILIKE '%egg%') AND i.name NOT ILIKE '%eggplant%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'FISH'
FROM ingredients i
WHERE i.category ILIKE '%fish%' OR i.name ILIKE '%salmon%' OR i.name ILIKE '%tuna%' OR i.name ILIKE '%cod%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'CRUSTACEAN'
FROM ingredients i
WHERE i.category ILIKE '%shellfish%' OR i.name ILIKE '%shrimp%' OR i.name ILIKE '%prawn%' OR i.name ILIKE '%crab%' OR i.name ILIKE '%lobster%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'MOLLUSC'
FROM ingredients i
WHERE i.name ILIKE '%clam%' OR i.name ILIKE '%mussel%' OR i.name ILIKE '%oyster%' OR i.name ILIKE '%scallop%' OR i.name ILIKE '%squid%' OR i.name ILIKE '%octopus%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'NUT'
FROM ingredients i
WHERE i.name ILIKE '%almond%' OR i.name ILIKE '%walnut%' OR i.name ILIKE '%cashew%' OR i.name ILIKE '%pistachio%' OR i.name ILIKE '%hazelnut%' OR i.name ILIKE '%pecan%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'PEANUT'
FROM ingredients i
WHERE i.name ILIKE '%peanut%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'SOY'
FROM ingredients i
WHERE i.name ILIKE '%soy%' OR i.name ILIKE '%tofu%' OR i.name ILIKE '%tempeh%' OR i.name ILIKE '%edamame%';

INSERT INTO allergen_mapping (ingredient_id, allergen_code)
SELECT i.id, 'SESAME'
FROM ingredients i
WHERE i.name ILIKE '%sesame%' OR i.name ILIKE '%tahini%';

-- Derived allergens per recipe (materialized for fast filtering)
CREATE TABLE recipe_allergens
(
    recipe_id     BIGINT       NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    allergen_code VARCHAR(32)  NOT NULL,
    PRIMARY KEY (recipe_id, allergen_code)
);

CREATE INDEX idx_recipe_allergens_recipe ON recipe_allergens (recipe_id);