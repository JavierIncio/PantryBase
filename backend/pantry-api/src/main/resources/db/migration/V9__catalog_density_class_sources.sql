-- Provenance of a density value. A class density is a curated claim, so the seed
-- records how each value was obtained; the ones added here are derived from the
-- provider's own household portion rather than from an unsourced constant.
ALTER TABLE density_classes
    ADD COLUMN source VARCHAR(255);

-- MILK: fdcId 171265 "Milk, whole, 3.25% milkfat" reports 1 cup = 244 g, so
-- 244 g / 236.588 ml = 1.031288 g/ml.
INSERT INTO density_classes (code, density_g_per_ml, source)
VALUES ('MILK', 1.031288, 'FDC fdcId 171265: 1 cup = 244 g');

-- Cheese is deliberately absent: FDC fdcId 169901 gives 1 cup = 224 g for grated
-- cheddar (0.946715 g/ml) while a block of cheddar sits near 0.40 g/ml, so a single
-- CHEESE class would be off by more than 2x for whichever form it was not measured on.
-- It only becomes valid once a class is narrow enough to name the form.
