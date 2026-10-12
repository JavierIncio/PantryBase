-- The UI theme chosen by the user (H6). Stored as the enum name; the API exposes
-- the lower-case id used as the frontend's data-theme attribute. Existing rows
-- (and the lazy default for new ones) fall back to BLUE.
ALTER TABLE user_preferences ADD COLUMN theme_id VARCHAR(16) NOT NULL DEFAULT 'BLUE';
