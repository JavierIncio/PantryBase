-- Household measures captured from the provider (USDA FDC), per ingredient.
-- gram_per_unit is the weight in grams of one unit of the measure (e.g. one US cup).
CREATE TABLE ingredient_measures
(
    ingredient_id BIGINT           NOT NULL REFERENCES ingredients (id),
    unit_code     VARCHAR(16)      NOT NULL REFERENCES units (code),
    gram_per_unit DECIMAL(12, 6)   NOT NULL,
    PRIMARY KEY (ingredient_id, unit_code),
    CHECK (gram_per_unit > 0)
);

-- Fluid ounce: the provider measures liquids in 'fl oz'; needed to map those portions.
INSERT INTO units (code, canonical, category, name)
VALUES ('FLOZ', false, 'VOLUME', 'Fluid Ounce');

INSERT INTO measure_conversions (unit_code, canonical_unit_code, conversion_factor)
VALUES ('FLOZ', 'ML', 29.5735);