-- H8 Social: favorites, saved and publication
CREATE TABLE user_favorites (
  user_id BIGINT NOT NULL,
  recipe_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, recipe_id),
  CONSTRAINT fk_user_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_favorites_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_favorites_user ON user_favorites (user_id);

CREATE TABLE user_saved (
  user_id BIGINT NOT NULL,
  recipe_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, recipe_id),
  CONSTRAINT fk_user_saved_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_user_saved_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_saved_user ON user_saved (user_id);
