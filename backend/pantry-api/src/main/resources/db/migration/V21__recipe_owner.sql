-- H8: user recipes remember their author, so only the author can publish them
ALTER TABLE recipes ADD COLUMN owner_id BIGINT REFERENCES users (id);
CREATE INDEX idx_recipes_owner_id ON recipes (owner_id);
