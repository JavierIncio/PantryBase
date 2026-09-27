DROP TABLE measure_conversion;

ALTER TABLE units
    ADD COLUMN name VARCHAR(32),
    DROP COLUMN id,
    ADD PRIMARY KEY (code);

CREATE TABLE measure_conversions
(
    unit_code           VARCHAR(16)    PRIMARY KEY REFERENCES units (code),
    canonical_unit_code VARCHAR(16)    NOT NULL REFERENCES units (code),
    conversion_factor   DECIMAL(12, 6) NOT NULL,
    CHECK (unit_code <> canonical_unit_code)
);

CREATE TABLE ingredient_densities
(
    ingredient_category VARCHAR(50) PRIMARY KEY,
    density_g_per_ml    DECIMAL(12, 6) NOT NULL
);

INSERT INTO units (code, canonical, category, name)
VALUES ('GRAM', true, 'WEIGHT', 'Gram'),
       ('ML', true, 'VOLUME', 'Milliliter'),
       ('UNIT', true, 'COUNT', 'Unit'),
       ('KG', false, 'WEIGHT', 'Kilogram'),
       ('OZ', false, 'WEIGHT', 'Ounce'),
       ('LB', false, 'WEIGHT', 'Pound'),
       ('L', false, 'VOLUME', 'Liter'),
       ('CUP', false, 'VOLUME', 'Cup'),
       ('TBSP', false, 'VOLUME', 'Tablespoon'),
       ('TSP', false, 'VOLUME', 'Teaspoon'),
       ('PINT', false, 'VOLUME', 'Pint');

INSERT INTO measure_conversions (unit_code, canonical_unit_code, conversion_factor)
VALUES ('KG', 'GRAM', 1000),
       ('OZ', 'GRAM', 28.3495),
       ('LB', 'GRAM', 453.592),
       ('L', 'ML', 1000),
       ('CUP', 'ML', 236.588),
       ('TBSP', 'ML', 14.7868),
       ('TSP', 'ML', 4.92892),
       ('PINT', 'ML', 473.176);

INSERT INTO ingredient_densities (ingredient_category, density_g_per_ml)
VALUES ('FLOUR', 0.53),
       ('SUGAR', 0.85),
       ('OIL', 0.92);