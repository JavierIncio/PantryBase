CREATE TABLE ingredients
(
    id          BIGSERIAL PRIMARY KEY,
    fdc_id      BIGINT       NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    category    VARCHAR(50),
    energy_kcal DOUBLE PRECISION,
    protein_g   DOUBLE PRECISION,
    fat_g       DOUBLE PRECISION,
    carbs_g     DOUBLE PRECISION,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);