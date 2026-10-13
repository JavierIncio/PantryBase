-- AI usage preference (H7-a). Default true so existing users keep AI enabled.
ALTER TABLE user_preferences ADD COLUMN use_ai BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN user_preferences.use_ai IS 'Whether the user wants AI-powered recommendations; if false, AI features are hidden from UI';