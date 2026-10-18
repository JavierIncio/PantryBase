-- H8 Social: user recipes can be published (NULL = private)
ALTER TABLE recipes ADD COLUMN published_at TIMESTAMP(3);
CREATE INDEX idx_recipes_published_at ON recipes (published_at);
