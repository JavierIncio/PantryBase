-- Pantry inventory: what each user has, how much, and where.
--
-- The quantity is stored in a canonical unit on purpose. A pantry is read far more
-- often than it is written, and everything downstream (recipe coverage in H4,
-- stock deduction in H5) has to compare quantities against each other. Storing
-- "2 CUPS" would force every one of those comparisons to convert first, and the
-- conversion depends on a density that may not exist. Mass and volume both
-- resolve to GRAM; a count (three eggs) stays UNIT.
--
-- One row per (user, ingredient). Two bottles of milk in the fridge and in the
-- cupboard are one ingredient with one quantity; location and note describe it.
-- The alternative, a row per physical container, forces every read of the
-- inventory to aggregate first and makes "which row am I editing" ambiguous.
CREATE TABLE pantry_items
(
    id           BIGSERIAL     PRIMARY KEY,
    user_id      BIGINT        NOT NULL REFERENCES users (id),
    ingredient_id BIGINT       NOT NULL REFERENCES ingredients (id),
    quantity     DECIMAL(14, 6) NOT NULL CHECK (quantity > 0),
    unit_code    VARCHAR(16)   NOT NULL REFERENCES units (code),
    location     VARCHAR(100),
    note         VARCHAR(255),
    -- Optimistic locking. H5 deducts stock automatically, so two sessions (or a
    -- browser tab and a cooking session) can try to move the same quantity; the
    -- last write must not silently win.
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (user_id, ingredient_id)
);

-- The reads that matter are "this user's pantry" and "everything of this
-- ingredient for this user"; the unique constraint already serves the second as
-- its index, so the first is the only one worth adding.
CREATE INDEX idx_pantry_items_user_id ON pantry_items (user_id);

-- Ledger of quantity movements, created now and written from H3-2 onwards.
--
-- delta_quantity is signed: positive adds stock, negative consumes it, so the
-- sum of an item's entries is its current quantity and that identity can be
-- asserted in a test instead of taken on trust. H5 will add a second origin for
-- deductions made by a cooking session.
--
-- No unit_code here: a pantry item's unit is immutable (the ingredient decides it
-- and it is fixed at creation), so a column repeating it could only ever drift
-- away from pantry_items.unit_code without anything noticing. The cascade is
-- deliberate too: an entry with no item to belong to is not an audit record, it
-- is an orphan.
CREATE TABLE consumption_entries
(
    id             BIGSERIAL      PRIMARY KEY,
    pantry_item_id BIGINT         NOT NULL REFERENCES pantry_items (id) ON DELETE CASCADE,
    user_id        BIGINT         NOT NULL REFERENCES users (id),
    delta_quantity DECIMAL(14, 6) NOT NULL,
    origin         VARCHAR(30)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_consumption_entries_pantry_item_id
    ON consumption_entries (pantry_item_id, created_at);