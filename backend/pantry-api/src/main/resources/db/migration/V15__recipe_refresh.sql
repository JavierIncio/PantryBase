-- Tracks when a THE_MEAL_DB recipe was last pulled from the provider, so stale
-- local copies can be re-fetched after the configured refresh-ttl. The default
-- stamps existing rows as fresh instead of force-refetching the whole catalog.
ALTER TABLE recipes ADD COLUMN refreshed_at timestamptz NOT NULL DEFAULT now();