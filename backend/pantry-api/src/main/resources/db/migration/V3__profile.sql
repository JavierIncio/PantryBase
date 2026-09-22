CREATE TABLE user_preferences
(
    user_id            BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    filter_mode        VARCHAR(16) NOT NULL, -- STRICT | LAX
    coverage_threshold SMALLINT    NOT NULL CHECK (coverage_threshold BETWEEN 0 AND 100),
    diet               VARCHAR(32) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_allergy_exclusions
(
    user_id     BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    allergen_id SMALLINT NOT NULL REFERENCES allergens (id),

    CONSTRAINT pk_user_allergens
        PRIMARY KEY (user_id, allergen_id)
);

INSERT INTO allergens (id, code, name)
VALUES (1, 'EGG', 'Egg'),
       (2, 'FISH', 'Fish'),
       (3, 'CRUSTACEAN', 'Crustacean'),
       (4, 'NUT', 'Tree Nut'),
       (5, 'SOY', 'Soy'),
       (6, 'SESAME', 'Sesame'),
       (7, 'CELERY', 'Celery'),
       (8, 'MUSTARD', 'Mustard'),
       (9, 'SULPHITE', 'Sulphite'),
       (10, 'MOLLUSC', 'Mollusc'),
       (11, 'LUPIN', 'Lupin'),
       (12, 'MILK', 'Milk'),
       (13, 'PEANUT', 'Peanut'),
       (14, 'GLUTEN', 'Gluten');
