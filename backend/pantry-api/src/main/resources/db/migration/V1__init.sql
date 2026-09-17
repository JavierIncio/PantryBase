CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE units
(
    id        SMALLINT PRIMARY KEY,
    code      VARCHAR(16) NOT NULL UNIQUE, -- GRAM | ML | UNIT
    canonical BOOLEAN     NOT NULL DEFAULT false,
    category  VARCHAR(32)                  -- SOLID | LIQUID | COUNTABLE
);

CREATE TABLE measure_conversion
(
    id                  BIGSERIAL PRIMARY KEY,
    source_unit_id      SMALLINT NOT NULL REFERENCES units (id),
    ingredient_category VARCHAR(50),
    density_g_per_ml    DECIMAL(12, 6),
    UNIQUE (source_unit_id, ingredient_category)
);

CREATE TABLE allergens
(
    id   SMALLINT PRIMARY KEY,
    code VARCHAR(20)  NOT NULL UNIQUE, -- PEANUT, GLUTEN, LACTOSE...
    name VARCHAR(100) NOT NULL
);