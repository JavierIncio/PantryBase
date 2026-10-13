-- The per-user AI provider configuration (V18) was removed: PantryBase now targets
-- free, local models only (Ollama) and no longer stores user API keys. Flyway
-- migrations are immutable, so the table is dropped here instead of editing V18.
DROP TABLE IF EXISTS user_ai_config;
