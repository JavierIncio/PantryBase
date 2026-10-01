-- Provider data type and freshness of the materialization.
--
-- data_type: FoodProfile.dataType was part of the response but was never
-- persisted, so a detail read served from the local copy could not reproduce
-- the contract the live provider had produced. Without it the stale fallback
-- would have to return a response of a different shape than the fresh one.
--
-- synced_at: the instant of the last SUCCESSFUL provider read, deliberately
-- not created_at. Materialization has always been insert-only, so created_at
-- records when the row was first written and can never say whether the
-- nutrients still match the provider. Defaulting to now() keeps existing rows
-- usable: they are treated as freshly synced once and age from the migration.
ALTER TABLE ingredients
    ADD COLUMN data_type VARCHAR(64);

ALTER TABLE ingredients
    ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Every read of the detail path filters on the pair (fdc_id, synced_at) to
-- decide between a fresh local copy and a provider call.
CREATE INDEX idx_ingredients_fdc_id_synced_at
    ON ingredients (fdc_id, synced_at);
