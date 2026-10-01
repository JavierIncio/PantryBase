-- H2-B revalidation of the seeded density classes against the provider's own household
-- portions. No density value changes here: each seeded value was re-derived from a live
-- FDC response and held. What changes is that the provenance is recorded instead of left
-- as an unsourced constant, so a future reader can see which food the value came from and
-- re-check it rather than trusting it.
--
-- Method: for a food reporting "1 cup = X g", density = X / 236.588 ml (the CUP->ML factor
-- seeded in V5). Cross-checked with a second measure on the same food where available.

-- FLOUR: 0.53 held. fdcId 168894 and 169761 ("Wheat flour, white, all-purpose,
-- enriched, bleached", SR Legacy) both report 1 cup = 125 g = 0.528345 g/ml, which rounds
-- to the seeded 0.53. A third flour (fdcId 789890, Foundation) reports 1 cup = 125 g as
-- well, so the value does not depend on the dataset.
UPDATE density_classes
SET source = 'FDC fdcId 168894/169761: 1 cup = 125 g / 236.588 ml = 0.528345 g/ml'
WHERE code = 'FLOUR';

-- SUGAR: 0.85 held, with a known disagreement between datasets. SR Legacy fdcId 169655
-- ("Sugars, granulated") reports 1 cup = 200 g = 0.845351 g/ml, consistent with the seeded
-- 0.85. Foundation fdcId 746784 for the same food reports 1 cup = 188 g = 0.794630 g/ml,
-- about 6% lower. Both are correct for their own dataset, so the seeded value follows
-- SR Legacy and the divergence is recorded here rather than averaged away: averaging would
-- produce a number no food in the catalog actually measures at.
UPDATE density_classes
SET source = 'FDC fdcId 169655 (SR Legacy): 1 cup = 200 g = 0.845351 g/ml; '
                 'Foundation fdcId 746784 reports 188 g/cup (0.794630), so datasets differ ~6%'
WHERE code = 'SUGAR';

-- OIL: 0.92 held. fdcId 172370 ("Oil, vegetable, soybean, refined") reports three
-- measures that agree closely, which is the strongest confirmation of the three:
-- 1 cup = 218 g = 0.921394, 1 tbsp = 13.6 g = 0.919739, 1 tsp = 4.5 g = 0.913012 g/ml.
-- Palm kernel oil (fdcId 171422) reports 1 cup = 218 g too, so the value is not specific
-- to soybean. Deliberately not generalized to fats like lard or shortening, which measure
-- near 0.87 and belong in a narrower class if one is ever added.
UPDATE density_classes
SET source = 'FDC fdcId 172370: 1 cup = 218 g = 0.921394, 1 tbsp = 13.6 g = 0.919739, '
                 '1 tsp = 4.5 g = 0.913012 g/ml'
WHERE code = 'OIL';
